package io.github.ronaldobertolucci.unita.dto.investment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentYieldDto(
    @Schema(description = "Strictly positive withdrawal from currentValue, credited to the pocket", example = "20.00")
    @NotNull @Positive BigDecimal amount,
    @NotNull LocalDate transactionDate,
    @NotNull Long pocketId,
    @NotNull Long categoryId,
    String notes
) {}