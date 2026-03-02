package io.github.ronaldobertolucci.unita.dto.investment;

import java.math.BigDecimal;

public record TaxSuggestionDto(
        BigDecimal grossAmount,
        BigDecimal totalInvested,
        BigDecimal earnings,
        BigDecimal suggestedTaxRate,
        BigDecimal suggestedTaxAmount,
        BigDecimal suggestedNetAmount,
        int daysElapsed,
        String taxBasis
) {
}