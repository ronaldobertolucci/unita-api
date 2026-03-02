package io.github.ronaldobertolucci.unita.dto.investment;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentPositionUpdateDto(
    @NotNull BigDecimal currentValue,
    @NotNull LocalDate lastValuationDate
) {}