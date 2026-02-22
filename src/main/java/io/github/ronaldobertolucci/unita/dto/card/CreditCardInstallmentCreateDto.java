package io.github.ronaldobertolucci.unita.dto.card;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreditCardInstallmentCreateDto(
        @NotNull @Positive Integer installmentNumber,
        @NotNull @Positive BigDecimal amount
) {
}