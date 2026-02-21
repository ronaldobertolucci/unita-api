package io.github.ronaldobertolucci.unita.dto.pocket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BankAccountCreateDto(
        @NotNull Long legalEntityId,
        @NotBlank @Size(max = 20) String number,
        @NotBlank @Size(max = 10) String agency,
        @NotNull Long bankAccountTypeId
) {
}