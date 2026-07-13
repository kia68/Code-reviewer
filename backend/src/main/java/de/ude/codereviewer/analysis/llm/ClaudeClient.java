package de.ude.codereviewer.analysis.llm;

@Component
@RequiredArgsConstructor
public class ClaudeClient {

    private final RestClient restClient;
    private final LlmProperties properties;

    public ClaudeResponse callWithTool(String userMessage, JsonNode toolSchema, String toolName) {
        
    }
}