package de.ude.codereviewer.analysis.llm.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Ein einzelner Content-Block aus der Anthropic-Response.
 * Je nach "type" sind unterschiedliche Felder gesetzt:
 *  - type="text"      -> nur 'text' ist gesetzt
 *  - type="tool_use"  -> 'name' und 'input' sind gesetzt
 */
public record ContentBlock(
    String type,
    String id,
    String name,
    JsonNode input,
    String text
) {
    public boolean isToolUse() {
        return "tool_use".equals(type);
    }

    public boolean isText() {
        return "text".equals(type);
    }
}