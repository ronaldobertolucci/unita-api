package io.github.ronaldobertolucci.unita.dto.card;

import io.github.ronaldobertolucci.unita.model.card.RecurringPurchase;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringPurchaseDto(
        Long id,
        String description,
        BigDecimal amount,
        String periodicity,
        LocalDate startDate,
        LocalDate endDate
) {
    public RecurringPurchaseDto(RecurringPurchase recurringPurchase) {
        this(
                recurringPurchase.getId(),
                recurringPurchase.getDescription(),
                recurringPurchase.getAmount(),
                recurringPurchase.getPeriodicity().getName(),
                recurringPurchase.getStartDate(),
                recurringPurchase.getEndDate()
        );
    }
}