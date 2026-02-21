package io.github.ronaldobertolucci.unita.dto.pocket;

import io.github.ronaldobertolucci.unita.model.pocket.BenefitAccount;
import io.github.ronaldobertolucci.unita.model.pocket.BenefitAccountStatus;

public record BenefitAccountDto(
        Long id,
        String legalEntityCorporateName,
        String benefitType,
        BenefitAccountStatus status
) {
    public BenefitAccountDto(BenefitAccount benefitAccount) {
        this(
                benefitAccount.getId(),
                benefitAccount.getLegalEntity().getCorporateName(),
                benefitAccount.getBenefitType().getName(),
                benefitAccount.getStatus()
        );
    }
}