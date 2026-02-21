package io.github.ronaldobertolucci.unita.dto.pocket;

import io.github.ronaldobertolucci.unita.model.pocket.BenefitAccountStatus;
import jakarta.validation.constraints.NotNull;

public record BenefitAccountUpdateDto(
        @NotNull BenefitAccountStatus status
) {
}