package io.github.ronaldobertolucci.unita.dto.group;

import io.github.ronaldobertolucci.unita.model.pocket.Pocket;
import io.github.ronaldobertolucci.unita.model.user.User;

public record GroupMemberPocketDto(
        Long id,
        String type,
        String label,
        GroupMemberPocketUserDto user
) {
    public static GroupMemberPocketDto from(Pocket pocket, User user) {
        return new GroupMemberPocketDto(
                pocket.getId(),
                pocket.getClass().getSimpleName(),
                pocket.getLabel(),
                GroupMemberPocketUserDto.from(user)
        );
    }
}