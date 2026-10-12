package io.github.ronaldobertolucci.unita.dto.user;

import io.github.ronaldobertolucci.unita.model.security.Role;
import io.github.ronaldobertolucci.unita.model.user.User;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

public record UserDto(
        Long id,
        String firstName,
        String lastName,
        String email,
        LocalDate dateOfBirth,
        Boolean enabled,
        Boolean emailVerified,
        Set<String> roles
) {
    public UserDto(User user) {
        this(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getDateOfBirth(),
                user.getEnabled(),
                user.getEmailVerified(),
                user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet())
        );
    }
}
