package de.ude.codereviewer.analysis.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClaudeRequest(
    String model,
    @JsonProperty("max_tokens") int maxTokens,
    String system,
    List<Message> messages,
    List<Object> tools,
    @JsonProperty("tool_choice") ToolChoice toolChoice

) {
    public record ToolChoice(
        String type,
        String name
    ) {}
}