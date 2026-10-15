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
        @Schema(description = "Assumed acquisition date, not in the future in America/Sao_Paulo; limitations are returned for missing or inconsistent history", example = "2024-01-01")
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDate
) {}
