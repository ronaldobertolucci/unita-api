package io.github.ronaldobertolucci.unita.dto.group;

import io.github.ronaldobertolucci.unita.dto.user.UserDto;
import io.github.ronaldobertolucci.unita.model.group.GroupInvitation;
import io.github.ronaldobertolucci.unita.model.group.InvitationStatus;

import java.time.LocalDateTime;

public record GroupInvitationDto(
        Long id,
        GroupDto group,
        UserDto invitedUser,
        UserDto invitingUser,
        InvitationStatus status,
        LocalDateTime invitedAt,
        LocalDateTime respondedAt
) {
    public GroupInvitationDto(GroupInvitation invitation) {
        this(
                invitation.getId(),
                new GroupDto(invitation.getGroup()),
                new UserDto(invitation.getInvitedUser()),
                new UserDto(invitation.getInvitingUser()),
                invitation.getStatus(),
                invitation.getInvitedAt(),
                invitation.getRespondedAt()
        );
    }
}