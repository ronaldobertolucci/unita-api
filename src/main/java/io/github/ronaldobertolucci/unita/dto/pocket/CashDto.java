package io.github.ronaldobertolucci.unita.dto.pocket;

import io.github.ronaldobertolucci.unita.model.pocket.Cash;

import java.math.BigDecimal;

public record CashDto(
        Long id,
        BigDecimal balance
) {
    public CashDto(Cash cash, BigDecimal balance) {
        this(cash.getId(), balance);
    }
}