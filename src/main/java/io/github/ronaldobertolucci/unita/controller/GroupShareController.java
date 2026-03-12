package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.group.*;
import io.github.ronaldobertolucci.unita.dto.investment.AssetDetailDto;
import io.github.ronaldobertolucci.unita.service.group.GroupShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/groups/{id}/share")
@RequiredArgsConstructor
public class GroupShareController {

    private final GroupShareService groupShareService;

    @PutMapping("/permissions")
    public ResponseEntity<List<GroupSharePermissionDto>> updatePermissions(
            @PathVariable Long id,
            @RequestBody @Valid GroupSharePermissionsUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(groupShareService.updatePermissions(id, dto, authentication));
    }

    @GetMapping("/balance")
    public ResponseEntity<List<GroupMemberBalanceDto>> getBalance(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(groupShareService.getBalance(id, authentication));
    }

    @GetMapping("/credit-card-bills")
    public ResponseEntity<List<GroupMemberCreditCardBillsDto>> getCreditCardBills(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(groupShareService.getCreditCardBills(id, authentication));
    }

    @GetMapping("/investments")
    public ResponseEntity<List<GroupMemberInvestmentsDto>> getInvestments(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(groupShareService.getInvestments(id, authentication));
    }

    @GetMapping("/expenses")
    public ResponseEntity<List<GroupMemberCategoryAmountDto>> getExpenses(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        return ResponseEntity.ok(groupShareService.getExpenses(id, startDate, endDate, authentication));
    }

    @GetMapping("/income")
    public ResponseEntity<List<GroupMemberCategoryAmountDto>> getIncome(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        return ResponseEntity.ok(groupShareService.getIncome(id, startDate, endDate, authentication));
    }
}