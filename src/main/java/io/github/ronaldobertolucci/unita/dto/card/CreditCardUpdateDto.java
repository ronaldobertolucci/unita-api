package io.github.ronaldobertolucci.unita.dto.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreditCardUpdateDto(
        @Min(1) @Max(31) Integer closingDay,
        @Min(1) @Max(31) Integer dueDay,
        @Positive BigDecimal creditLimit
) {}