package com.hackathon.calendlylite.exception;

import com.hackathon.calendlylite.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralised exception handler — ensures every error response follows the
 * standard {@code ApiResponse} envelope regardless of where the exception is thrown.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Known API errors (slot not found, conflict, etc.) */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        log.warn("API error [{}]: {}", ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(ex.getMessage()));
    }

    /** Bean Validation failures (@NotBlank, @Email, @Future, etc.) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .reduce((a, b) -> a + ", " + b)
                .orElse("Validation failed");
        log.warn("Validation error: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Validation failed: " + message));
    }

    /** Missing required HTTP headers (e.g. Idempotency-Key not provided) */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        String message = "Required header '" + ex.getHeaderName() + "' is missing";
        log.warn("Missing header: {}", ex.getHeaderName());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message));
    }

    /** Catch-all for any unexpected exceptions */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("An unexpected error occurred. Please try again."));
    }
}
