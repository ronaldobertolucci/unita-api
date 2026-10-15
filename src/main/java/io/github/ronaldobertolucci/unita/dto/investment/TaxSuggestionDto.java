package io.github.ronaldobertolucci.unita.dto.investment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Legacy numeric estimate only, without IOF or annual/monthly tax assessment. Never replaces the tax informed on redemption.")
public record TaxSuggestionDto(
        BigDecimal grossAmount,
        BigDecimal totalInvested,
        @Schema(description = "Gross amount minus total invested; may be negative")
        BigDecimal earnings,
        @Schema(description = "Suggested rate as a fraction: 0.15 means 15 percent")
        BigDecimal suggestedTaxRate,
        BigDecimal suggestedTaxAmount,
        BigDecimal suggestedNetAmount,
        @Schema(description = "Days since the supplied purchaseDate, using today's date in America/Sao_Paulo; does not represent every contribution")
        int daysElapsed,
        @Schema(description = "Category-specific regime, taxable base and any limitation of the estimate")
        String taxBasis,
        @Schema(description = "Non-negative taxable base: earnings for fixed income/VGBL, gross amount for PGBL/closed pension, zero if exempt")
        BigDecimal taxBase,
        @Schema(description = "LIMITED_ESTIMATE when purchase history is missing, spans multiple dates or disagrees with purchaseDate")
        TaxSuggestionStatus estimationStatus,
        @Schema(description = "Limitations to display before using the estimate; an empty list does not imply a complete tax assessment")
        List<String> warnings
) {
    public TaxSuggestionDto {
        warnings = List.copyOf(warnings);
    }
}
