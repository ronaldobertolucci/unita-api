package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.employer.*;
import io.github.ronaldobertolucci.unita.service.employer.EmployerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EmployerController {

    private final EmployerService employerService;

    // -------------------------------------------------------------------------
    // IndividualEmployer
    // -------------------------------------------------------------------------

    @GetMapping("/employers/individual")
    public ResponseEntity<List<IndividualEmployerDto>> findAllIndividual(Authentication authentication) {
        return ResponseEntity.ok(employerService.findAllIndividual(authentication));
    }

    @GetMapping("/employers/individual/{id}")
    public ResponseEntity<IndividualEmployerDto> findIndividualById(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(employerService.findIndividualById(id, authentication));
    }

    @PostMapping("/employers/individual")
    public ResponseEntity<IndividualEmployerDto> createIndividual(
            @RequestBody @Valid IndividualEmployerCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employerService.createIndividual(dto, authentication));
    }

    @PatchMapping("/employers/individual/{id}")
    public ResponseEntity<IndividualEmployerDto> updateIndividual(
            @PathVariable Long id,
            @RequestBody @Valid IndividualEmployerUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(employerService.updateIndividual(id, dto, authentication));
    }

    @DeleteMapping("/employers/individual/{id}")
    public ResponseEntity<Void> deleteIndividual(
            @PathVariable Long id,
            Authentication authentication) {
        employerService.deleteIndividual(id, authentication);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // LegalEntityEmployer
    // -------------------------------------------------------------------------

    @GetMapping("/employers/legal-entity")
    public ResponseEntity<List<LegalEntityEmployerDto>> findAllLegalEntity(Authentication authentication) {
        return ResponseEntity.ok(employerService.findAllLegalEntity(authentication));
    }

    @GetMapping("/employers/legal-entity/{id}")
    public ResponseEntity<LegalEntityEmployerDto> findLegalEntityById(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(employerService.findLegalEntityById(id, authentication));
    }

    @PostMapping("/employers/legal-entity")
    public ResponseEntity<LegalEntityEmployerDto> createLegalEntity(
            @RequestBody @Valid LegalEntityEmployerCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employerService.createLegalEntity(dto, authentication));
    }

    @PatchMapping("/employers/legal-entity/{id}")
    public ResponseEntity<LegalEntityEmployerDto> updateLegalEntity(
            @PathVariable Long id,
            @RequestBody @Valid LegalEntityEmployerUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(employerService.updateLegalEntity(id, dto, authentication));
    }

    @DeleteMapping("/employers/legal-entity/{id}")
    public ResponseEntity<Void> deleteLegalEntity(
            @PathVariable Long id,
            Authentication authentication) {
        employerService.deleteLegalEntity(id, authentication);
        return ResponseEntity.noContent().build();
    }
}