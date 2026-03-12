package io.github.ronaldobertolucci.unita.dto.group;

import java.math.BigDecimal;

public record CategoryAmountDto(
    String categoryName,
    BigDecimal totalAmount
) {}