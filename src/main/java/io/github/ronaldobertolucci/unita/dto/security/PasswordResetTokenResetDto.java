package io.github.ronaldobertolucci.unita.dto.security;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetTokenResetDto(
        @NotBlank
        String token,
        @NotBlank
        String newPassword
) {
}
