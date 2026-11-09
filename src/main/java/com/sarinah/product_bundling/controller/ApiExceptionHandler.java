package com.sarinah.product_bundling.controller;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Input yang gagal validasi dijawab 400 dengan pesan singkat (bukan 500). */
@RestControllerAdvice(basePackageClasses = CatalogueController.class)
public class ApiExceptionHandler {

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<Map<String, Object>> invalidInput(Exception ex) {
        List<String> messages;
        if (ex instanceof HandlerMethodValidationException h) {
            messages = h.getAllErrors().stream().map(e -> e.getDefaultMessage()).distinct().limit(5).toList();
        } else {
            messages = ((ConstraintViolationException) ex).getConstraintViolations().stream()
                    .map(v -> v.getMessage()).distinct().limit(5).toList();
        }
        return body(messages);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException ex) {
        return body(ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage).distinct().limit(5).toList());
    }

    private ResponseEntity<Map<String, Object>> body(List<String> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "validation_failed");
        body.put("messages", messages);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
