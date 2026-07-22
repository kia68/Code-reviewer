package de.ude.codereviewer.analysis.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.ude.codereviewer.analysis.llm.dto.FindingDraft;
import de.ude.codereviewer.analysis.llm.dto.Message;
import de.ude.codereviewer.analysis.llm.dto.OpenAiChatRequest;
import de.ude.codereviewer.analysis.llm.dto.OpenAiChatResponse;
import de.ude.codereviewer.analysis.llm.exception.LlmApiException;
import de.ude.codereviewer.analysis.llm.exception.LlmRateLimitExceededException;
import de.ude.codereviewer.analysis.llm.ratelimit.TokenBudgetGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * HTTP-Kommunikation mit einem OpenAI-kompatiblen /chat/completions-Endpoint
 * (GWDG SAIA / Chat AI). Erzwingt strukturierte Findings über Function-Calling,
 * fällt aber auf das Parsen des Text-Contents zurück, weil offene Modelle
 * (Llama/Qwen/Codestral) über vLLM nicht immer zuverlässig tool_calls liefern.
 */
import org.springframework.beans.factory.annotation.Qualifier;

@Component
@Slf4j
public class OpenAiClient {

    private static final String CHAT_ENDPOINT = "/chat/completions";
    private static final String TOOL_NAME = "submit_findings";
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 1000L;

    private static final String SYSTEM_PROMPT = """
        Du bist ein präziser, strukturierter Code-Review-Assistent.
        Antworte ausschließlich über das Tool 'submit_findings'.
        Falls das Tool nicht verfügbar ist, antworte mit einem einzelnen
        JSON-Objekt der Form {"findings": [...]} und keinem weiteren Text.
        Erfinde keine Informationen, die nicht aus dem Code ableitbar sind.
        """;

    private final RestClient restClient;
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final ClaudeToolSchemas toolSchemas;
    private final TokenBudgetGuard budgetGuard;

