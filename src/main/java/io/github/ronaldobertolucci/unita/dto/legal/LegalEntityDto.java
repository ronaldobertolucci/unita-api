package io.github.ronaldobertolucci.unita.dto.legal;

import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.swagger.v3.oas.annotations.media.Schema;

public record LegalEntityDto(
        @Schema(description = "Legacy numeric institution ID; private to the authenticated user") Long id,
        @Schema(description = "New values are canonical uppercase text; historical text is returned unchanged, even when invalid", example = "00000000E08G12") String cnpj,
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