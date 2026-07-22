package de.ude.codereviewer.analysis.llm.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractToolInput_parsesFindingsArrayCorrectly() {
        String rawJson = """
            {
              "id": "msg_01",
              "type": "message",
              "role": "assistant",
              "content": [
                {
                  "type": "tool_use",
                  "id": "toolu_01",
                  "name": "submit_findings",
                  "input": {
                    "findings": [
                      {
                        "type": "CODE_SMELL",
                        "severity": "MEDIUM",
                        "lineStart": 12,
                        "lineEnd": 18,
                        "message": "Methode zu lang",
                        "suggestion": "In kleinere Methoden aufteilen",
                        "confidence": 0.85
                      }
                    ]
                  }
                }
              ],
              "stop_reason": "tool_use",
              "usage": { "input_tokens": 500, "output_tokens": 120 }
            }
            """;

        ClaudeResponse response = ClaudeResponse.parse(rawJson, objectMapper);

        List<FindingDraft> findings = response.extractToolInput("findings", FindingDraft.class);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).type()).isEqualTo("CODE_SMELL");
        assertThat(findings.get(0).confidence()).isEqualTo(0.85);
        assertThat(response.usage().total()).isEqualTo(620);
    }

    @Test
    void extractToolInput_throwsWhenToolUseBlockMissing() {
        String rawJsonWithoutToolUse = """
            {
              "id": "msg_02",
              "type": "message",
              "role": "assistant",
              "content": [
                { "type": "text", "text": "Ich kann das nicht analysieren." }
              ],
              "stop_reason": "end_turn",
              "usage": { "input_tokens": 100, "output_tokens": 20 }
            }
            """;

        ClaudeResponse response = ClaudeResponse.parse(rawJsonWithoutToolUse, objectMapper);

        assertThat(response.hasToolUse()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> response.extractToolInput("findings", FindingDraft.class)
        ).isInstanceOf(de.ude.codereviewer.analysis.llm.exception.LlmResponseParsingException.class);
    }
}