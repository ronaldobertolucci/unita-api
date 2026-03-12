package io.github.ronaldobertolucci.unita.dto.group;

import java.util.List;

public record GroupMemberBalanceDto(
    String memberName,
    List<GroupPocketDto> pockets
) {}