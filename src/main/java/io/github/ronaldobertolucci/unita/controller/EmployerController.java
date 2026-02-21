package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.employer.EmployerDto;
import io.github.ronaldobertolucci.unita.dto.employer.IndividualEmployerCreateDto;
import io.github.ronaldobertolucci.unita.dto.employer.LegalEntityEmployerCreateDto;
import io.github.ronaldobertolucci.unita.service.employer.EmployerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/employers")
@RequiredArgsConstructor
public class EmployerController {

    private final EmployerService employerService;

    @PostMapping("/individual")
    public ResponseEntity<EmployerDto> createIndividual(
            @RequestBody @Valid IndividualEmployerCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employerService.createIndividual(dto));
    }

    @PostMapping("/legal-entity")
    public ResponseEntity<EmployerDto> createLegalEntity(
            @RequestBody @Valid LegalEntityEmployerCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employerService.createLegalEntity(dto));
    }

    @GetMapping
    public ResponseEntity<List<EmployerDto>> findAll() {
        return ResponseEntity.ok(employerService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployerDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(employerService.findById(id));
    }
}