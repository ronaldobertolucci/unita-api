package io.github.ronaldobertolucci.unita.dto.employer;

import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;

public record IndividualEmployerDto(
    Long id,
    String cpf,
    String name
) {
    public IndividualEmployerDto(IndividualEmployer employer) {
        this(
            employer.getId(),
            employer.getCpf(),
            employer.getName()
        );
    }
}