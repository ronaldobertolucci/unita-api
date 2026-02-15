package io.github.ronaldobertolucci.unita.dto.group;

import jakarta.validation.constraints.NotNull;

public record GroupUpdateResponsibleDto(

        @NotNull(message = "New responsible user ID is required")
        Long newResponsibleUserId
) {
}