package io.github.ronaldobertolucci.unita.dto.group;

import io.github.ronaldobertolucci.unita.dto.investment.AssetDetailDto;

import java.util.List;

public record GroupMemberInvestmentsDto(
    String memberName,
    List<AssetDetailDto> assets
) {}