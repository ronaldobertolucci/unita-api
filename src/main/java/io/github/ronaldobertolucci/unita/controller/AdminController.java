package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.admin.*;
import io.github.ronaldobertolucci.unita.service.admin.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/bank-account-types")
    public ResponseEntity<List<BankAccountTypeDto>> findAllBankAccountTypes() {
        return ResponseEntity.ok(adminService.findAllBankAccountTypes());
    }

    @GetMapping("/benefit-types")
    public ResponseEntity<List<BenefitTypeDto>> findAllBenefitTypes() {
        return ResponseEntity.ok(adminService.findAllBenefitTypes());
    }

    @GetMapping("/card-brands")
    public ResponseEntity<List<CardBrandDto>> findAllCardBrands() {
        return ResponseEntity.ok(adminService.findAllCardBrands());
    }

    @GetMapping("/recurrence-periodicities")
    public ResponseEntity<List<RecurrencePeriodicityDto>> findAllRecurrencePeriodicities() {
        return ResponseEntity.ok(adminService.findAllRecurrencePeriodicities());
    }
}