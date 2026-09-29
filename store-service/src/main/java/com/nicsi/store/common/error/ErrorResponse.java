package com.nicsi.store.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Standardized API error response format.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String code,
    String message,
    Map<String, Object> details,
    String correlationId,
    String path
) {
    public static ErrorResponse of(int status, String error, String code, String message,
                                    Map<String, Object> details, String correlationId, String path) {
        return new ErrorResponse(Instant.now(), status, error, code, message, details, correlationId, path);
    }
}
