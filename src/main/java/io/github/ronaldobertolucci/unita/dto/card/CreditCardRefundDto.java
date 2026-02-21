package io.github.ronaldobertolucci.unita.dto.card;

import io.github.ronaldobertolucci.unita.model.card.CreditCardRefund;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditCardRefundDto(
        Long id,
        String description,
        BigDecimal amount,
        LocalDate refundDate
) {
    public CreditCardRefundDto(CreditCardRefund refund) {
        this(
                refund.getId(),
                refund.getDescription(),
                refund.getAmount(),
                refund.getRefundDate()
        );
    }
}