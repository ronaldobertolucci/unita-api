package io.github.ronaldobertolucci.unita.dto.pocket;

import io.github.ronaldobertolucci.unita.dto.category.CategoryDto;
import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionDto(
        Long id,
        BigDecimal amount,
        Direction direction,
        LocalDate transactionDate,
        String description,
        CategoryDto category,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Backend origin: TRANSFER, FGTS_WITHDRAWAL, BILL_PAYMENT or UNLINKED; UNLINKED does not prove a manual origin")
        String operationType,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Transfer operation ID or paid bill ID; null for unlinked records")
        Long operationId,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Whether individual deletion is currently allowed; server revalidates")
        boolean canDelete
) {
    public TransactionDto(Long id, BigDecimal amount, Direction direction, LocalDate transactionDate,
                          String description, CategoryDto category) {
        this(id, amount, direction, transactionDate, description, category, "UNLINKED", null, true);
    }

    public TransactionDto(Transaction transaction) {
        this(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getDirection(),
                transaction.getTransactionDate(),
                transaction.getDescription(),
                new CategoryDto(transaction.getCategory()),
                transaction.transferOperation() != null ? transaction.transferOperation().getType()
                        : transaction.getPaymentBill() != null ? "BILL_PAYMENT" : "UNLINKED",
                transaction.transferOperation() != null ? transaction.transferOperation().getId()
                        : transaction.getPaymentBill() != null ? transaction.getPaymentBill().getId() : null,
                !transaction.isDeletionProtected()
        );
    }
}