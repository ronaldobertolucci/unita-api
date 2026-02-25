package io.github.ronaldobertolucci.unita.dto.card;

import io.github.ronaldobertolucci.unita.model.card.CreditCardInstallment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillInstallmentDto(
    Long id,
    String description,
    BigDecimal amount,
    LocalDate purchaseDate,
    Integer installmentNumber,
    Integer totalInstallments
) {
    public BillInstallmentDto(CreditCardInstallment installment) {
        this(
            installment.getId(),
            installment.getPurchase().getDescription(),
            installment.getAmount(),
            installment.getPurchase().getPurchaseDate(),
            installment.getInstallmentNumber(),
            installment.getPurchase().getInstallmentsCount()
        );
    }
}