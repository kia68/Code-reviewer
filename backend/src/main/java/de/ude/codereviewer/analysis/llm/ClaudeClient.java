package de.ude.codereviewer.analysis.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ude.codereviewer.analysis.llm.dto.ClaudeRequest;
import de.ude.codereviewer.analysis.llm.dto.ClaudeResponse;
import de.ude.codereviewer.analysis.llm.dto.Message;
import de.ude.codereviewer.analysis.llm.exception.LlmApiException;
import de.ude.codereviewer.analysis.llm.exception.LlmRateLimitExceededException;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Kapselt die HTTP-Kommunikation mit der Anthropic Claude API.
 * Erzwingt strukturierte Antworten via Function-Calling (tool_choice)
 * und behandelt transiente Fehler (Rate-Limits, Server-Fehler) mit
 * automatischem Retry inklusive Backoff.
 */
import org.springframework.beans.factory.annotation.Qualifier;

@Component
@Slf4j
public class ClaudeClient {

    private static final String MESSAGES_ENDPOINT = "/v1/messages";

    private static final String DEFAULT_SYSTEM_PROMPT = """
        Du bist ein präziser, strukturierter Code-Review-Assistent.
        Antworte ausschließlich über das bereitgestellte Tool.
        Erfinde keine Informationen, die nicht aus dem Code ableitbar sind.
        """;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 1000L;

    private final RestClient restClient;
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    public ClaudeClient(@Qualifier("claudeRestClient") RestClient restClient,
                        LlmProperties properties,
                        ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Führt einen Claude-API-Call mit erzwungenem Tool-Use aus.
     * Nutzt den Standard-System-Prompt.
     *
     * @param userMessage der eigentliche Prompt-Text (Code + Anweisung)
     * @param toolSchema  das JSON-Schema des Tools, das Claude aufrufen MUSS
     * @param toolName    der Name des Tools (z.B. "submit_findings")
     * @return die geparste, typsichere Antwort
     */
    public ClaudeResponse callWithTool(String userMessage, JsonNode toolSchema, String toolName) {
        return callWithTool(DEFAULT_SYSTEM_PROMPT, userMessage, toolSchema, toolName);
    }

    /**
     * Wie {@link #callWithTool(String, JsonNode, String)}, erlaubt aber
     * einen phasenspezifischen System-Prompt (z.B. unterschiedliche
     * Rollenbeschreibung für Generate- vs. Reflect-Phase).
     */
    public ClaudeResponse callWithTool(String systemPrompt, String userMessage, JsonNode toolSchema, String toolName) {
        ClaudeRequest request = new ClaudeRequest(
            properties.model(),
            properties.maxTokensPerCall(),
            systemPrompt,
            List.of(new Message("user", userMessage)),
            List.of(toolSchema),
            new ClaudeRequest.ToolChoice("tool", toolName)
        );

        String rawResponse = executeWithRetry(request, toolName);
        ClaudeResponse response = ClaudeResponse.parse(rawResponse, objectMapper);

        if (!response.hasToolUse()) {
            log.warn("Claude hat trotz tool_choice keinen Tool-Call ausgeführt. " +
                    "tool={}, stop_reason={}", toolName, response.stopReason());
        }

        log.info("Claude-Call erfolgreich: tool={}, inputTokens={}, outputTokens={}",
                toolName, response.usage().inputTokens(), response.usage().outputTokens());

        return response;
    }

    /**
     * Führt den eigentlichen HTTP-Call aus und wiederholt ihn bei
     * transienten Fehlern (429, 5xx, Netzwerk-Timeouts) mit
     * exponentiellem Backoff. Bei nicht-transienten Fehlern (4xx außer 429)
     * wird sofort abgebrochen, da ein Retry hier keinen Sinn hat.
     */
    private String executeWithRetry(ClaudeRequest request, String toolName) {
        int attempt = 0;
        long backoffMillis = INITIAL_BACKOFF_MILLIS;

        while (true) {
            attempt++;
            try {
                return restClient.post()
                    .uri(MESSAGES_ENDPOINT)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    if (attempt > MAX_RETRIES) {
                        throw new LlmRateLimitExceededException(
                            "Rate-Limit der Claude API nach " + MAX_RETRIES + " Versuchen weiterhin aktiv", e);
                    }
                    long retryAfterMillis = extractRetryAfterMillis(e).orElse(backoffMillis);
                    log.warn("Claude API Rate-Limit (429) erreicht, tool={}, Versuch={}/{}, warte {}ms",
                            toolName, attempt, MAX_RETRIES, retryAfterMillis);
                    sleepUninterruptibly(retryAfterMillis);
                    backoffMillis *= 2;
                    continue;
                }
                // Andere 4xx-Fehler (400 Bad Request, 401 Unauthorized, 403 Forbidden, ...)
                // sind nicht transient -> sofort abbrechen, Retry würde nichts ändern.
                throw new LlmApiException(
                    "Claude API meldete Client-Fehler %d für tool=%s: %s"
                        .formatted(e.getStatusCode().value(), toolName, e.getResponseBodyAsString()),
                    e, e.getStatusCode().value(), false);

            } catch (HttpServerErrorException e) {
                if (attempt > MAX_RETRIES) {
                    throw new LlmApiException(
                        "Claude API Server-Fehler %d nach %d Versuchen für tool=%s"
                            .formatted(e.getStatusCode().value(), MAX_RETRIES, toolName),
                        e, e.getStatusCode().value(), true);
                }
                log.warn("Claude API Server-Fehler ({}), tool={}, Versuch={}/{}, warte {}ms",
                        e.getStatusCode().value(), toolName, attempt, MAX_RETRIES, backoffMillis);
                sleepUninterruptibly(backoffMillis);
                backoffMillis *= 2;

            } catch (ResourceAccessException e) {
                // Netzwerk-Timeout oder Verbindungsfehler
                if (attempt > MAX_RETRIES) {
                    throw new LlmApiException(
                        "Netzwerkfehler bei Claude-API-Call nach %d Versuchen für tool=%s: %s"
                            .formatted(MAX_RETRIES, toolName, e.getMessage()),
                        e, null, true);
                }
                log.warn("Netzwerkfehler bei Claude-API-Call, tool={}, Versuch={}/{}, warte {}ms: {}",
                        toolName, attempt, MAX_RETRIES, backoffMillis, e.getMessage());
                sleepUninterruptibly(backoffMillis);
                backoffMillis *= 2;
            }
        }
    }

    /**
     * Liest den "Retry-After"-Header aus der 429-Antwort, falls Anthropic
     * ihn mitschickt. Fällt auf den internen Backoff-Wert zurück, falls
     * der Header fehlt oder nicht parsbar ist.
     */
    private java.util.Optional<Long> extractRetryAfterMillis(HttpClientErrorException e) {
        String retryAfterHeader = e.getResponseHeaders() != null
            ? e.getResponseHeaders().getFirst("Retry-After")
            : null;

        if (retryAfterHeader == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Long.parseLong(retryAfterHeader) * 1000L);
        } catch (NumberFormatException ex) {
            return java.util.Optional.empty();
        }
    }

    private void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmApiException("Retry-Wartezeit wurde unterbrochen", e, null, false);
        }
    }

    /**
     * Hilfsmethode, um den HTTP-Statuscode unabhängig vom konkreten
     * Exception-Typ auszulesen (aktuell ungenutzt, aber praktisch
     * für zukünftige Erweiterungen wie z.B. Metriken/Monitoring).
     */
    private int statusCodeOf(HttpStatusCode statusCode) {
        return statusCode.value();
    }
}