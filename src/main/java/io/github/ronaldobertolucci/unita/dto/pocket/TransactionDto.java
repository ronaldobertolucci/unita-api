package io.github.ronaldobertolucci.unita.dto.pocket;

import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionDto(
        Long id,
        BigDecimal amount,
        Direction direction,
        LocalDate transactionDate,
        String description
) {
    public TransactionDto(Transaction transaction) {
        this(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getDirection(),
                transaction.getTransactionDate(),
                transaction.getDescription()
        );
    }
}