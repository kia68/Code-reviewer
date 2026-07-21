package de.ude.codereviewer.analysis.llm.exception;

/**
 * Wird geworfen, wenn ein geplanter Claude-API-Call das konfigurierte
 * tägliche Token-Budget überschreiten würde. Verhindert unkontrollierte
 * Kosten durch exzessive LLM-Nutzung.
 */
public class LlmBudgetExceededException extends RuntimeException {

    public LlmBudgetExceededException(String message) {
        super(message);
    }
}