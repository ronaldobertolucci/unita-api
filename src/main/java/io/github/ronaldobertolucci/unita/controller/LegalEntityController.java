package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityCreateDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityUpdateDto;
import io.github.ronaldobertolucci.unita.service.legal.LegalEntityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/legal-entities")
@RequiredArgsConstructor
public class LegalEntityController {

    private final LegalEntityService legalEntityService;

    @GetMapping
    public ResponseEntity<List<LegalEntityDto>> findAll(Authentication authentication) {
        return ResponseEntity.ok(legalEntityService.findAll(authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LegalEntityDto> findById(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(legalEntityService.findById(id, authentication));
    }

    @PostMapping
    public ResponseEntity<LegalEntityDto> create(
            @RequestBody @Valid LegalEntityCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(legalEntityService.create(dto, authentication));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<LegalEntityDto> update(
            @PathVariable Long id,
            @RequestBody @Valid LegalEntityUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(legalEntityService.update(id, dto, authentication));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {
        legalEntityService.delete(id, authentication);
        return ResponseEntity.noContent().build();
    }
}