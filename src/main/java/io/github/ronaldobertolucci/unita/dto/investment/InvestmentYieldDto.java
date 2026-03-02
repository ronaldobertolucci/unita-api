package io.github.ronaldobertolucci.unita.dto.investment;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentYieldDto(
    @NotNull BigDecimal amount,
    @NotNull LocalDate transactionDate,
    @NotNull Long pocketId,
    @NotNull Long categoryId,
    String notes
) {}