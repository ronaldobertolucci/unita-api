// GroupInvitationResponseDto.java
package io.github.ronaldobertolucci.unita.dto.group;

import jakarta.validation.constraints.NotNull;

public record GroupInvitationResponseDto(

        @NotNull(message = "Accept status is required")
        Boolean accept
) {
}