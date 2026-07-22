package de.ude.codereviewer.analysis.llm.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ude.codereviewer.analysis.llm.exception.LlmResponseParsingException;
import java.util.Optional;

/**
 * Toleranter Wrapper um eine OpenAI-kompatible /chat/completions-Antwort.
 * Navigiert per JsonNode statt strikter Records, weil vLLM-Backends (GWDG)
 * kleine Feld-Abweichungen liefern können. Bietet zwei Extraktionswege:
 * strukturiert über tool_calls oder als Fallback über den Text-Content.
 */
public final class OpenAiChatResponse {

    private final JsonNode root;

    private OpenAiChatResponse(JsonNode root) {
        this.root = root;
    }

    public static OpenAiChatResponse parse(String rawJson, ObjectMapper objectMapper) {
        try {
            return new OpenAiChatResponse(objectMapper.readTree(rawJson));
        } catch (JsonProcessingException e) {
            throw new LlmResponseParsingException(
                "Konnte OpenAI-Antwort nicht parsen: " + e.getMessage(), e);
        }
    }

    private JsonNode firstMessage() {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).path("message");
    }

    /**
     * Die {@code arguments} des ersten tool_calls als roher JSON-String,
     * falls das Modell den Function-Call genutzt hat.
     */
    public Optional<String> firstToolCallArguments() {
        JsonNode message = firstMessage();
        if (message == null) {
            return Optional.empty();
        }
        JsonNode toolCalls = message.path("tool_calls");
        if (!toolCalls.isArray() || toolCalls.isEmpty()) {
            return Optional.empty();
        }
        JsonNode args = toolCalls.get(0).path("function").path("arguments");
        return args.isMissingNode() || args.isNull() ? Optional.empty() : Optional.of(args.asText());
    }

    /** Freitext-Content der Antwort (Fallback, wenn kein tool_call kam). */
    public String content() {
        JsonNode message = firstMessage();
        if (message == null) {
            return "";
        }
        JsonNode content = message.path("content");
        return content.isTextual() ? content.asText() : "";
    }

    public String finishReason() {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "unknown";
        }
        return choices.get(0).path("finish_reason").asText("unknown");
    }

    /** Gesamt-Token-Verbrauch (prompt + completion) für das Budget-Tracking. */
    public int totalTokens() {
        JsonNode usage = root.path("usage");
        if (usage.has("total_tokens")) {
            return usage.path("total_tokens").asInt(0);
        }
        return usage.path("prompt_tokens").asInt(0) + usage.path("completion_tokens").asInt(0);
    }
}
