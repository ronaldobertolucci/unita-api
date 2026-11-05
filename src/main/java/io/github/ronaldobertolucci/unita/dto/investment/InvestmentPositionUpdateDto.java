package io.github.ronaldobertolucci.unita.dto.investment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentPositionUpdateDto(
    @NotNull @PositiveOrZero BigDecimal currentValue,
    @NotNull LocalDate lastValuationDate
) {}