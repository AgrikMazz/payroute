package dev.payroute.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(ApiError.of(ex.status().value(), ex.status().getReasonPhrase(), ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        var fields = new LinkedHashMap<String, String>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(java.time.Instant.now(), 400, "Bad Request", "Request validation failed", fields));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicate(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(409, "Conflict", "A record with this idempotency key already exists; replay the original request to retrieve it."));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        return ResponseEntity.internalServerError().body(ApiError.of(500, "Internal Server Error", "The request could not be completed."));
    }
}
