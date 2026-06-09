package com.urlshortener.url_shortener.exception;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class BaseExceptionHandler {
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

        @ExceptionHandler(HeaderRequiredException.class)
        public ResponseEntity<Map<String, Object>> handleMissingHeader(HeaderRequiredException ex) {
                String message = ex.getMessage();
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

        @ExceptionHandler(BlockedApiKeyException.class)
        public ResponseEntity<Map<String, Object>> handleBlocked(BlockedApiKeyException ex) {
                Map<String, Object> body = Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", 403,
                                "error", "Forbidden",
                                "message", ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
}
