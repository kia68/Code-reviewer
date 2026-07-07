package de.ude.codereviewer.common.exception;

import java.time.Instant;

public record ErrorResponse(Instant timestamp, int status, String error, String message) {
}
