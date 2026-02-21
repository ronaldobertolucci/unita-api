package io.github.ronaldobertolucci.unita.dto.card;

import io.github.ronaldobertolucci.unita.model.card.CreditCardPurchase;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditCardPurchaseDto(
        Long id,
        String description,
        BigDecimal totalValue,
        LocalDate purchaseDate,
        Integer installmentsCount
) {
    public CreditCardPurchaseDto(CreditCardPurchase purchase) {
        this(
                purchase.getId(),
                purchase.getDescription(),
                purchase.getTotalValue(),
                purchase.getPurchaseDate(),
                purchase.getInstallmentsCount()
        );
    }
}