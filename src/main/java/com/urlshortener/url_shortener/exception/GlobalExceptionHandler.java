package com.urlshortener.url_shortener.exception;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.urlshortener.url_shortener.controller.UrlShortenerController;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice(assignableTypes = UrlShortenerController.class)
public class GlobalExceptionHandler {

        @ExceptionHandler(ShortCodeNotFoundException.class)
        public ResponseEntity<Map<String, Object>> handleShortCodeNotFound(ShortCodeNotFoundException ex) {
                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", 404,
                                "error", "Not Found",
                                "message", ex.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
                String message = ex.getBindingResult().getFieldErrors().stream()
                                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                                .collect(Collectors.joining(", "));
                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", 400,
                                "error", "Bad Request",
                                "message", message);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
                String message = ex.getConstraintViolations().stream()
                                .map(v -> v.getMessage())
                                .collect(Collectors.joining(", "));
                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", 400,
                                "error", "Bad Request",
                                "message", message);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }

        @ExceptionHandler(MissingRequestHeaderException.class)
        public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
                String message = "Required header '" + ex.getHeaderName() + "' is missing";
                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", 400,
                                "error", "Bad Request",
                                "message", message);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }

        @ExceptionHandler(InvalidApiKeyException.class)
        public ResponseEntity<Map<String, Object>> handleInvalidApiKey(InvalidApiKeyException ex) {
                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", HttpStatus.UNAUTHORIZED,
                                "error", "Invalid Api Key",
                                "message", ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        @ExceptionHandler(ForbiddenException.class)
        public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", HttpStatus.FORBIDDEN,
                                "error", "Forbidden",
                                "message", ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        @ExceptionHandler(UrlExpiredException.class)
        public ResponseEntity<Map<String, Object>> handleExpired(UrlExpiredException ex) {
                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", HttpStatus.GONE,
                                "error", "GONE",
                                "message", ex.getMessage());
                return ResponseEntity.status(HttpStatus.GONE).body(body);
        }

        @ExceptionHandler(ShortCodeTakenException.class)
        public ResponseEntity<Map<String, Object>> handleShortCodeTaken(ShortCodeTakenException ex) {
                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", HttpStatus.CONFLICT,
                                "error", "CONFLICT",
                                "message", ex.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        @ExceptionHandler(TierRestrictedException.class)
        public ResponseEntity<Map<String, Object>> handleTierRestrictedException(TierRestrictedException ex) {
                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", HttpStatus.FORBIDDEN,
                                "error", "FORBIDDEN",
                                "message", ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        @ExceptionHandler(PasswordRequiredException.class)
        public ResponseEntity<Map<String, Object>> handlePasswordRequiredException(PasswordRequiredException ex) {
                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", HttpStatus.UNAUTHORIZED,
                                "error", "UNAUTHORIZED",
                                "message", ex.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        private String extractCleanMessage(HttpMessageNotReadableException ex) {
                Throwable cause = ex.getCause();

                while (cause != null) {
                        if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife) {
                                String field = ife.getPath().stream()
                                                .map(ref -> ref.getFieldName())
                                                .filter(java.util.Objects::nonNull)
                                                .collect(java.util.stream.Collectors.joining("."));
                                return "Invalid format for field '" + field
                                                + "': value '" + ife.getValue()
                                                + "'. Expected ISO-8601 timestamp with timezone (e.g., 2026-06-10T09:30:00Z)";
                        }

                        if (cause instanceof java.time.format.DateTimeParseException dtpe) {
                                return "Invalid or old timestamp: '" + dtpe.getParsedString()
                                                + "'. Expected ISO-8601 with timezone (e.g., 2026-06-10T09:30:00Z)";
                        }

                        if (cause instanceof com.fasterxml.jackson.core.JsonParseException) {
                                return "Malformed JSON in request body";
                        }

                        cause = cause.getCause();
                }

                return "Request body is missing or malformed";
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
                String message = extractCleanMessage(ex);

                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", HttpStatus.BAD_REQUEST,
                                "error", "Bad Request",
                                "message", message);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", 500,
                                "error", "Internal Server Error",
                                "message", "Something went wrong");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
}
