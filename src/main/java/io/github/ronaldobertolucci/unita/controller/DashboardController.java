package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.dashboard.*;
import io.github.ronaldobertolucci.unita.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardDto> getDashboard(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getDashboard(authentication));
    }

    @GetMapping("/summary")
    public ResponseEntity<FinancialSummaryDto> getFinancialSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getFinancialSummary(startDate, endDate, authentication));
    }

    @GetMapping("/monthly")
    public ResponseEntity<List<MonthlyFinancialSummaryDto>> getMonthlyFinancialSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getMonthlyFinancialSummary(startDate, endDate, authentication));
    }

    @GetMapping("/issuer-risk")
    public ResponseEntity<List<IssuerRiskSummaryDto>> getIssuerRiskSummary(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getIssuerRiskSummary(authentication));
    }

    @GetMapping("/indexer-summary")
    public ResponseEntity<List<IndexerSummaryDto>> getIndexerSummary(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getIndexerSummary(authentication));
    }
}