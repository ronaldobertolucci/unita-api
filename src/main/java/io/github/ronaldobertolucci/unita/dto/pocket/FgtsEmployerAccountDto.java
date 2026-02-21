package io.github.ronaldobertolucci.unita.dto.pocket;

import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;
import io.github.ronaldobertolucci.unita.model.pocket.FgtsEmployerAccount;
import io.github.ronaldobertolucci.unita.model.pocket.FgtsEmployerAccountStatus;

import java.time.LocalDate;

public record FgtsEmployerAccountDto(
        Long id,
        String employerName,
        LocalDate admissionDate,
        LocalDate dismissalDate,
        FgtsEmployerAccountStatus status
) {
    public FgtsEmployerAccountDto(FgtsEmployerAccount account) {
        this(
                account.getId(),
                resolveEmployerName(account),
                account.getAdmissionDate(),
                account.getDismissalDate(),
                account.getStatus()
        );
    }

    private static String resolveEmployerName(FgtsEmployerAccount account) {
        return switch (account.getEmployer()) {
            case IndividualEmployer e -> e.getName();
            case LegalEntityEmployer e -> e.getLegalEntity().getCorporateName();
            default -> "Unknown";
        };
    }
}