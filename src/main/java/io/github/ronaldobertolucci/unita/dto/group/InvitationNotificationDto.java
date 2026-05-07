package io.github.ronaldobertolucci.unita.dto.group;

import io.github.ronaldobertolucci.unita.model.group.GroupInvitation;

public record InvitationNotificationDto(
        Long invitationId,
        String groupName,
        String invitingUserFirstName,
        String invitingUserLastName
) {
    public InvitationNotificationDto(GroupInvitation invitation) {
        this(
                invitation.getId(),
                invitation.getGroup().getName(),
                invitation.getInvitingUser().getFirstName(),
                invitation.getInvitingUser().getLastName()
        );
    }
}