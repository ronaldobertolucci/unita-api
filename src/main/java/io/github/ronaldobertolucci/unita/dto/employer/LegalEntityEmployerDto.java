package io.github.ronaldobertolucci.unita.dto.employer;

import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;

public record LegalEntityEmployerDto(
    Long id,
    LegalEntityDto legalEntity
) {
    public LegalEntityEmployerDto(LegalEntityEmployer employer) {
        this(
            employer.getId(),
            new LegalEntityDto(employer.getLegalEntity())
        );
    }
}