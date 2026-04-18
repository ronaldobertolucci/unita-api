package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.math.BigDecimal;

public record GroupMonthlyFinancialSummaryDto(
        String month,
        BigDecimal totalIncome,
        BigDecimal totalExpense
) {}