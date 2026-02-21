package io.github.ronaldobertolucci.unita.dto.employer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IndividualEmployerCreateDto(
        @NotBlank @Size(min = 11, max = 11) String cpf,
        @NotBlank @Size(max = 255) String name
) {
}