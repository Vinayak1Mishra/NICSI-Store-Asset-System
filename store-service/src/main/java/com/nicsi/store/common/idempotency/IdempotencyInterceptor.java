package com.nicsi.store.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENCY_KEY_ATTRIBUTE = "idempotencyKey";
    
    private final ObjectMapper objectMapper;

    public IdempotencyInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        
        if (!handlerMethod.hasMethodAnnotation(IdempotentPost.class)) {
            return true;
        }
        
        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        
        if (key == null || key.isBlank()) {
            writeError(response, request, "Idempotency-Key header is required for this endpoint");
            return false;
        }
        
        key = key.trim();
        if (key.length() < 8 || key.length() > 150) {
            writeError(response, request, "Idempotency-Key must be between 8 and 150 characters");
            return false;
        }
        
        request.setAttribute(IDEMPOTENCY_KEY_ATTRIBUTE, key);
        return true;
    }
    
    private void writeError(HttpServletResponse response, HttpServletRequest request, String message) throws Exception {
        response.setStatus(400);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = ErrorResponse.of(
            400, "Bad Request", "IDEMPOTENCY_KEY_INVALID", message,
            null, MDC.get("correlationId"), request.getRequestURI()
        );
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
