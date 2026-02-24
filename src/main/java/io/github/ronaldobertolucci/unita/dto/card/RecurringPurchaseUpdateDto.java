package io.github.ronaldobertolucci.unita.dto.card;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecurringPurchaseUpdateDto(
    @NotNull @Positive BigDecimal amount
) {}