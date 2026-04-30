package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.util.List;

public record GroupMemberIssuerRiskDto(
        GroupMemberUserDto user,
        List<IssuerRiskSummaryDto> issuerRisk
) {}