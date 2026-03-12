package io.github.ronaldobertolucci.unita.dto.group;

import io.github.ronaldobertolucci.unita.model.employer.Employer;
import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;
import io.github.ronaldobertolucci.unita.model.pocket.BankAccount;
import io.github.ronaldobertolucci.unita.model.pocket.BenefitAccount;
import io.github.ronaldobertolucci.unita.model.pocket.FgtsEmployerAccount;
import io.github.ronaldobertolucci.unita.model.pocket.Pocket;
import org.hibernate.Hibernate;

import java.math.BigDecimal;

public record GroupPocketDto(
    Long id,
    String type,
    BigDecimal balance,
    String institutionName,    // BankAccount, BenefitAccount
    String agency,             // BankAccount
    String number,             // BankAccount
    String benefitTypeName,    // BenefitAccount
    String employerName,       // FgtsEmployerAccount
    String employerDocument    // FgtsEmployerAccount (cpf ou cnpj)
) {
    public static GroupPocketDto from(Pocket pocket, BigDecimal balance) {
        if (pocket instanceof BankAccount ba) {
            return new GroupPocketDto(
                ba.getId(), "BANK_ACCOUNT", balance,
                ba.getLegalEntity().getCorporateName(),
                ba.getAgency(), ba.getNumber(),
                null, null, null
            );
        } else if (pocket instanceof BenefitAccount bea) {
            return new GroupPocketDto(
                bea.getId(), "BENEFIT_ACCOUNT", balance,
                bea.getLegalEntity().getCorporateName(),
                null, null,
                bea.getBenefitType().getName(),
                null, null
            );
        } else if (pocket instanceof FgtsEmployerAccount fgts) {
            Employer employer = (Employer) Hibernate.unproxy(fgts.getEmployer());
            String name = employer instanceof LegalEntityEmployer le
                    ? le.getLegalEntity().getCorporateName() : ((IndividualEmployer) employer).getName();
            String document = employer instanceof LegalEntityEmployer le
                    ? le.getLegalEntity().getCnpj() : ((IndividualEmployer) employer).getCpf();
            return new GroupPocketDto(
                    fgts.getId(), "FGTS_EMPLOYER_ACCOUNT", balance,
                    null, null, null, null,
                    name, document
            );
        } else {
            return new GroupPocketDto(
                pocket.getId(), "CASH", balance,
                null, null, null, null, null, null
            );
        }
    }
}