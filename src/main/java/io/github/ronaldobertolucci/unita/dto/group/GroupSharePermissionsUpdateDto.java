package io.github.ronaldobertolucci.unita.dto.group;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GroupSharePermissionsUpdateDto(
    @NotNull @NotEmpty List<@NotNull GroupSharePermissionUpdateItemDto> permissions
) {}