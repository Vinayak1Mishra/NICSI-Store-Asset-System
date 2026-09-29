package com.nicsi.store.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler providing structured error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        return createErrorResponse(
            ex.getHttpStatus(),
            ex.getErrorCode(),
            ex.getMessage(),
            ex.getDetails(),
            request.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Validation error: {}", details);
        return createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Input validation failed",
            details,
            request.getRequestURI()
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> 
            details.put(violation.getPropertyPath().toString(), violation.getMessage()));
        log.warn("Constraint violation: {}", details);
        return createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "CONSTRAINT_VIOLATION",
            "Constraint validation failed",
            details,
            request.getRequestURI()
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic locking failure: {}", ex.getMessage());
        return createErrorResponse(
            HttpStatus.CONFLICT,
            "CONCURRENT_MODIFICATION",
            "The resource was modified by another transaction",
            null,
            request.getRequestURI()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return createErrorResponse(
            HttpStatus.FORBIDDEN,
            "ACCESS_DENIED",
            "Access is denied",
            null,
            request.getRequestURI()
        );
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return createErrorResponse(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            "The requested resource was not found",
            null,
            request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: ", ex);
        return createErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred",
            null,
            request.getRequestURI()
        );
    }

    private ResponseEntity<ErrorResponse> createErrorResponse(
            HttpStatus status, String code, String message, Map<String, Object> details, String path) {
        
        String correlationId = MDC.get("correlationId");
        
        ErrorResponse response = ErrorResponse.of(
            status.value(),
            status.getReasonPhrase(),
            code,
            message,
            details,
            correlationId,
            path
        );
        
        return new ResponseEntity<>(response, status);
    }
}
