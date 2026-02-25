package io.github.ronaldobertolucci.unita.dto.pocket;

public record TransferDto(
    TransactionDto sourceTransaction,
    TransactionDto targetTransaction
) {}