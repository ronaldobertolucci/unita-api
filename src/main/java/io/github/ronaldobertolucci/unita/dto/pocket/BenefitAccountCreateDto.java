package io.github.ronaldobertolucci.unita.dto.pocket;

import jakarta.validation.constraints.NotNull;

public record BenefitAccountCreateDto(
        @NotNull Long legalEntityId,
        @NotNull Long benefitTypeId
) {
}