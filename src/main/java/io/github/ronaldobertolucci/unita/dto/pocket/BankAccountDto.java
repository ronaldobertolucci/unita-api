package io.github.ronaldobertolucci.unita.dto.pocket;

import io.github.ronaldobertolucci.unita.model.pocket.BankAccount;
import io.github.ronaldobertolucci.unita.model.pocket.BankAccountStatus;

public record BankAccountDto(
        Long id,
        String legalEntityCorporateName,
        String number,
        String agency,
        String bankAccountType,
        BankAccountStatus status
) {
    public BankAccountDto(BankAccount bankAccount) {
        this(
                bankAccount.getId(),
                bankAccount.getLegalEntity().getCorporateName(),
                bankAccount.getNumber(),
                bankAccount.getAgency(),
                bankAccount.getBankAccountType().getName(),
                bankAccount.getStatus()
        );
    }
}