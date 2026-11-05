package io.github.ronaldobertolucci.unita.dto.investment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Legacy numeric estimate only, without IOF or annual/monthly tax assessment. Never replaces the tax informed on redemption.")
public record TaxSuggestionDto(
        BigDecimal grossAmount,
        @Schema(description = "Cumulative historical contributions, unchanged by partial sales")
        BigDecimal totalInvested,
        @Schema(description = "Gross amount minus costBasis of the sold units; may be negative")
        BigDecimal earnings,
        @Schema(description = "Suggested rate as a fraction: 0.15 means 15 percent")
        BigDecimal suggestedTaxRate,
        BigDecimal suggestedTaxAmount,
        BigDecimal suggestedNetAmount,
        @Schema(description = "Days between supplied purchaseDate and saleDate; does not represent every contribution")
        int daysElapsed,
        @Schema(description = "Category-specific regime, taxable base and any limitation of the estimate")
        String taxBasis,
        @Schema(description = "Non-negative taxable base: earnings for fixed income/VGBL, gross amount for PGBL/closed pension, zero if exempt")
        BigDecimal taxBase,
        @Schema(description = "LIMITED_ESTIMATE for missing/conflicting purchase history, unknown legacy units/costs or YIELD receipts")
        TaxSuggestionStatus estimationStatus,
        @Schema(description = "Limitations to display before using the estimate; an empty list does not imply a complete tax assessment")
        List<String> warnings,
        @Schema(description = "Proportional remaining acquisition cost of the units to sell; not cumulative totalInvested")
        BigDecimal costBasis
) {
    public TaxSuggestionDto {
        warnings = List.copyOf(warnings);
    }
}
