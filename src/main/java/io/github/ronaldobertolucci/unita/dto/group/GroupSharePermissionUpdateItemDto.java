package io.github.ronaldobertolucci.unita.dto.group;

import io.github.ronaldobertolucci.unita.model.group.ShareType;
import jakarta.validation.constraints.NotNull;

public record GroupSharePermissionUpdateItemDto(
    @NotNull ShareType shareType,
    @NotNull Boolean enabled
) {}