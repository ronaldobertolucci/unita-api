package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.dashboard.*;
import io.github.ronaldobertolucci.unita.service.group.GroupDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/groups/{groupId}/dashboard")
@RequiredArgsConstructor
public class GroupDashboardController {

    private final GroupDashboardService groupDashboardService;

    @GetMapping
    public ResponseEntity<GroupDashboardDto> getGroupDashboard(
            @PathVariable Long groupId,
            Authentication authentication) {
        return ResponseEntity.ok(groupDashboardService.getGroupDashboard(groupId, authentication));
    }

    @GetMapping("/summary")
    public ResponseEntity<GroupFinancialSummaryDto> getGroupFinancialSummary(
            @PathVariable Long groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        return ResponseEntity.ok(groupDashboardService.getGroupFinancialSummary(
                groupId, startDate, endDate, authentication));
    }

    @GetMapping("/monthly")
    public ResponseEntity<GroupMonthlyDto> getGroupMonthlyFinancialSummary(
            @PathVariable Long groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        return ResponseEntity.ok(groupDashboardService.getGroupMonthlyFinancialSummary(
                groupId, startDate, endDate, authentication));
    }

    @GetMapping("/issuer-risk")
    public ResponseEntity<GroupIssuerRiskDto> getGroupIssuerRiskSummary(
            @PathVariable Long groupId,
            Authentication authentication) {
        return ResponseEntity.ok(groupDashboardService.getGroupIssuerRiskSummary(groupId, authentication));
    }

    @GetMapping("/indexer-summary")
    public ResponseEntity<GroupIndexerSummaryDto> getGroupIndexerSummary(
            @PathVariable Long groupId,
            Authentication authentication) {
        return ResponseEntity.ok(groupDashboardService.getGroupIndexerSummary(groupId, authentication));
    }

    @GetMapping("/liquidity-summary")
    public ResponseEntity<GroupLiquiditySummaryDto> getGroupLiquiditySummary(
            @PathVariable Long groupId,
            Authentication authentication) {
        return ResponseEntity.ok(groupDashboardService.getGroupLiquiditySummary(groupId, authentication));
    }
}