    public OpenAiClient(
            @Qualifier("openAiRestClient") RestClient restClient,
            LlmProperties properties,
            ObjectMapper objectMapper,
            ClaudeToolSchemas toolSchemas,
            TokenBudgetGuard budgetGuard) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.toolSchemas = toolSchemas;
        this.budgetGuard = budgetGuard;
    }

    /**
     * Fordert Findings für den übergebenen Prompt an. Liefert eine leere
     * Liste, wenn das Modell keine verwertbare Struktur zurückgibt.
     */
    public List<FindingDraft> requestFindings(String userMessage) {
        OpenAiChatRequest request = new OpenAiChatRequest(
            properties.model(),
            List.of(new Message("system", SYSTEM_PROMPT), new Message("user", userMessage)),
            properties.maxTokensPerCall(),
            0.0,
            List.of(buildFindingsTool()),
            buildForcedToolChoice()
        );

        String rawResponse = executeWithRetry(request);
        OpenAiChatResponse response = OpenAiChatResponse.parse(rawResponse, objectMapper);
        budgetGuard.recordActualUsage(response.totalTokens());

        List<FindingDraft> findings = extractFindings(response);
        log.info("OpenAI-Call: model={}, finish={}, tokens={}, findings={}",
            properties.model(), response.finishReason(), response.totalTokens(), findings.size());
        return findings;
    }

    /** Baut das OpenAI-Function-Objekt aus dem geteilten submit_findings-Schema. */
    private ObjectNode buildFindingsTool() {
        JsonNode schema = toolSchemas.submitFindings();
        ObjectNode function = objectMapper.createObjectNode();
        function.put("name", schema.path("name").asText(TOOL_NAME));
        function.put("description", schema.path("description").asText(""));
        function.set("parameters", schema.path("input_schema"));

        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "function");
        tool.set("function", function);
        return tool;
    }

    private ObjectNode buildForcedToolChoice() {
        ObjectNode fn = objectMapper.createObjectNode();
        fn.put("name", TOOL_NAME);
        ObjectNode choice = objectMapper.createObjectNode();
        choice.put("type", "function");
        choice.set("function", fn);
        return choice;
    }

    private List<FindingDraft> extractFindings(OpenAiChatResponse response) {
        Optional<String> toolArgs = response.firstToolCallArguments();
        if (toolArgs.isPresent()) {
            return parseFindingsJson(toolArgs.get());
        }
        // Fallback: some open models ignore tool_choice and answer in plain text.
        String content = response.content();
        if (!content.isBlank()) {
            log.warn("OpenAI-Modell lieferte keinen tool_call, versuche Content-Fallback (finish={})",
                response.finishReason());
            return parseFindingsJson(content);
        }
        log.warn("OpenAI-Antwort enthielt weder tool_calls noch Content — keine Findings");
        return List.of();
    }

    /**
     * Extrahiert die findings-Liste aus einem JSON-String. Toleriert Markdown-
     * Fences und umgebenden Text, indem vom ersten '{' bis zum letzten '}'
     * geschnitten wird.
     */
    private List<FindingDraft> parseFindingsJson(String raw) {
        try {
            String json = isolateJsonObject(raw);
            JsonNode root = objectMapper.readTree(json);
            JsonNode findingsNode = root.isArray() ? root : root.path("findings");
            if (!findingsNode.isArray()) {
                return List.of();
            }
            List<FindingDraft> result = new ArrayList<>();
            for (JsonNode item : findingsNode) {
                result.add(objectMapper.treeToValue(item, FindingDraft.class));
            }
            return result;
        } catch (Exception e) {
            log.warn("Konnte OpenAI-Findings nicht parsen: {}", e.getMessage());
            return List.of();
        }
    }

    private String isolateJsonObject(String raw) {
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        // Might be a bare array.
        int arrStart = trimmed.indexOf('[');
        int arrEnd = trimmed.lastIndexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) {
            return trimmed.substring(arrStart, arrEnd + 1);
        }
        return trimmed;
    }

    private String executeWithRetry(OpenAiChatRequest request) {
        int attempt = 0;
        long backoffMillis = INITIAL_BACKOFF_MILLIS;

        while (true) {
            attempt++;
            try {
                return restClient.post()
                    .uri(CHAT_ENDPOINT)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    if (attempt > MAX_RETRIES) {
                        throw new LlmRateLimitExceededException(
                            "Rate-Limit der OpenAI-API nach " + MAX_RETRIES + " Versuchen weiterhin aktiv", e);
                    }
                    log.warn("OpenAI Rate-Limit (429), Versuch={}/{}, warte {}ms", attempt, MAX_RETRIES, backoffMillis);
                    sleep(backoffMillis);
                    backoffMillis *= 2;
                    continue;
                }
                throw new LlmApiException(
                    "OpenAI-API Client-Fehler %d: %s".formatted(e.getStatusCode().value(), e.getResponseBodyAsString()),
                    e, e.getStatusCode().value(), false);

            } catch (HttpServerErrorException e) {
                if (attempt > MAX_RETRIES) {
                    throw new LlmApiException(
                        "OpenAI-API Server-Fehler %d nach %d Versuchen".formatted(e.getStatusCode().value(), MAX_RETRIES),
                        e, e.getStatusCode().value(), true);
                }
                log.warn("OpenAI Server-Fehler ({}), Versuch={}/{}, warte {}ms",
                    e.getStatusCode().value(), attempt, MAX_RETRIES, backoffMillis);
                sleep(backoffMillis);
                backoffMillis *= 2;

            } catch (ResourceAccessException e) {
                if (attempt > MAX_RETRIES) {
                    throw new LlmApiException(
                        "Netzwerkfehler bei OpenAI-Call nach %d Versuchen: %s".formatted(MAX_RETRIES, e.getMessage()),
                        e, null, true);
                }
                log.warn("Netzwerkfehler bei OpenAI-Call, Versuch={}/{}, warte {}ms: {}",
                    attempt, MAX_RETRIES, backoffMillis, e.getMessage());
                sleep(backoffMillis);
                backoffMillis *= 2;
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmApiException("Retry-Wartezeit unterbrochen", e, null, false);
        }
    }
}
