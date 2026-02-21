package io.github.ronaldobertolucci.unita.dto.pocket;

import io.github.ronaldobertolucci.unita.model.pocket.FgtsEmployerAccountStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FgtsEmployerAccountUpdateDto(
        @NotNull FgtsEmployerAccountStatus status,
        LocalDate dismissalDate
) {
}