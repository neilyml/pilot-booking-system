package com.aiimglobal.pilot.booking.system.exception;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiFieldError::field))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body is malformed.", request, List.of());
    }

    @ExceptionHandler({
            InvalidRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    ResponseEntity<ApiError> handleInvalidRequestParameter(
            Exception exception, HttpServletRequest request) {
        log.warn("action=request_rejected method={} path={} reason=invalid_parameter",
                request.getMethod(), request.getRequestURI());
        String message = exception instanceof InvalidRequestParameterException
                ? exception.getMessage()
                : "A request parameter is missing or invalid.";
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETER", message, request, List.of());
    }

    @ExceptionHandler(ResourceConflictException.class)
    ResponseEntity<ApiError> handleConflict(ResourceConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getCode(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> handleInvalidCredentials(
            InvalidCredentialsException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied.", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleConstraintConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "REGISTRATION_CONFLICT",
                "The registration conflicts with an existing account.", request, List.of());
    }

    @ExceptionHandler(MissingReferenceDataException.class)
    ResponseEntity<ApiError> handleMissingReferenceData(
            MissingReferenceDataException exception, HttpServletRequest request) {
        return internalError(request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("action=request_failed method={} path={} exceptionType={}",
                request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
        return internalError(request);
    }

    private ResponseEntity<ApiError> internalError(HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred.", request, List.of());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<ApiFieldError> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), code, message, request.getRequestURI(), fieldErrors));
    }
}
