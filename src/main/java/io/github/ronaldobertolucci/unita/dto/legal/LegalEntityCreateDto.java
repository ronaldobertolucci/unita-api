package io.github.ronaldobertolucci.unita.dto.legal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LegalEntityCreateDto(
        @NotBlank @Size(min = 14, max = 14) String cnpj,
        @NotBlank @Size(max = 255) String corporateName,
        @Size(max = 255) String tradeName,
        @Size(max = 50) String stateRegistration
) {
}