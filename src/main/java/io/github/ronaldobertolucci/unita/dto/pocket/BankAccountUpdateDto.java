package io.github.ronaldobertolucci.unita.dto.pocket;

import io.github.ronaldobertolucci.unita.model.pocket.BankAccountStatus;
import jakarta.validation.constraints.NotNull;

public record BankAccountUpdateDto(
        @NotNull BankAccountStatus status
) {
}