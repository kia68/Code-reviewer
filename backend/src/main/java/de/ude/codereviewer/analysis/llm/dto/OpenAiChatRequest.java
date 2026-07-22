package de.ude.codereviewer.analysis.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request-Body für den OpenAI-kompatiblen /chat/completions-Endpoint
 * (z.B. GWDG SAIA / Chat AI). Anders als die Anthropic-Variante:
 * System-Prompt ist eine Message mit role="system", und Tools folgen
 * dem OpenAI-Function-Format ({type:"function", function:{...}}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatRequest(
    String model,
    List<Message> messages,
    @JsonProperty("max_tokens") int maxTokens,
    Double temperature,
    List<Object> tools,
    @JsonProperty("tool_choice") Object toolChoice
) {}
