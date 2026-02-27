package io.github.ronaldobertolucci.unita.dto.category;

import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryAdminCreateDto(
    @NotBlank @Size(max = 100) String name,
    @NotNull CategoryType type
) {}