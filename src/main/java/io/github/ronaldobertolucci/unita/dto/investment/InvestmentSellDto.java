package io.github.ronaldobertolucci.unita.dto.investment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentSellDto(
    @Schema(description = "Gross redemption amount; must be greater than zero", example = "100.00")
    @NotNull @Positive BigDecimal grossAmount,
    @Schema(description = "Tax informed by the client; must be non-negative and no greater than grossAmount", example = "10.00")
    @NotNull @PositiveOrZero BigDecimal taxAmount,
    @NotNull LocalDate transactionDate,
    @NotNull Long pocketId,
    @NotNull Long categoryId,
    String notes
) {}
