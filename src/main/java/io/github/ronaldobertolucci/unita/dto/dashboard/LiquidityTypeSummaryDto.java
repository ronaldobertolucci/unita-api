package io.github.ronaldobertolucci.unita.dto.dashboard;

import io.github.ronaldobertolucci.unita.model.investment.LiquidityType;

import java.math.BigDecimal;

public record LiquidityTypeSummaryDto(
        LiquidityType liquidityType,
        BigDecimal totalCurrentValue
) {
}
