package io.github.ronaldobertolucci.unita.dto.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponseDto(
        LocalDateTime timestamp,
        Integer status,
        String error,
        String message,
        String path,
        List<String> details,
        List<FieldValidationError> errors
) {
    public record FieldValidationError(String field, String message) {
    }

    public ErrorResponseDto(LocalDateTime timestamp, Integer status, String error, String message,
                            String path, List<String> details) {
        this(timestamp, status, error, message, path, details, null);
    }

    public ErrorResponseDto(Integer status, String error, String message, String path) {
        this(LocalDateTime.now(), status, error, message, path, null);
    }

    public ErrorResponseDto(Integer status, String error, String message, String path, List<String> details) {
        this(LocalDateTime.now(), status, error, message, path, details);
    }

    public ErrorResponseDto(Integer status, String error, String message, String path,
                            List<String> details, List<FieldValidationError> errors) {
        this(LocalDateTime.now(), status, error, message, path, details, errors);
    }
}
