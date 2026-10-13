package io.github.ronaldobertolucci.unita.controller;
import io.github.ronaldobertolucci.unita.dto.exception.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=RecurringBillController.class) @Order(Ordered.HIGHEST_PRECEDENCE)
public class RecurringBillErrorHandler {
 @ExceptionHandler({HttpMessageNotReadableException.class,MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class})
 public ResponseEntity<ErrorResponseDto> invalid(Exception ex,HttpServletRequest request) {
  return ResponseEntity.badRequest().body(new ErrorResponseDto(400,"Bad Request","Invalid recurring bill request",request.getRequestURI()));
 }
}
