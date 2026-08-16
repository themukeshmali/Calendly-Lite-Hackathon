package com.hackathon.calendlylite.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Standard API response wrapper for all endpoints.
 * { "success": true, "message": "...", "data": {...}, "timestamp": "..." }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp = LocalDateTime.now();

    // Private constructor — use static factories
    private ApiResponse() {}

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.message = message;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.message = message;
        return r;
    }

    // Getters (Jackson needs these to serialize)
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
