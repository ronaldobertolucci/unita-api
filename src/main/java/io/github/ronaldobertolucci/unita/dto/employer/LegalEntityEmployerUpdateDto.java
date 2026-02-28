package io.github.ronaldobertolucci.unita.dto.employer;

import jakarta.validation.constraints.NotNull;

public record LegalEntityEmployerUpdateDto(
    @NotNull Long legalEntityId
) {}