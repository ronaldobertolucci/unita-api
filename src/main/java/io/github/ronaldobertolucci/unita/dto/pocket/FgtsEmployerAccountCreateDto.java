package io.github.ronaldobertolucci.unita.dto.pocket;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FgtsEmployerAccountCreateDto(
        @NotNull Long employerId,
        @NotNull LocalDate admissionDate,
        LocalDate dismissalDate
) {
}