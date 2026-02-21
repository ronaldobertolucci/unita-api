package io.github.ronaldobertolucci.unita.dto.card;

import io.github.ronaldobertolucci.unita.model.card.CreditCard;

import java.math.BigDecimal;

public record CreditCardDto(
        Long id,
        String legalEntityCorporateName,
        String lastFourDigits,
        String cardBrand,
        BigDecimal creditLimit,
        Integer closingDay,
        Integer dueDay
) {
    public CreditCardDto(CreditCard creditCard) {
        this(
                creditCard.getId(),
                creditCard.getLegalEntity().getCorporateName(),
                creditCard.getLastFourDigits(),
                creditCard.getCardBrand().getName(),
                creditCard.getCreditLimit(),
                creditCard.getClosingDay(),
                creditCard.getDueDay()
        );
    }
}