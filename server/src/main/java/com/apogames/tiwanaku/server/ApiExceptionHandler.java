package com.apogames.tiwanaku.server;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(org.springframework.web.bind.MethodArgumentNotValidException exception,
                                                   HttpServletRequest request) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining("; "));
        LOGGER.warn("Invalid request method={} path={} fields={}", request.getMethod(), request.getRequestURI(), detail);
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.BAD_REQUEST, detail));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> invalidJson(HttpServletRequest request) {
        LOGGER.warn("Invalid JSON request method={} path={}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Invalid JSON request: check required fields, types and unsupported properties."));
    }

    @ExceptionHandler({org.springframework.dao.DataAccessException.class, java.io.IOException.class, java.io.UncheckedIOException.class})
    public ResponseEntity<ProblemDetail> storage(Exception exception, HttpServletRequest request) {
        LOGGER.error("Local storage operation failed method={} path={}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(503).body(ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Local storage failed. No successful save is acknowledged. Check free disk space/permissions and preserve the data directory; reconnect to reconcile any uncertain action."));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handle(ApiException exception, HttpServletRequest request) {
        LOGGER.warn("API rejected method={} path={} status={} reason={}",
                request.getMethod(), request.getRequestURI(), exception.getStatusCode().value(),
                exception.getReason());
        return ResponseEntity.status(exception.getStatusCode())
                .body(ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason()));
    }
}
