package de.ude.codereviewer.analysis.llm.exception;

/**
 * Wird geworfen, wenn ein Claude-API-Call endgültig fehlschlägt
 * (nach Ausschöpfen aller Retry-Versuche, oder bei nicht-transienten
 * Fehlern wie 401/403/400).
 */
public class LlmApiException extends RuntimeException {

    private final Integer httpStatusCode;
    private final boolean retryable;

    public LlmApiException(String message, Throwable cause, Integer httpStatusCode, boolean retryable) {
        super(message, cause);
        this.httpStatusCode = httpStatusCode;
        this.retryable = retryable;
    }

    public LlmApiException(String message, Throwable cause) {
        this(message, cause, null, false);
    }

    /**
     * HTTP-Statuscode der fehlgeschlagenen Anfrage, falls bekannt.
     * Kann null sein bei reinen Netzwerkfehlern (kein HTTP-Response erhalten).
     */
    public Integer httpStatusCode() {
        return httpStatusCode;
    }

    /**
     * Zeigt an, ob der Fehler grundsätzlich transient war
     * (z.B. Server-Fehler, Timeout) und ein erneuter Versuch
     * zu einem späteren Zeitpunkt sinnvoll sein könnte.
     */
    public boolean isRetryable() {
        return retryable;
    }
}