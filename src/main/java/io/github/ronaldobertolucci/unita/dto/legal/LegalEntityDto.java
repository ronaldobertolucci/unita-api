package io.github.ronaldobertolucci.unita.dto.legal;

import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;

public record LegalEntityDto(
        Long id,
        String cnpj,
        String corporateName,
        String tradeName,
        String stateRegistration
) {
    public LegalEntityDto(LegalEntity legalEntity) {
        this(
                legalEntity.getId(),
                legalEntity.getCnpj(),
                legalEntity.getCorporateName(),
                legalEntity.getTradeName(),
                legalEntity.getStateRegistration()
        );
    }
}