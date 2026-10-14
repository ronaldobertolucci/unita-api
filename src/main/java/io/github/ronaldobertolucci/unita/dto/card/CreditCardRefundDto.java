package io.github.ronaldobertolucci.unita.dto.card;

import io.github.ronaldobertolucci.unita.dto.category.CategoryDto;
import io.github.ronaldobertolucci.unita.model.card.CreditCardRefund;

import java.math.BigDecimal;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreditCardRefundDto(
        Long id,
        String description,
        BigDecimal amount,
        LocalDate refundDate,
        CategoryDto category,
        @Schema(description = "Original purchase ID, null for unlinked and historical credits") Long purchaseId
) {
    public CreditCardRefundDto(Long id, String description, BigDecimal amount, LocalDate refundDate, CategoryDto category) {
        this(id, description, amount, refundDate, category, null);
    }
    public CreditCardRefundDto(CreditCardRefund refund) {
        this(
                refund.getId(),
                refund.getDescription(),
                refund.getAmount(),
                refund.getRefundDate(),
                new CategoryDto(refund.getCategory()),
                refund.getPurchase() == null ? null : refund.getPurchase().getId()
        );
    }
}
