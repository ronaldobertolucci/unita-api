package io.github.ronaldobertolucci.unita.dto.pocket;

import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.pocket.RecurringTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringTransactionDto(
        Long id,
        BigDecimal amount,
        Direction direction,
        String periodicity,
        LocalDate startDate,
        LocalDate endDate,
        String description
) {
    public RecurringTransactionDto(RecurringTransaction recurringTransaction) {
        this(
                recurringTransaction.getId(),
                recurringTransaction.getAmount(),
                recurringTransaction.getDirection(),
                recurringTransaction.getPeriodicity().getName(),
                recurringTransaction.getStartDate(),
                recurringTransaction.getEndDate(),
                recurringTransaction.getDescription()
        );
    }
}