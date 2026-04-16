package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.util.List;

public record FinancialSummaryDto(
        List<CategorySummaryDto> incomes,
        List<CategorySummaryDto> expenses
) {}