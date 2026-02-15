package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.group.GroupCreateDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupMembershipDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupUpdateResponsibleDto;
import io.github.ronaldobertolucci.unita.dto.security.MessageResponseDto;
import io.github.ronaldobertolucci.unita.service.group.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /**
     * Cria um novo grupo
     * POST /groups
     */
    @PostMapping
    public ResponseEntity<GroupDto> createGroup(
            @RequestBody @Valid GroupCreateDto dto,
            Authentication authentication) {

        GroupDto group = groupService.createGroup(dto, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }

    /**
     * Lista todos os grupos onde o usuário é membro
     * GET /groups/my
     */
    @GetMapping("/my")
    public ResponseEntity<List<GroupDto>> getMyGroups(Authentication authentication) {
        List<GroupDto> groups = groupService.getMyGroups(authentication);
        return ResponseEntity.ok(groups);
    }

    /**
     * Lista grupos onde o usuário é responsável
     * GET /groups/my/responsible
     */
    @GetMapping("/my/responsible")
    public ResponseEntity<List<GroupDto>> getGroupsWhereIAmResponsible(Authentication authentication) {
        List<GroupDto> groups = groupService.getGroupsWhereIAmResponsible(authentication);
        return ResponseEntity.ok(groups);
    }

    /**
     * Busca grupo por ID
     * GET /groups/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<GroupDto> getGroupById(
            @PathVariable Long id,
            Authentication authentication) {

        GroupDto group = groupService.getGroupById(id, authentication);
        return ResponseEntity.ok(group);
    }

    /**
     * Atualiza nome do grupo
     * PUT /groups/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<GroupDto> updateGroupName(
            @PathVariable Long id,
            @RequestBody @Valid GroupCreateDto dto,
            Authentication authentication) {

        GroupDto group = groupService.updateGroupName(id, dto, authentication);
        return ResponseEntity.ok(group);
    }

    /**
     * Transfere responsabilidade do grupo
     * PUT /groups/{id}/transfer
     */
    @PutMapping("/{id}/transfer")
    public ResponseEntity<GroupDto> transferResponsibility(
            @PathVariable Long id,
            @RequestBody @Valid GroupUpdateResponsibleDto dto,
            Authentication authentication) {

        GroupDto group = groupService.transferResponsibility(id, dto, authentication);
        return ResponseEntity.ok(group);
    }

    /**
     * Deleta um grupo
     * DELETE /groups/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponseDto> deleteGroup(
            @PathVariable Long id,
            Authentication authentication) {

        groupService.deleteGroup(id, authentication);
        return ResponseEntity.ok(new MessageResponseDto("Group deleted successfully"));
    }

    /**
     * Sai de um grupo
     * DELETE /groups/{id}/leave
     */
    @DeleteMapping("/{id}/leave")
    public ResponseEntity<MessageResponseDto> leaveGroup(
            @PathVariable Long id,
            Authentication authentication) {

        groupService.leaveGroup(id, authentication);
        return ResponseEntity.ok(new MessageResponseDto("You have left the group"));
    }

    /**
     * Lista membros do grupo
     * GET /groups/{id}/members
     */
    @GetMapping("/{id}/members")
    public ResponseEntity<List<GroupMembershipDto>> getGroupMembers(
            @PathVariable Long id,
            Authentication authentication) {

        List<GroupMembershipDto> members = groupService.getGroupMembers(id, authentication);
        return ResponseEntity.ok(members);
    }

    /**
     * Conta membros do grupo
     * GET /groups/{id}/members/count
     */
    @GetMapping("/{id}/members/count")
    public ResponseEntity<Long> getGroupMemberCount(
            @PathVariable Long id,
            Authentication authentication) {

        Long count = groupService.getGroupMemberCount(id, authentication);
        return ResponseEntity.ok(count);
    }
}