package io.github.ronaldobertolucci.unita.dto.dashboard;

import io.github.ronaldobertolucci.unita.model.user.User;

public record GroupMemberUserDto(Long id, String firstName, String lastName, String email) {
    public static GroupMemberUserDto from(User user) {
        return new GroupMemberUserDto(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail());
    }
}