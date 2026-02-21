package io.github.ronaldobertolucci.unita.dto.admin;

import io.github.ronaldobertolucci.unita.model.finance.BankAccountType;

public record BankAccountTypeDto(
        Long id,
        String name
) {
    public BankAccountTypeDto(BankAccountType bankAccountType) {
        this(bankAccountType.getId(), bankAccountType.getName());
    }
}