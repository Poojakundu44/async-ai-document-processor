package com.asyncai.docprocessor.common.exception;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WHY a global exception handler?
 *
 * Without this, Spring returns its default error format which:
 * - Leaks stack traces and implementation details to clients
 * - Is inconsistent (different format for different errors)
 * - Is hard to parse programmatically
 *
 * @RestControllerAdvice intercepts exceptions thrown from any @RestController
 * and lets you return a consistent, clean error response.
 *
 * CRITICAL RULE: Never return stack traces in production responses.
 * Log them internally (with a correlation ID), but send only a
 * human-readable message and error code to the client.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ============================================================
    // Standard error response structure
    // WHY a consistent structure? API clients write code against
    // your error format. If it changes, their error handling breaks.
    // ============================================================
    @Value
    @Builder
    public static class ErrorResponse {
        String errorCode;
        String message;
        Instant timestamp;
        Map<String, String> fieldErrors;  // populated for validation failures
    }

    /**
     * 404 — Resource not found
     */
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(DocumentNotFoundException ex) {
        log.warn("Document not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.builder()
                        .errorCode("DOCUMENT_NOT_FOUND")
                        .message(ex.getMessage())
                        .timestamp(Instant.now())
                        .build()
        );
    }

    /**
     * 400 — Validation failures from @Valid
     * Spring throws this when @RequestBody or @RequestPart validation fails.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        // In case of duplicate field names, keep the first message
                        (first, second) -> first
                ));

        log.warn("Validation failed: {}", fieldErrors);

        return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                        .errorCode("VALIDATION_ERROR")
                        .message("Request validation failed")
                        .timestamp(Instant.now())
                        .fieldErrors(fieldErrors)
                        .build()
        );
    }

    /**
     * 413 — File too large
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                ErrorResponse.builder()
                        .errorCode("FILE_TOO_LARGE")
                        .message("File size exceeds the maximum allowed limit of 50MB")
                        .timestamp(Instant.now())
                        .build()
        );
    }

    /**
     * 400 — Business rule violations (e.g. unsupported file type)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                        .errorCode("BAD_REQUEST")
                        .message(ex.getMessage())
                        .timestamp(Instant.now())
                        .build()
        );
    }

    /**
     * 500 — Catch-all for unexpected errors
     * WHY log at ERROR level here specifically?
     * This is the "something unexpected happened" handler.
     * Everything above is expected/handled. This one means a bug.
     * ERROR level triggers PagerDuty alerts in production.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        // Log with full stack trace for debugging
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        // Return a GENERIC message — never leak internals
        return ResponseEntity.internalServerError().body(
                ErrorResponse.builder()
                        .errorCode("INTERNAL_ERROR")
                        .message("An unexpected error occurred. Please try again or contact support.")
                        .timestamp(Instant.now())
                        .build()
        );
    }
}
