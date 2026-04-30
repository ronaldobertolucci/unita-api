package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.util.List;

public record GroupMemberIndexerSummaryDto(
        GroupMemberUserDto user,
        List<IndexerSummaryDto> indexerSummary
) {}