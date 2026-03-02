package io.github.ronaldobertolucci.unita.dto.investment;

import io.github.ronaldobertolucci.unita.model.investment.InvestmentTransaction;
import io.github.ronaldobertolucci.unita.model.investment.InvestmentTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentTransactionDto(
    Long id,
    InvestmentTransactionType type,
    BigDecimal amount,
    LocalDate transactionDate,
    String notes,
    Long pocketTransactionId
) {
    public InvestmentTransactionDto(InvestmentTransaction tx) {
        this(
            tx.getId(),
            tx.getType(),
            tx.getAmount(),
            tx.getTransactionDate(),
            tx.getNotes(),
            tx.getTransaction() != null ? tx.getTransaction().getId() : null
        );
    }
}