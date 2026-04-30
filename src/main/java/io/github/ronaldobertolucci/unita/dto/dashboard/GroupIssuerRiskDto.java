package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.util.List;

public record GroupIssuerRiskDto(
        List<GroupMemberIssuerRiskDto> members
) {}