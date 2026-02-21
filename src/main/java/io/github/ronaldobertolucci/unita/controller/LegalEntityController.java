package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityCreateDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
import io.github.ronaldobertolucci.unita.service.legal.LegalEntityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/legal-entities")
@RequiredArgsConstructor
public class LegalEntityController {

    private final LegalEntityService legalEntityService;

    @PostMapping
    public ResponseEntity<LegalEntityDto> create(
            @RequestBody @Valid LegalEntityCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(legalEntityService.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<LegalEntityDto>> findAll() {
        return ResponseEntity.ok(legalEntityService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LegalEntityDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(legalEntityService.findById(id));
    }
}