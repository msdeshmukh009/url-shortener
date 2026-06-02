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
