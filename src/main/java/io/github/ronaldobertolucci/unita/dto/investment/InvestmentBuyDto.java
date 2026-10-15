package io.github.ronaldobertolucci.unita.dto.investment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentBuyDto(
    @Schema(description = "Legacy unit price, multiplied by quantity to obtain the contribution", example = "100.00")
    @NotNull @Positive BigDecimal amount,
    @Schema(description = "Strictly positive legacy quantity", example = "10")
    @NotNull @Positive BigDecimal quantity,
    @NotNull LocalDate transactionDate,
    @NotNull Long pocketId,
    @NotNull Long categoryId,
    String notes
) {}
