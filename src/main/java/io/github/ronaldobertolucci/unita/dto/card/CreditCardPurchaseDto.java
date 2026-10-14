package io.github.ronaldobertolucci.unita.dto.card;

import io.github.ronaldobertolucci.unita.model.card.CreditCardPurchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreditCardPurchaseDto(
        Long id,
        String description,
        BigDecimal totalValue,
        LocalDate purchaseDate,
        Integer installmentsCount,
        List<CreditCardInstallmentDto> installments
) {
    public CreditCardPurchaseDto(Long id, String description, BigDecimal totalValue, LocalDate purchaseDate, Integer installmentsCount) {
        this(id, description, totalValue, purchaseDate, installmentsCount, List.of());
    }

    public CreditCardPurchaseDto(CreditCardPurchase purchase, List<CreditCardInstallmentDto> installments) {
        this(purchase.getId(), purchase.getDescription(), purchase.getTotalValue(), purchase.getPurchaseDate(),
                purchase.getInstallmentsCount(), installments);
    }

    public CreditCardPurchaseDto(CreditCardPurchase purchase) {
        this(
                purchase.getId(),
                purchase.getDescription(),
                purchase.getTotalValue(),
                purchase.getPurchaseDate(),
                purchase.getInstallmentsCount(),
                List.of()
        );
    }
}