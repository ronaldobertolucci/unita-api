package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.group.GroupInvitationCreateDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupInvitationDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupInvitationResponseDto;
import io.github.ronaldobertolucci.unita.dto.security.MessageResponseDto;
import io.github.ronaldobertolucci.unita.service.group.GroupInvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/invitations")
@RequiredArgsConstructor
public class GroupInvitationController {

    private final GroupInvitationService invitationService;

    /**
     * Cria um novo convite
     * POST /invitations
     */
    @PostMapping
    public ResponseEntity<GroupInvitationDto> createInvitation(
            @RequestBody @Valid GroupInvitationCreateDto dto,
            Authentication authentication) {

        GroupInvitationDto invitation = invitationService.createInvitation(dto, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(invitation);
    }

    /**
     * Lista convites pendentes do usuário
     * GET /invitations/my/pending
     */
    @GetMapping("/my/pending")
    public ResponseEntity<List<GroupInvitationDto>> getMyPendingInvitations(Authentication authentication) {
        List<GroupInvitationDto> invitations = invitationService.getMyPendingInvitations(authentication);
        return ResponseEntity.ok(invitations);
    }

    /**
     * Conta convites pendentes do usuário
     * GET /invitations/my/pending/count
     */
    @GetMapping("/my/pending/count")
    public ResponseEntity<Long> getMyPendingInvitationsCount(Authentication authentication) {
        Long count = invitationService.getMyPendingInvitationsCount(authentication);
        return ResponseEntity.ok(count);
    }

    /**
     * Lista todos os convites de um grupo (somente responsável)
     * GET /invitations/group/{groupId}
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<GroupInvitationDto>> getGroupInvitations(
            @PathVariable Long groupId,
            Authentication authentication) {

        List<GroupInvitationDto> invitations = invitationService.getGroupInvitations(groupId, authentication);
        return ResponseEntity.ok(invitations);
    }

    /**
     * Responde a um convite (aceitar ou rejeitar)
     * PUT /invitations/{id}/respond
     */
    @PutMapping("/{id}/respond")
    public ResponseEntity<GroupInvitationDto> respondToInvitation(
            @PathVariable Long id,
            @RequestBody @Valid GroupInvitationResponseDto dto,
            Authentication authentication) {

        GroupInvitationDto invitation = invitationService.respondToInvitation(id, dto, authentication);
        return ResponseEntity.ok(invitation);
    }

    /**
     * Cancela um convite
     * DELETE /invitations/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponseDto> cancelInvitation(
            @PathVariable Long id,
            Authentication authentication) {

        invitationService.cancelInvitation(id, authentication);
        return ResponseEntity.ok(new MessageResponseDto("Invitation cancelled successfully"));
    }
}