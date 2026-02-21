package io.github.ronaldobertolucci.unita.dto.card;

import io.github.ronaldobertolucci.unita.model.card.CreditCardBill;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditCardBillDto(
        Long id,
        LocalDate closingDate,
        LocalDate dueDate,
        CreditCardBillStatus status,
        BigDecimal totalInstallments,
        BigDecimal totalRefunds,
        BigDecimal totalAmount
) {
    public CreditCardBillDto(CreditCardBill bill, BigDecimal totalInstallments, BigDecimal totalRefunds) {
        this(
                bill.getId(),
                bill.getClosingDate(),
                bill.getDueDate(),
                bill.getStatus(),
                totalInstallments,
                totalRefunds,
                totalInstallments.subtract(totalRefunds)
        );
    }
}