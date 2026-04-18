package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record GroupMemberDashboardDto(
        GroupMemberUserDto user,
        List<CategorySummaryDto> pockets,
        List<CategorySummaryDto> investments,
        BigDecimal totalOpenBills
) {}