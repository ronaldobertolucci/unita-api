package io.github.ronaldobertolucci.unita.service.group;

import io.github.ronaldobertolucci.unita.dto.group.*;
import io.github.ronaldobertolucci.unita.model.group.GroupMembership;
import io.github.ronaldobertolucci.unita.model.group.GroupSharePermission;
import io.github.ronaldobertolucci.unita.model.group.ShareType;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupShareService {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final GroupSharePermissionRepository groupSharePermissionRepository;
    private final PocketRepository pocketRepository;

    public List<GroupSharePermissionDto> getPermissions(Long groupId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateGroupExists(groupId);
        validateMembership(currentUser.getId(), groupId);

        return groupSharePermissionRepository
                .findAllByGroupIdAndUserId(groupId, currentUser.getId())
                .stream().map(GroupSharePermissionDto::new)
                .toList();
    }

    @Transactional
    public List<GroupSharePermissionDto> updatePermissions(Long groupId,
                                                           GroupSharePermissionsUpdateDto dto,
                                                           Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateGroupExists(groupId);
        validateMembership(currentUser.getId(), groupId);

        Map<ShareType, GroupSharePermission> existing = groupSharePermissionRepository
                .findAllByGroupIdAndUserId(groupId, currentUser.getId())
                .stream()
                .collect(Collectors.toMap(GroupSharePermission::getShareType, Function.identity()));

        List<GroupSharePermission> toSave = new ArrayList<>();

        for (GroupSharePermissionUpdateItemDto item : dto.permissions()) {
            GroupSharePermission permission = existing.getOrDefault(
                item.shareType(),
                GroupSharePermission.builder()
                    .group(groupRepository.getReferenceById(groupId))
                    .user(currentUser)
                    .shareType(item.shareType())
                    .build()
            );
            permission.setEnabled(item.enabled());
            toSave.add(permission);
        }

        return groupSharePermissionRepository.saveAll(toSave)
                .stream()
                .map(GroupSharePermissionDto::new)
                .toList();
    }

    // --- private helpers ---

    private void validateGroupExists(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new EntityNotFoundException("Group not found with id: " + groupId);
        }
    }

    private void validateMembership(Long userId, Long groupId) {
        if (!groupMembershipRepository.existsByUserIdAndGroupId(userId, groupId)) {
            throw new IllegalArgumentException("User is not a member of this group");
        }
    }

    public List<GroupMemberPocketDto> getPockets(Long groupId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();

        if (!groupRepository.existsById(groupId)) {
            throw new EntityNotFoundException("Group not found");
        }

        if (!groupMembershipRepository.existsByUserIdAndGroupId(user.getId(), groupId)) {
            throw new IllegalArgumentException("User is not a member of this group");
        }

        List<GroupMembership> members = groupMembershipRepository.findByGroupIdWithUsers(groupId);

        return members.stream()
                .flatMap(member -> pocketRepository.findAllByUserId(member.getUser().getId())
                        .stream()
                        .map(pocket -> GroupMemberPocketDto.from(pocket, member.getUser())))
                .toList();
    }
}