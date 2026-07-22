package de.ude.codereviewer.analysis.llm.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiChatResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractsToolCallArgumentsWhenModelUsesFunctionCalling() {
        String raw = """
            {
              "choices": [{
                "message": {
                  "role": "assistant",
                  "content": null,
                  "tool_calls": [{
                    "id": "call_1",
                    "type": "function",
                    "function": {
                      "name": "submit_findings",
                      "arguments": "{\\"findings\\":[{\\"type\\":\\"LONG_METHOD\\"}]}"
                    }
                  }]
                },
                "finish_reason": "tool_calls"
              }],
              "usage": {"prompt_tokens": 100, "completion_tokens": 50, "total_tokens": 150}
            }
            """;

        OpenAiChatResponse response = OpenAiChatResponse.parse(raw, mapper);

        assertThat(response.firstToolCallArguments()).isPresent();
        assertThat(response.firstToolCallArguments().get()).contains("LONG_METHOD");
        assertThat(response.finishReason()).isEqualTo("tool_calls");
        assertThat(response.totalTokens()).isEqualTo(150);
    }

    @Test
    void fallsBackToContentWhenNoToolCall() {
        String raw = """
            {
              "choices": [{
                "message": {"role": "assistant", "content": "{\\"findings\\":[]}"},
                "finish_reason": "stop"
              }],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5}
            }
            """;

        OpenAiChatResponse response = OpenAiChatResponse.parse(raw, mapper);

        assertThat(response.firstToolCallArguments()).isEmpty();
        assertThat(response.content()).contains("findings");
    }

    @Test
    void totalTokensFallsBackToPromptPlusCompletionWhenTotalMissing() {
        String raw = """
            {
              "choices": [{"message": {"content": "hi"}, "finish_reason": "stop"}],
              "usage": {"prompt_tokens": 30, "completion_tokens": 12}
            }
            """;

        OpenAiChatResponse response = OpenAiChatResponse.parse(raw, mapper);

        assertThat(response.totalTokens()).isEqualTo(42);
    }

    @Test
    void handlesEmptyChoicesGracefully() {
        String raw = """
            {"choices": [], "usage": {"total_tokens": 0}}
            """;

        OpenAiChatResponse response = OpenAiChatResponse.parse(raw, mapper);

        assertThat(response.firstToolCallArguments()).isEmpty();
        assertThat(response.content()).isEmpty();
        assertThat(response.finishReason()).isEqualTo("unknown");
    }
}
