package io.github.ronaldobertolucci.unita.dto.group;

import jakarta.validation.constraints.NotNull;

public record GroupInvitationCreateDto(

        @NotNull(message = "Group ID is required")
        Long groupId,

        @NotNull(message = "Invited user ID is required")
        Long invitedUserId
) {
}