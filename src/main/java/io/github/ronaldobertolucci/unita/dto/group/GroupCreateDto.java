package io.github.ronaldobertolucci.unita.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupCreateDto(

        @NotBlank(message = "Group name is required")
        @Size(min = 3, max = 100, message = "Group name must be between 3 and 100 characters")
        String name
) {
}