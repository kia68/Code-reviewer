package de.ude.codereviewer.analysis.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class OpenAiHTTPConfig {

    private final LlmProperties properties;

    @Bean(name = "openAiRestClient")
    public RestClient openAiRestClient() {
        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader("Authorization", "Bearer " + properties.apiKey())
            .defaultHeader("content-type", "application/json")
            .build();
    }
}
