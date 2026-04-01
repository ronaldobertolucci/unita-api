package io.github.ronaldobertolucci.unita.dto.group;

import io.github.ronaldobertolucci.unita.model.user.User;

public record GroupMemberPocketUserDto(
        Long id,
        String firstName,
        String lastName,
        String email
) {
    public static GroupMemberPocketUserDto from(User user) {
        return new GroupMemberPocketUserDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
        );
    }
}