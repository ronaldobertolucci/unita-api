package io.github.ronaldobertolucci.unita.dto.investment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssetUpdateDto(
    @NotBlank @Size(max = 255) String name,
    @NotNull Long legalEntityId,
    Long custodianLegalEntityId
) {}