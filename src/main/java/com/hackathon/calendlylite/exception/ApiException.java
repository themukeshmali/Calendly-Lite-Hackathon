package com.hackathon.calendlylite.exception;

import org.springframework.http.HttpStatus;

/**
 * Application-level exception carrying an HTTP status code.
 * Thrown anywhere in the service/controller layer to signal a known error condition.
 * {@link GlobalExceptionHandler} catches this and returns a structured {@code ApiResponse}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
