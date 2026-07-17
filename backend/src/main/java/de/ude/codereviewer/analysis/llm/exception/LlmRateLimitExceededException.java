package de.ude.codereviewer.analysis.llm.exception;

/**
 * Spezialisierung von LlmApiException für den Fall, dass die
 * Anthropic-Rate-Limits (HTTP 429) auch nach mehreren Retry-Versuchen
 * weiterhin aktiv sind. Getrennt von LlmApiException, damit der
 * aufrufende Code (z.B. ReviewRunService) gezielt darauf reagieren
 * kann (z.B. ReviewRun auf Status "RATE_LIMITED" statt "FAILED" setzen).
 */
public class LlmRateLimitExceededException extends LlmApiException {

    public LlmRateLimitExceededException(String message, Throwable cause) {
        super(message, cause, 429, true);
    }
}