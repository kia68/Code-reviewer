package de.ude.codereviewer.analysis.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Konfiguriert den HTTP-Client für die Kommunikation mit der
 * Anthropic Claude API. Setzt Base-URL sowie die von Anthropic
 * geforderten Standard-Header für jeden Request.
 */
@Configuration
@RequiredArgsConstructor
public class ClaudeHttpConfig {

    private final LlmProperties properties;

    @Bean
    public RestClient claudeRestClient() {
        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader("x-api-key", properties.apiKey())
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader("content-type", "application/json")
            .build();
    }
}