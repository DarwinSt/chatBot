package com.financebot.exception;

import com.financebot.dto.error.ApiError;
import com.financebot.dto.error.ValidationFieldError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ValidationFieldError> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new ValidationFieldError(fe.getField(), fe.getDefaultMessage()));
        }
        return build(HttpStatus.BAD_REQUEST, "Validation Failed", "Se encontraron errores de validación", request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ValidationFieldError> errors = ex.getConstraintViolations().stream()
                .map(this::toValidationFieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation Failed", ex.getMessage(), request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Cuerpo de la petición inválido o incompleto", request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String msg = "Parámetro requerido ausente: " + ex.getParameterName();
        return build(HttpStatus.BAD_REQUEST, "Bad Request", msg, request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String msg = "Parámetro inválido: " + ex.getName();
        return build(HttpStatus.BAD_REQUEST, "Bad Request", msg, request, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request, null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "Error interno del servidor",
                request,
                null);
    }

    private ValidationFieldError toValidationFieldError(ConstraintViolation<?> v) {
        String path = v.getPropertyPath() != null ? v.getPropertyPath().toString() : "constraint";
        return new ValidationFieldError(path, v.getMessage());
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String errorTitle,
            String message,
            HttpServletRequest request,
            List<ValidationFieldError> validationErrors) {
        String path = resolvePath(request);
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                errorTitle,
                message,
                path,
                validationErrors);
        return ResponseEntity.status(status).body(body);
    }

    private String resolvePath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
