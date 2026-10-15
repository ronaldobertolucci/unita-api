package io.github.ronaldobertolucci.unita.dto.investment;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentBuyDto(
    @Schema(description = "Legacy unit price, multiplied by quantity to obtain the contribution", example = "100.00")
    @NotNull BigDecimal amount,
    @NotNull BigDecimal quantity,
    @NotNull LocalDate transactionDate,
    @NotNull Long pocketId,
    @NotNull Long categoryId,
    String notes
) {}
