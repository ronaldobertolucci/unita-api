package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.group.*;
import io.github.ronaldobertolucci.unita.service.group.GroupShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/groups/{id}/share")
@RequiredArgsConstructor
public class GroupShareController {

    private final GroupShareService groupShareService;

    @GetMapping("/permissions")
    public ResponseEntity<List<GroupSharePermissionDto>> getPermissions(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(groupShareService.getPermissions(id, authentication));
    }

    @PutMapping("/permissions")
    public ResponseEntity<List<GroupSharePermissionDto>> updatePermissions(
            @PathVariable Long id,
            @RequestBody @Valid GroupSharePermissionsUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(groupShareService.updatePermissions(id, dto, authentication));
    }

    @GetMapping("/pockets")
    public ResponseEntity<List<GroupMemberPocketDto>> getPockets(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(groupShareService.getPockets(id, authentication));
    }
}