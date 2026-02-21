package io.github.ronaldobertolucci.unita.dto.employer;

import io.github.ronaldobertolucci.unita.model.employer.Employer;
import io.github.ronaldobertolucci.unita.model.employer.EmployerType;
import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;

public record EmployerDto(
        Long id,
        EmployerType type,
        String name,
        String document
) {
    public static EmployerDto from(Employer employer) {
        return switch (employer) {
            case IndividualEmployer e -> new EmployerDto(e.getId(), EmployerType.INDIVIDUAL, e.getName(), e.getCpf());
            case LegalEntityEmployer e -> new EmployerDto(e.getId(), EmployerType.LEGAL_ENTITY,
                    e.getLegalEntity().getCorporateName(), e.getLegalEntity().getCnpj());
            default -> throw new IllegalStateException("Unknown employer type");
        };
    }
}