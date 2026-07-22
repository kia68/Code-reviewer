package de.ude.codereviewer.analysis.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Lädt die statischen Tool-Schemas (JSON) für Function-Calling
 * mit der Claude API beim Anwendungsstart und stellt sie als
 * JsonNode-Objekte für den ClaudeClient bereit.
 */
@Component
public class ClaudeToolSchemas {

    private final JsonNode submitFindingsSchema;
    private final JsonNode submitReflectionSchema;

    public ClaudeToolSchemas(ObjectMapper objectMapper) throws IOException {
        this.submitFindingsSchema = loadSchema(objectMapper, "llm-schemas/submit-findings-schema.json");
        this.submitReflectionSchema = loadSchema(objectMapper, "llm-schemas/submit-reflection-schema.json");
    }

    /**
     * Tool-Schema für die Generate- und Refine-Phase.
     * Erzwingt eine strukturierte Liste von Findings.
     */
    public JsonNode submitFindings() {
        return submitFindingsSchema;
    }

    /**
     * Tool-Schema für die Reflect-Phase.
     * Erzwingt eine kritische Bewertung der ursprünglichen Findings.
     */
    public JsonNode submitReflection() {
        return submitReflectionSchema;
    }

    private JsonNode loadSchema(ObjectMapper objectMapper, String classpathLocation) throws IOException {
        try (var inputStream = new ClassPathResource(classpathLocation).getInputStream()) {
            return objectMapper.readTree(inputStream);
        }
    }
}