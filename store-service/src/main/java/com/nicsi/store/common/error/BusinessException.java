package com.nicsi.store.common.error;

import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.HashMap;

/**
 * Base exception class for all business rules violations.
 */
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;
    private final Map<String, Object> details;

    public BusinessException(String message, HttpStatus httpStatus) {
        this("BUSINESS_ERROR", message, httpStatus);
    }

    public BusinessException(String message) {
        this("BUSINESS_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = new HashMap<>();
    }

    public BusinessException(String errorCode, String message, HttpStatus httpStatus, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = details != null ? details : new HashMap<>();
    }
    
    public BusinessException addDetail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getHttpStatus() { return httpStatus; }
    public Map<String, Object> getDetails() { return details; }
}
