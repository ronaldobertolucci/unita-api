package io.github.ronaldobertolucci.unita.dto.group;

import io.github.ronaldobertolucci.unita.dto.user.UserDto;
import io.github.ronaldobertolucci.unita.model.group.Group;

public record GroupDto(
        Long id,
        String name,
        UserDto responsibleUser
) {
    public GroupDto(Group group) {
        this(
                group.getId(),
                group.getName(),
                group.getResponsibleUser() != null ? new UserDto(group.getResponsibleUser()) : null
        );
    }
}