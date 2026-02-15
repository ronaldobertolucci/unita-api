package io.github.ronaldobertolucci.unita.service.group;

import io.github.ronaldobertolucci.unita.dto.group.GroupCreateDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupMembershipDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupUpdateResponsibleDto;
import io.github.ronaldobertolucci.unita.model.group.Group;
import io.github.ronaldobertolucci.unita.model.group.GroupMembership;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.GroupMembershipRepository;
import io.github.ronaldobertolucci.unita.repository.GroupRepository;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional
    public GroupDto createGroup(GroupCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        // Verifica se já existe grupo com mesmo nome para este usuário
        if (groupRepository.existsByNameAndResponsibleUserId(dto.name(), currentUser.getId())) {
            throw new IllegalArgumentException("You already have a group with this name");
        }

        // Cria o grupo
        Group group = Group.builder()
                .name(dto.name())
                .responsibleUser(currentUser)
                .build();

        group = groupRepository.save(group);

        // Adiciona o criador como membro
        GroupMembership membership = GroupMembership.builder()
                .user(currentUser)
                .group(group)
                .build();

        membershipRepository.save(membership);

        return new GroupDto(group);
    }

    public List<GroupDto> getMyGroups(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        List<Group> groups = groupRepository.findGroupsByMemberUserId(currentUser.getId());

        return groups.stream()
                .map(GroupDto::new)
                .collect(Collectors.toList());
    }

    public List<GroupDto> getGroupsWhereIAmResponsible(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        List<Group> groups = groupRepository.findByResponsibleUserId(currentUser.getId());

        return groups.stream()
                .map(GroupDto::new)
                .collect(Collectors.toList());
    }

    public GroupDto getGroupById(Long groupId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Group group = groupRepository.findByIdWithResponsible(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        // Verifica se o usuário é membro do grupo
        if (!membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), groupId)) {
            throw new IllegalArgumentException("You are not a member of this group");
        }

        return new GroupDto(group);
    }

    @Transactional
    public GroupDto updateGroupName(Long groupId, GroupCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        // Verifica se o usuário é o responsável
        if (!group.getResponsibleUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Only the group owner can update the group name");
        }

        // Verifica se já existe outro grupo com esse nome para o usuário
        if (groupRepository.existsByNameAndResponsibleUserId(dto.name(), currentUser.getId())
                && !group.getName().equals(dto.name())) {
            throw new IllegalArgumentException("You already have a group with this name");
        }

        group.setName(dto.name());
        groupRepository.save(group);

        return new GroupDto(group);
    }

    @Transactional
    public GroupDto transferResponsibility(Long groupId, GroupUpdateResponsibleDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        // Verifica se o usuário é o responsável atual
        if (!group.getResponsibleUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Only the current group owner can transfer responsibility");
        }

        // Verifica se o novo responsável é membro do grupo
        if (!membershipRepository.existsByUserIdAndGroupId(dto.newResponsibleUserId(), groupId)) {
            throw new IllegalArgumentException("New responsible user must be a member of the group");
        }

        User newResponsible = userRepository.findById(dto.newResponsibleUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        group.setResponsibleUser(newResponsible);
        groupRepository.save(group);

        return new GroupDto(group);
    }

    @Transactional
    public void deleteGroup(Long groupId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        // Verifica se o usuário é o responsável
        if (!group.getResponsibleUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Only the group owner can delete the group");
        }

        groupRepository.delete(group);
    }

    @Transactional
    public void leaveGroup(Long groupId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        // Verifica se o usuário é membro
        if (!membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), groupId)) {
            throw new IllegalArgumentException("You are not a member of this group");
        }

        // Verifica se o usuário é o responsável
        if (group.getResponsibleUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Group owner cannot leave. Transfer responsibility or delete the group.");
        }

        membershipRepository.deleteByUserIdAndGroupId(currentUser.getId(), groupId);
    }

    public List<GroupMembershipDto> getGroupMembers(Long groupId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        // Verifica se o usuário é membro do grupo
        if (!membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), groupId)) {
            throw new IllegalArgumentException("You are not a member of this group");
        }

        List<GroupMembership> memberships = membershipRepository.findByGroupIdWithUsers(groupId);

        return memberships.stream()
                .map(GroupMembershipDto::new)
                .collect(Collectors.toList());
    }

    public Long getGroupMemberCount(Long groupId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        // Verifica se o usuário é membro do grupo
        if (!membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), groupId)) {
            throw new IllegalArgumentException("You are not a member of this group");
        }

        return membershipRepository.countByGroupId(groupId);
    }
}