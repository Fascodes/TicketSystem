package dev.fascodes.ticketSystem.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.time.Instant;

public record ApiError(int status, String error, String message, String path, Instant timestamp) {
    public static ApiError of(HttpStatus status, String message, HttpServletRequest req) {
        return new ApiError(status.value(), status.getReasonPhrase(), message,
                req.getRequestURI(), Instant.now());
    }
}
