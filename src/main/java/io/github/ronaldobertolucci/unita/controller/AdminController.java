package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.admin.*;
import io.github.ronaldobertolucci.unita.dto.category.CategoryAdminCreateDto;
import io.github.ronaldobertolucci.unita.dto.category.CategoryDto;
import io.github.ronaldobertolucci.unita.dto.category.CategoryUpdateDto;
import io.github.ronaldobertolucci.unita.service.admin.AdminService;
import io.github.ronaldobertolucci.unita.service.category.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final CategoryService categoryService;

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

    @PostMapping("/categories")
    public ResponseEntity<CategoryDto> createGlobalCategory(
            @RequestBody @Valid CategoryAdminCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createGlobalCategory(dto));
    }

    @PatchMapping("/categories/{id}")
    public ResponseEntity<CategoryDto> updateGlobalCategory(
            @PathVariable Long id,
            @RequestBody @Valid CategoryUpdateDto dto) {
        return ResponseEntity.ok(categoryService.updateGlobalCategory(id, dto));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteGlobalCategory(@PathVariable Long id) {
        categoryService.deleteGlobalCategory(id);
        return ResponseEntity.noContent().build();
    }

}