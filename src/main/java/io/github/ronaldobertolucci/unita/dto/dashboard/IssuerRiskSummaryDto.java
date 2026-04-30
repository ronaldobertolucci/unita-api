package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.math.BigDecimal;

public record IssuerRiskSummaryDto(
        String legalEntityName,
        BigDecimal totalCurrentValue
) {}