package de.ude.codereviewer.analysis.llm.exception;

/**
 * Wird geworfen, wenn eine Claude-API-Antwort nicht dem erwarteten
 * Format entspricht (z.B. fehlender tool_use-Block, ungültiges JSON).
 */
public class LlmResponseParsingException extends RuntimeException {

    public LlmResponseParsingException(String message) {
        super(message);
    }

    public LlmResponseParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}