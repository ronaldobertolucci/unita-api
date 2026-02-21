package io.github.ronaldobertolucci.unita.dto.admin;

import io.github.ronaldobertolucci.unita.model.finance.CardBrand;

public record CardBrandDto(
        Long id,
        String name
) {
    public CardBrandDto(CardBrand cardBrand) {
        this(cardBrand.getId(), cardBrand.getName());
    }
}