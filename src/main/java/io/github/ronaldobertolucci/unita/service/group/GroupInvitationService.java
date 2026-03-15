package io.github.ronaldobertolucci.unita.service.group;

import io.github.ronaldobertolucci.unita.dto.group.GroupInvitationCreateDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupInvitationDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupInvitationResponseDto;
import io.github.ronaldobertolucci.unita.model.group.Group;
import io.github.ronaldobertolucci.unita.model.group.GroupInvitation;
import io.github.ronaldobertolucci.unita.model.group.GroupMembership;
import io.github.ronaldobertolucci.unita.model.group.InvitationStatus;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.GroupInvitationRepository;
import io.github.ronaldobertolucci.unita.repository.GroupMembershipRepository;
import io.github.ronaldobertolucci.unita.repository.GroupRepository;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupInvitationService {

    private final GroupInvitationRepository invitationRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMembershipRepository membershipRepository;

    @Transactional
    public GroupInvitationDto createInvitation(GroupInvitationCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        // Verifica se o grupo existe
        Group group = groupRepository.findById(dto.groupId())
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        // Verifica se o usuário atual é membro do grupo
        if (!membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), dto.groupId())) {
            throw new IllegalArgumentException("You must be a member of the group to invite others");
        }

        // Verifica se o usuário convidado existe
        User invitedUser = userRepository.findByEmailWithRoles(dto.invitedUserEmail())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Verifica se o usuário convidado já é membro
        if (membershipRepository.existsByUserIdAndGroupId(invitedUser.getId(), dto.groupId())) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        // Verifica se já existe convite pendente
        if (invitationRepository.existsByGroupIdAndInvitedUserIdAndStatus(
                dto.groupId(), invitedUser.getId(), InvitationStatus.PENDING)) {
            throw new IllegalArgumentException("User already has a pending invitation to this group");
        }

        // Cria o convite
        GroupInvitation invitation = GroupInvitation.builder()
                .group(group)
                .invitedUser(invitedUser)
                .invitingUser(currentUser)
                .status(InvitationStatus.PENDING)
                .build();

        invitation = invitationRepository.save(invitation);

        return new GroupInvitationDto(invitation);
    }

    public List<GroupInvitationDto> getMyPendingInvitations(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        List<GroupInvitation> invitations = invitationRepository
                .findPendingInvitationsByInvitedUserId(currentUser.getId());

        return invitations.stream()
                .map(GroupInvitationDto::new)
                .collect(Collectors.toList());
    }

    public Long getMyPendingInvitationsCount(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        return invitationRepository.countByInvitedUserIdAndStatus(
                currentUser.getId(),
                InvitationStatus.PENDING
        );
    }

    public List<GroupInvitationDto> getGroupInvitations(Long groupId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        // Verifica se o usuário é o responsável do grupo
        if (!group.getResponsibleUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Only the group owner can view all invitations");
        }

        List<GroupInvitation> invitations = invitationRepository.findByGroupIdWithUsers(groupId);

        return invitations.stream()
                .map(GroupInvitationDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public GroupInvitationDto respondToInvitation(Long invitationId, GroupInvitationResponseDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        GroupInvitation invitation = invitationRepository.findByIdWithRelations(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));

        // Verifica se o convite é para o usuário atual
        if (!invitation.getInvitedUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("This invitation is not for you");
        }

        // Verifica se o convite ainda está pendente
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("This invitation has already been responded to");
        }

        // Processa a resposta
        if (dto.accept()) {
            // Aceita o convite
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitation.setRespondedAt(LocalDateTime.now());

            // Adiciona o usuário como membro do grupo
            GroupMembership membership = GroupMembership.builder()
                    .user(currentUser)
                    .group(invitation.getGroup())
                    .build();

            membershipRepository.save(membership);
        } else {
            // Rejeita o convite
            invitation.setStatus(InvitationStatus.REJECTED);
            invitation.setRespondedAt(LocalDateTime.now());
        }

        invitationRepository.save(invitation);

        return new GroupInvitationDto(invitation);
    }

    @Transactional
    public void cancelInvitation(Long invitationId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        GroupInvitation invitation = invitationRepository.findByIdWithRelations(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));

        // Verifica se o convite ainda está pendente
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Only pending invitations can be cancelled");
        }

        // Verifica se o usuário atual é quem enviou o convite ou é o responsável do grupo
        boolean isInviter = invitation.getInvitingUser().getId().equals(currentUser.getId());
        boolean isGroupOwner = invitation.getGroup().getResponsibleUser().getId().equals(currentUser.getId());

        if (!isInviter && !isGroupOwner) {
            throw new IllegalArgumentException("You can only cancel invitations you sent or if you are the group owner");
        }

        invitationRepository.delete(invitation);
    }
}