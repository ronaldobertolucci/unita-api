package io.github.ronaldobertolucci.unita.dto.pocket;

import io.github.ronaldobertolucci.unita.model.pocket.Pocket;

import java.math.BigDecimal;

public record PocketSummaryDto(
        Long id,
        String type,
        String label,
        boolean active,
        BigDecimal balance
) {
    public static PocketSummaryDto of(Pocket pocket, BigDecimal balance) {
        return new PocketSummaryDto(
                pocket.getId(),
                pocket.getClass().getSimpleName(),
                pocket.getLabel(),
                pocket.isActive(),
                balance
        );
    }
}