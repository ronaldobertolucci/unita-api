package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityCreateDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityUpdateDto;
import io.github.ronaldobertolucci.unita.service.legal.LegalEntityService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.github.ronaldobertolucci.unita.dto.exception.ErrorResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Private institutions", description = "Registrations belong to the authenticated user. The same CNPJ is allowed for different users. Existing numeric IDs and response fields are preserved.")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid registration or CNPJ already registered by this user", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "404", description = "Registration unavailable (missing or owned by another user)", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
})
@RestController
@RequestMapping("/legal-entities")
@RequiredArgsConstructor
public class LegalEntityController {

    private final LegalEntityService legalEntityService;

    @Operation(summary = "List own institutions, ordered by corporate name")
    @GetMapping
    public ResponseEntity<List<LegalEntityDto>> findAll(Authentication authentication) {
        return ResponseEntity.ok(legalEntityService.findAll(authentication));
    }

    @Operation(summary = "Read an own institution; historical CNPJ text is preserved")
    @GetMapping("/{id}")
    public ResponseEntity<LegalEntityDto> findById(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(legalEntityService.findById(id, authentication));
    }

    @Operation(summary = "Create a private institution", responses = @ApiResponse(responseCode = "201", description = "Institution created"))
    @PostMapping
    public ResponseEntity<LegalEntityDto> create(
            @RequestBody @Valid LegalEntityCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(legalEntityService.create(dto, authentication));
    }

    @Operation(summary = "Replace registration fields of an own institution", description = "cnpj and corporateName are required. Optional omitted fields are cleared; historical invalid CNPJ must be explicitly corrected.")
    @PatchMapping("/{id}")
    public ResponseEntity<LegalEntityDto> update(
            @PathVariable Long id,
            @RequestBody @Valid LegalEntityUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(legalEntityService.update(id, dto, authentication));
    }

    @Operation(summary = "Delete an unused own institution", responses = {
            @ApiResponse(responseCode = "204", description = "Institution deleted", content = @Content),
            @ApiResponse(responseCode = "409", description = "Institution referenced by an asset, legacy custodian, bank, benefit, card or employer", content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {
        legalEntityService.delete(id, authentication);
        return ResponseEntity.noContent().build();
    }
}