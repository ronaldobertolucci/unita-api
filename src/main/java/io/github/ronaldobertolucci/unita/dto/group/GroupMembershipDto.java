package io.github.ronaldobertolucci.unita.dto.group;

import io.github.ronaldobertolucci.unita.dto.user.UserDto;
import io.github.ronaldobertolucci.unita.model.group.GroupMembership;

import java.time.LocalDateTime;

public record GroupMembershipDto(
        Long id,
        UserDto user,
        GroupDto group,
        LocalDateTime joinedAt
) {
    public GroupMembershipDto(GroupMembership membership) {
        this(
                membership.getId(),
                new UserDto(membership.getUser()),
                new GroupDto(membership.getGroup()),
                membership.getJoinedAt()
        );
    }
}