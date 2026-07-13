package de.ude.codereviewer.analysis.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ude.codereviewer.analysis.llm.exception.LlmResponseParsingException;

import java.util.List;
import java.util.Optional;

/**
 * Typsicherer Wrapper um die rohe Anthropic-/v1/messages-Antwort.
 * Kapselt das Parsen des JSON sowie die Extraktion des tool_use-Blocks,
 * über den wir strukturierte Daten von Claude erhalten.
 */
public final class ClaudeResponse {

    private final RawApiResponse raw;
    private final ObjectMapper objectMapper;

    private ClaudeResponse(RawApiResponse raw, ObjectMapper objectMapper) {
        this.raw = raw;
        this.objectMapper = objectMapper;
    }

    public static ClaudeResponse parse(String rawJson, ObjectMapper objectMapper) {
        try {
            RawApiResponse parsed = objectMapper.readValue(rawJson, RawApiResponse.class);
            return new ClaudeResponse(parsed, objectMapper);
        } catch (JsonProcessingException e) {
            throw new LlmResponseParsingException(
                "Konnte Claude-API-Antwort nicht parsen: " + e.getMessage(), e);
        }
    }

    public <T> List<T> extractToolInput(String arrayFieldName, Class<T> itemType) {
        JsonNode toolInput = getToolUseInputOrThrow();
        JsonNode arrayNode = toolInput.get(arrayFieldName);

        if (arrayNode == null || !arrayNode.isArray()) {
            throw new LlmResponseParsingException(
                "Erwartetes Array-Feld '%s' fehlt oder ist kein Array im tool_use-Input: %s"
                    .formatted(arrayFieldName, toolInput));
        }

        try {
            List<T> result = new java.util.ArrayList<>();
            for (JsonNode item : arrayNode) {
                result.add(objectMapper.treeToValue(item, itemType));
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new LlmResponseParsingException(
                "Konnte Array-Element in Feld '%s' nicht auf %s mappen: %s"
                    .formatted(arrayFieldName, itemType.getSimpleName(), e.getMessage()), e);
        }
    }

    public <T> T extractToolInputAs(Class<T> targetType) {
        JsonNode toolInput = getToolUseInputOrThrow();
        try {
            return objectMapper.treeToValue(toolInput, targetType);
        } catch (JsonProcessingException e) {
            throw new LlmResponseParsingException(
                "Konnte tool_use-Input nicht auf %s mappen: %s"
                    .formatted(targetType.getSimpleName(), e.getMessage()), e);
        }
    }

    public String extractSummaryText() {
        return raw.content().stream()
            .filter(ContentBlock::isText)
            .map(ContentBlock::text)
            .findFirst()
            .orElse("");
    }

    public String stopReason() {
        return raw.stopReason();
    }

    public Usage usage() {
        return raw.usage();
    }

    public boolean hasToolUse() {
        return findToolUseBlock().isPresent();
    }

    // --- private Hilfsmethoden ---

    private Optional<ContentBlock> findToolUseBlock() {
        return raw.content().stream()
            .filter(ContentBlock::isToolUse)
            .findFirst();
    }

    private JsonNode getToolUseInputOrThrow() {
        return findToolUseBlock()
            .map(ContentBlock::input)
            .orElseThrow(() -> new LlmResponseParsingException(
                "Erwarteter tool_use-Block fehlt in Claude-Antwort. stop_reason=" + raw.stopReason()));
    }

    private record RawApiResponse(
        String id,
        String type,
        String role,
        List<ContentBlock> content,

        @JsonProperty("stop_reason")
        String stopReason,

        Usage usage
    ) {}
}