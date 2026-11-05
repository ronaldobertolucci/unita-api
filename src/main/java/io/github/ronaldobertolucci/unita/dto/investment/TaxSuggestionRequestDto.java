package io.github.ronaldobertolucci.unita.dto.investment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxSuggestionRequestDto(
        @Schema(description = "Strictly positive gross redemption amount in the legacy numeric contract", example = "1100.00")
        @NotNull @Positive BigDecimal grossAmount,
        @Schema(description = "Assumed acquisition date, not after saleDate; limitations are returned for missing or inconsistent history", example = "2024-01-01")
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDate,
        @Schema(description = "Quantity to sell, no greater than the remaining units")
        @NotNull @Positive BigDecimal quantity,
        @Schema(description = "Reference date of the proposed sale; used to calculate the holding period")
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate saleDate
) {}
