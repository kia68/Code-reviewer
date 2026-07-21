package de.ude.codereviewer.analysis.llm.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesToExpectedAnthropicJsonFormat() throws Exception {
        ClaudeRequest request = new ClaudeRequest(
            "claude-sonnet-4-5",
            2048,
            "Du bist ein Reviewer.",
            List.of(new Message("user", "Analysiere: class Foo {}")),
            List.of(objectMapper.createObjectNode().put("name", "submit_findings")),
            new ClaudeRequest.ToolChoice("tool","submit_findings")
        );

        String json = objectMapper.writeValueAsString(request);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("max_tokens").asInt()).isEqualTo(2048);
        assertThat(node.get("tool_choice").get("type").asText()).isEqualTo("tool");
        assertThat(node.get("tool_choice").get("name").asText()).isEqualTo("submit_findings");
        assertThat(node.has("system")).isTrue();
    }

    @Test
    void omitsNullFieldsFromJson() throws Exception {
        ClaudeRequest request = new ClaudeRequest(
            "claude-sonnet-4-5", 2048, null, List.of(), List.of(), null
        );

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).doesNotContain("\"system\":null");
        assertThat(json).doesNotContain("\"tool_choice\":null");
    }
}