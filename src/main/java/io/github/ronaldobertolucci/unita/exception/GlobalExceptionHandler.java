package io.github.ronaldobertolucci.unita.exception;

import io.github.ronaldobertolucci.unita.dto.exception.ErrorResponseDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // A concurrent reference can appear after service validation. Only these financial
    // constraints are translated; unrelated integrity/infrastructure failures stay 500.
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleFinancialIntegrity(
            org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
        return handleRuntimeException(ex, request);
    }

    @ExceptionHandler(AccountStateException.class)
    public ResponseEntity<Map<String, Object>> handleAccountState(AccountStateException ex) {
        return ResponseEntity.status(403).header("Cache-Control", "no-store")
                .body(Map.of("status", 403, "code", ex.getCode(), "message", ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationServiceException.class)
    public ResponseEntity<ErrorResponseDto> handleAuthenticationServiceFailure(
            AuthenticationServiceException ex,
            HttpServletRequest request) {
        return handleRuntimeException(ex, request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleEntityNotFound(
            EntityNotFoundException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = new ErrorResponseDto(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = new ErrorResponseDto(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        ErrorResponseDto error = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Invalid input data",
                request.getRequestURI(),
                details,
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fieldError -> new ErrorResponseDto.FieldValidationError(
                                fieldError.getField(), fieldError.getDefaultMessage()))
                        .toList()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ErrorResponseDto> handleAuthenticationErrors(
            Exception ex,
            HttpServletRequest request) {

        ErrorResponseDto error = new ErrorResponseDto(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Invalid email or password",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDto> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {

        // PostgreSQL RESTRICT can surface as a generic JpaSystemException (SQLState
        // 23001), rather than DataIntegrityViolationException. Inspect the cause chain.
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof java.sql.SQLException sql) {
                if ("55P03".equals(sql.getSQLState())) {
                    return ResponseEntity.status(503).body(new ErrorResponseDto(503, "Concurrency Timeout",
                            "Timed out waiting for a financial resource; consult its state before retrying", request.getRequestURI()));
                }
                if ("40P01".equals(sql.getSQLState()) || "40001".equals(sql.getSQLState())) {
                    return ResponseEntity.status(409).body(new ErrorResponseDto(409, "Concurrency Conflict",
                            "Concurrent operation was rolled back; refresh financial data", request.getRequestURI()));
                }
            }
            String constraint = null;
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                constraint = violation.getConstraintName();
            } else if (cause instanceof java.sql.SQLException sql
                    && ("23001".equals(sql.getSQLState()) || "23503".equals(sql.getSQLState()))) {
                constraint = sql.getMessage();
            }
            if (constraint != null && (constraint.toLowerCase(java.util.Locale.ROOT).contains("fk_transfer_source")
                    || constraint.toLowerCase(java.util.Locale.ROOT).contains("fk_transfer_target")
                    || constraint.toLowerCase(java.util.Locale.ROOT).contains("fk_credit_card_bills_payment"))) {
                return handleIllegalStateException(new IllegalStateException(
                        "Resource is linked to a protected financial operation and cannot be deleted"), request);
            }
            cause = cause.getCause();
        }

        ErrorResponseDto error = new ErrorResponseDto(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        ErrorResponseDto error = new ErrorResponseDto(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler({InvalidTokenException.class, TokenExpiredException.class})
    public ResponseEntity<ErrorResponseDto> handleTokenExceptions(
            RuntimeException ex,
            HttpServletRequest request) {

        ErrorResponseDto error = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid Token",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(Map.of("status", 403, "code", "ACCESS_DENIED"));
    }
}
