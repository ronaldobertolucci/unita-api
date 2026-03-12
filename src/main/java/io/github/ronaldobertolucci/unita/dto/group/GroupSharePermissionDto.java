package io.github.ronaldobertolucci.unita.dto.group;

import io.github.ronaldobertolucci.unita.model.group.GroupSharePermission;
import io.github.ronaldobertolucci.unita.model.group.ShareType;

public record GroupSharePermissionDto(
    ShareType shareType,
    boolean enabled
) {
    public GroupSharePermissionDto(GroupSharePermission permission) {
        this(permission.getShareType(), permission.isEnabled());
    }
}