package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.investment.*;
import io.github.ronaldobertolucci.unita.service.investment.AssetService;
import io.github.ronaldobertolucci.unita.service.investment.InvestmentTransactionService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "Read a legacy investment asset",
            description = "Legacy contract: numeric IDs and decimals, a single position, and original status and valuation date.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Owned asset with legacy position and category details"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "404", description = "Resource unavailable to the authenticated user")
            })
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

    @Operation(summary = "Estimate legacy redemption tax",
            description = "Read-only estimate using purchaseDate and today's date in America/Sao_Paulo. "
                    + "Fixed income and VGBL use non-negative earnings; PGBL/closed pension use gross amount. "
                    + "LIMITED_ESTIMATE and warnings identify missing, conflicting or multiple purchase dates. "
                    + "No IOF or monthly/annual assessment; never replaces taxAmount informed on sell.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Numeric suggestion with taxable base, estimation status and warnings"),
                    @ApiResponse(responseCode = "400", description = "Invalid amount, purchase date or legacy investment base"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "404", description = "Resource unavailable to the authenticated user")
            })
    @GetMapping("/{id}/tax-suggestion")
    public ResponseEntity<TaxSuggestionDto> getTaxSuggestion(
            @PathVariable Long id,
            @Valid @ModelAttribute @ParameterObject TaxSuggestionRequestDto request,
            Authentication authentication) {
        return ResponseEntity.ok(assetService.getTaxSuggestion(id, request.grossAmount(), request.purchaseDate(), authentication));
    }

    // -------------------------------------------------------------------------
    // InvestmentTransaction
    // -------------------------------------------------------------------------

    @Operation(summary = "Read legacy investment history",
            description = "Legacy numeric IDs and amounts. pocketTransactionId may be null. SELL and TAX have no explicit pairing.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Original movement types, dates, notes and cash references"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "404", description = "Resource unavailable to the authenticated user")
            })
    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<InvestmentTransactionDto>> findTransactions(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(investmentTransactionService.findAllByAsset(id, authentication));
    }

    @Operation(summary = "Register a legacy contribution",
            description = "amount is a strictly positive unit price and quantity must be strictly positive; amount multiplied by quantity is debited from the pocket and added to totalInvested.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "BUY movement with numeric amount and cash reference"),
                    @ApiResponse(responseCode = "400", description = "Missing or non-positive amount/quantity, or invalid category"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "404", description = "Resource unavailable to the authenticated user"),
                    @ApiResponse(responseCode = "409", description = "Asset is not active")
            })
    @PostMapping("/{id}/transactions/buy")
    public ResponseEntity<InvestmentTransactionDto> buy(
            @PathVariable Long id,
            @RequestBody @Valid InvestmentBuyDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investmentTransactionService.buy(id, dto, authentication));
    }

    @Operation(summary = "Register a legacy yield withdrawal",
            description = "Requires amount > 0 and amount <= currentValue. Credits amount to the pocket, adds it to redeemedValue and subtracts it from currentValue without changing quantity.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "YIELD movement with numeric amount and cash reference"),
                    @ApiResponse(responseCode = "400", description = "Missing or non-positive amount, or invalid category"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "404", description = "Resource unavailable to the authenticated user"),
                    @ApiResponse(responseCode = "409", description = "Asset is not active or amount exceeds currentValue")
            })
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
