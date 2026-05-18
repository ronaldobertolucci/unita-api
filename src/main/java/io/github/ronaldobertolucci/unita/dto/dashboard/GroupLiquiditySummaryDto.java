package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.util.List;

public record GroupLiquiditySummaryDto(
        List<GroupMemberLiquiditySummaryDto> members
) {}