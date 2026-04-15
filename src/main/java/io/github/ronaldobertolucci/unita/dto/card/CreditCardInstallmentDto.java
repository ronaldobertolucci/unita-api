package io.github.ronaldobertolucci.unita.dto.card;

import io.github.ronaldobertolucci.unita.dto.category.CategoryDto;
import io.github.ronaldobertolucci.unita.model.card.CreditCardInstallment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditCardInstallmentDto(
        Long id,
        Integer installmentNumber,
        LocalDate installmentDate,
        BigDecimal amount,
        Long creditCardBillId,
        LocalDate billDueDate,
        CategoryDto category
) {
    public CreditCardInstallmentDto(CreditCardInstallment installment) {
        this(
                installment.getId(),
                installment.getInstallmentNumber(),
                installment.getInstallmentDate(),
                installment.getAmount(),
                installment.getCreditCardBill().getId(),
                installment.getCreditCardBill().getDueDate(),
                new CategoryDto(installment.getCategory())
        );
    }
}