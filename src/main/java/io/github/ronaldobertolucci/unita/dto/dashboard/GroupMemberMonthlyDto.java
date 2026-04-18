package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.util.List;

public record GroupMemberMonthlyDto(
        GroupMemberUserDto user,
        List<GroupMonthlyFinancialSummaryDto> monthly
) {}