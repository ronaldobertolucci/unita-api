package io.github.ronaldobertolucci.unita.dto.admin;

import io.github.ronaldobertolucci.unita.model.finance.BenefitType;

public record BenefitTypeDto(
        Long id,
        String name
) {
    public BenefitTypeDto(BenefitType benefitType) {
        this(benefitType.getId(), benefitType.getName());
    }
}