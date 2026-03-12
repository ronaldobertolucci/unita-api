package io.github.ronaldobertolucci.unita.dto.group;

import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GroupCreditCardBillDto(
    Long id,
    String cardCorporateName,
    String cardLastFourDigits,
    LocalDate closingDate,
    LocalDate dueDate,
    CreditCardBillStatus status,
    BigDecimal totalAmount
) {}