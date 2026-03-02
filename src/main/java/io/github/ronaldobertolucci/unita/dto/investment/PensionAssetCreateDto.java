package io.github.ronaldobertolucci.unita.dto.investment;

import io.github.ronaldobertolucci.unita.model.investment.PensionType;
import io.github.ronaldobertolucci.unita.model.investment.TaxRegime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PensionAssetCreateDto(
    @NotBlank @Size(max = 255) String name,
    @NotNull Long legalEntityId,
    @NotNull PensionType pensionType,
    @NotNull TaxRegime taxRegime
) {}