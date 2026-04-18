package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.util.List;

public record GroupMemberFinancialSummaryDto(
        GroupMemberUserDto user,
        List<CategorySummaryDto> incomes,
        List<CategorySummaryDto> expenses
) {}