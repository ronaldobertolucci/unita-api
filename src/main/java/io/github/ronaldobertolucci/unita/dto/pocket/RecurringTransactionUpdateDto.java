package io.github.ronaldobertolucci.unita.dto.pocket;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecurringTransactionUpdateDto(
    @NotNull @Positive BigDecimal amount
) {}