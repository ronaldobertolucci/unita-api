package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.math.BigDecimal;

public record GroupMemberNetProfitDto(
        GroupMemberUserDto user,
        BigDecimal netProfit
) {}