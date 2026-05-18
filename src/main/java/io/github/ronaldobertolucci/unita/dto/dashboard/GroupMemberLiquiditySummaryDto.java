package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.util.List;

public record GroupMemberLiquiditySummaryDto(
        GroupMemberUserDto user,
        List<LiquidityTypeSummaryDto> liquidityTypeSummary
) {}