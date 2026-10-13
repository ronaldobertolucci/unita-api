package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.investment.*;
import io.github.ronaldobertolucci.unita.service.investment.AssetService;
import io.github.ronaldobertolucci.unita.service.investment.InvestmentTransactionService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final InvestmentTransactionService investmentTransactionService;

    // -------------------------------------------------------------------------
    // Asset
    // -------------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<List<AssetSummaryDto>> findAll(Authentication authentication) {
        return ResponseEntity.ok(assetService.findAll(authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetDetailDto> findById(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(assetService.findById(id, authentication));
    }

    @PostMapping("/fixed-income")
    public ResponseEntity<AssetDetailDto> createFixedIncome(
            @RequestBody @Valid FixedIncomeAssetCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assetService.createFixedIncome(dto, authentication));
    }

    @PostMapping("/pension")
    public ResponseEntity<AssetDetailDto> createPension(
            @RequestBody @Valid PensionAssetCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assetService.createPension(dto, authentication));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssetDetailDto> update(
            @PathVariable Long id,
            @RequestBody @Valid AssetUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(assetService.update(id, dto, authentication));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {
        assetService.delete(id, authentication);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Position
    // -------------------------------------------------------------------------

    @PatchMapping("/{id}/position")
    public ResponseEntity<AssetDetailDto> updatePosition(
            @PathVariable Long id,
            @RequestBody @Valid InvestmentPositionUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(assetService.updatePosition(id, dto, authentication));
    }

    // -------------------------------------------------------------------------
    // Tax Suggestion
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/tax-suggestion")
    public ResponseEntity<TaxSuggestionDto> getTaxSuggestion(
            @PathVariable Long id,
            @RequestParam BigDecimal grossAmount,
            @RequestParam LocalDate purchaseDate,
            Authentication authentication) {
        return ResponseEntity.ok(assetService.getTaxSuggestion(id, grossAmount, purchaseDate, authentication));
    }

    // -------------------------------------------------------------------------
    // InvestmentTransaction
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<InvestmentTransactionDto>> findTransactions(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(investmentTransactionService.findAllByAsset(id, authentication));
    }

    @PostMapping("/{id}/transactions/buy")
    public ResponseEntity<InvestmentTransactionDto> buy(
            @PathVariable Long id,
            @RequestBody @Valid InvestmentBuyDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investmentTransactionService.buy(id, dto, authentication));
    }

    @PostMapping("/{id}/transactions/yield")
    public ResponseEntity<InvestmentTransactionDto> yield(
            @PathVariable Long id,
            @RequestBody @Valid InvestmentYieldDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investmentTransactionService.yield(id, dto, authentication));
    }

    @Operation(
            summary = "Redeem the entire investment position",
            description = "Requires grossAmount > 0 and 0 <= taxAmount <= grossAmount. "
                    + "Tax is supplied by the client. Zero net amount is permitted.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Full redemption registered with SELL and TAX transactions"),
                    @ApiResponse(responseCode = "400", description = "Missing or invalid input amounts"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "404", description = "Resource unavailable to the authenticated user"),
                    @ApiResponse(responseCode = "409", description = "Asset already fully redeemed")
            })
    @PostMapping("/{id}/transactions/sell")
    public ResponseEntity<List<InvestmentTransactionDto>> sell(
            @PathVariable Long id,
            @RequestBody @Valid InvestmentSellDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investmentTransactionService.sell(id, dto, authentication));
    }
}