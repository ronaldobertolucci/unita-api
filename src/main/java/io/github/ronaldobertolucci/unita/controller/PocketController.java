package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.pocket.*;
import io.github.ronaldobertolucci.unita.service.pocket.PocketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/pockets")
@RequiredArgsConstructor
public class PocketController {

    private final PocketService pocketService;

    // -------------------------------------------------------------------------
    // Pocket (geral)
    // -------------------------------------------------------------------------

    @GetMapping("/my")
    public ResponseEntity<List<PocketSummaryDto>> findMyPockets(Authentication authentication) {
        return ResponseEntity.ok(pocketService.findMyPockets(authentication));
    }

    // -------------------------------------------------------------------------
    // BankAccount
    // -------------------------------------------------------------------------

    @PostMapping("/bank-accounts")
    public ResponseEntity<BankAccountDto> createBankAccount(
            @RequestBody @Valid BankAccountCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pocketService.createBankAccount(dto, authentication));
    }

    @GetMapping("/bank-accounts/{id}")
    public ResponseEntity<BankAccountDto> findBankAccountById(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(pocketService.findBankAccountById(id, authentication));
    }

    @PutMapping("/bank-accounts/{id}")
    public ResponseEntity<BankAccountDto> updateBankAccount(
            @PathVariable Long id,
            @RequestBody @Valid BankAccountUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(pocketService.updateBankAccount(id, dto, authentication));
    }

    @DeleteMapping("/bank-accounts/{id}")
    public ResponseEntity<Void> deleteBankAccount(
            @PathVariable Long id,
            Authentication authentication) {
        pocketService.deleteBankAccount(id, authentication);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // BenefitAccount
    // -------------------------------------------------------------------------

    @PostMapping("/benefit-accounts")
    public ResponseEntity<BenefitAccountDto> createBenefitAccount(
            @RequestBody @Valid BenefitAccountCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pocketService.createBenefitAccount(dto, authentication));
    }

    @GetMapping("/benefit-accounts/{id}")
    public ResponseEntity<BenefitAccountDto> findBenefitAccountById(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(pocketService.findBenefitAccountById(id, authentication));
    }

    @PutMapping("/benefit-accounts/{id}")
    public ResponseEntity<BenefitAccountDto> updateBenefitAccount(
            @PathVariable Long id,
            @RequestBody @Valid BenefitAccountUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(pocketService.updateBenefitAccount(id, dto, authentication));
    }

    @DeleteMapping("/benefit-accounts/{id}")
    public ResponseEntity<Void> deleteBenefitAccount(
            @PathVariable Long id,
            Authentication authentication) {
        pocketService.deleteBenefitAccount(id, authentication);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // FgtsEmployerAccount
    // -------------------------------------------------------------------------

    @PostMapping("/fgts")
    public ResponseEntity<FgtsEmployerAccountDto> createFgtsEmployerAccount(
            @RequestBody @Valid FgtsEmployerAccountCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pocketService.createFgtsEmployerAccount(dto, authentication));
    }

    @GetMapping("/fgts/{id}")
    public ResponseEntity<FgtsEmployerAccountDto> findFgtsEmployerAccountById(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(pocketService.findFgtsEmployerAccountById(id, authentication));
    }

    @PutMapping("/fgts/{id}")
    public ResponseEntity<FgtsEmployerAccountDto> updateFgtsEmployerAccount(
            @PathVariable Long id,
            @RequestBody @Valid FgtsEmployerAccountUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(pocketService.updateFgtsEmployerAccount(id, dto, authentication));
    }

    @DeleteMapping("/fgts/{id}")
    public ResponseEntity<Void> deleteFgtsEmployerAccount(
            @PathVariable Long id,
            Authentication authentication) {
        pocketService.deleteFgtsEmployerAccount(id, authentication);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Cash
    // -------------------------------------------------------------------------

    @PostMapping("/cash")
    public ResponseEntity<CashDto> createCash(Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pocketService.createCash(authentication));
    }

    @GetMapping("/cash")
    public ResponseEntity<CashDto> findCash(Authentication authentication) {
        return ResponseEntity.ok(pocketService.findCash(authentication));
    }

    // -------------------------------------------------------------------------
    // Transaction
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/transactions")
    public ResponseEntity<TransactionDto> createTransaction(
            @PathVariable Long id,
            @RequestBody @Valid TransactionCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pocketService.createTransaction(id, dto, authentication));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<TransactionDto>> findTransactions(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(pocketService.findTransactions(id, authentication));
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BigDecimal> findBalance(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(pocketService.findBalance(id, authentication));
    }

    @DeleteMapping("/{id}/transactions/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable Long id,
            @PathVariable Long transactionId,
            Authentication authentication) {
        pocketService.deleteTransaction(id, transactionId, authentication);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // RecurringTransaction
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/recurring")
    public ResponseEntity<RecurringTransactionDto> createRecurringTransaction(
            @PathVariable Long id,
            @RequestBody @Valid RecurringTransactionCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pocketService.createRecurringTransaction(id, dto, authentication));
    }

    @GetMapping("/{id}/recurring")
    public ResponseEntity<List<RecurringTransactionDto>> findRecurringTransactions(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(pocketService.findRecurringTransactions(id, authentication));
    }

    @DeleteMapping("/{id}/recurring/{recurringId}")
    public ResponseEntity<Void> deleteRecurringTransaction(
            @PathVariable Long id,
            @PathVariable Long recurringId,
            Authentication authentication) {
        pocketService.deleteRecurringTransaction(id, recurringId, authentication);
        return ResponseEntity.noContent().build();
    }
}