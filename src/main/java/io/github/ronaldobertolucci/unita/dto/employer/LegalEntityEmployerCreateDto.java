package io.github.ronaldobertolucci.unita.dto.employer;

import jakarta.validation.constraints.NotNull;

public record LegalEntityEmployerCreateDto(
        @NotNull Long legalEntityId
) {
}