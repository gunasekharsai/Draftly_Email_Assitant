package com.draftly.ai.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.draftly.ai.dto.ApiErrorDto;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorDto> handleBadRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, exception, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorDto> handleIllegalState(IllegalStateException exception, HttpServletRequest request) {
        return build(HttpStatus.BAD_GATEWAY, exception, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleUnexpectedError(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, exception, request);
    }

    private ResponseEntity<ApiErrorDto> build(HttpStatus status, Exception exception, HttpServletRequest request) {
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Unexpected server error."
                : exception.getMessage();

        return ResponseEntity.status(status).body(new ApiErrorDto(
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                Instant.now()
        ));
    }
}
