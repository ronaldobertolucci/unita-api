package io.github.ronaldobertolucci.unita.dto.security;

import io.github.ronaldobertolucci.unita.dto.user.UserDto;

public record TokenDto(
        String token,
        String type,
        Long expiresIn,
        UserDto user
) {
    public TokenDto(String token, Long expiresInSeconds, UserDto user) {
        this(token, "Bearer", expiresInSeconds, user);
    }
}