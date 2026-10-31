package dev.fascodes.ticketSystem.common;

import dev.fascodes.ticketSystem.auth.exception.EmailAlreadyUsedException;
import dev.fascodes.ticketSystem.auth.exception.InvalidRefreshTokenException;
import dev.fascodes.ticketSystem.auth.exception.TokenReuseException;
import dev.fascodes.ticketSystem.auth.refresh.RefreshCookieFactory;
import dev.fascodes.ticketSystem.event.exception.EventNotFoundException;
import dev.fascodes.ticketSystem.venue.exception.VenueNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final RefreshCookieFactory cookieFactory;

    public GlobalExceptionHandler(RefreshCookieFactory cookieFactory) {
        this.cookieFactory = cookieFactory;
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> invalidRefresh(InvalidRefreshTokenException ex, HttpServletRequest req) {
        if (ex instanceof TokenReuseException) {
            log.warn("Refresh token reuse detected");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .body(ApiError.of(HttpStatus.UNAUTHORIZED, "Invalid or expired session", req));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> authentication(HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED,
                        "Invalid credentials or account temporarily unavailable", req));
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ApiError> emailTaken(HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, "Email already in use", req));
    }

    @ExceptionHandler({EventNotFoundException.class, VenueNotFoundException.class})
    public ResponseEntity<ApiError> notFound(HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND, "Resource not found", req));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, message, req));
    }

    @ExceptionHandler(InvalidSortException.class)
    public ResponseEntity<ApiError> invalidSort(InvalidSortException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, ex.getMessage(), req));
    }

    // Malformed path/query param (e.g. non-UUID id, published=abc) - client error, not 500
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, ex.getName() + ": invalid value", req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> fallback(Exception ex, HttpServletRequest req) throws Exception {
        if (ex instanceof AccessDeniedException) {
            throw ex;
        }
        log.error("Unhandled exception on {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req));
    }
}
