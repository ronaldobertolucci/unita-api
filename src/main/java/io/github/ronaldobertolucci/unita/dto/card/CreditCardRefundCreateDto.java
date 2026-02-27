package io.github.ronaldobertolucci.unita.dto.card;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditCardRefundCreateDto(
        @NotBlank @Size(max = 255) String description,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate refundDate,
        @NotNull Long categoryId
) {}