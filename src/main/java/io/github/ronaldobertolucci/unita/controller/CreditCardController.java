package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.card.*;
import io.github.ronaldobertolucci.unita.service.card.CreditCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/credit-cards")
@RequiredArgsConstructor
public class CreditCardController {

    private final CreditCardService creditCardService;

    // -------------------------------------------------------------------------
    // CreditCard
    // -------------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<CreditCardDto> createCreditCard(
            @RequestBody @Valid CreditCardCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creditCardService.createCreditCard(dto, authentication));
    }

    @GetMapping("/my")
    public ResponseEntity<List<CreditCardDto>> findMyCreditCards(Authentication authentication) {
        return ResponseEntity.ok(creditCardService.findMyCreditCards(authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreditCardDto> findCreditCardById(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.findCreditCardById(id, authentication));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CreditCardDto> updateCreditCard(
            @PathVariable Long id,
            @RequestBody @Valid CreditCardUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.updateCreditCard(id, dto, authentication));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCreditCard(
            @PathVariable Long id,
            Authentication authentication) {
        creditCardService.deleteCreditCard(id, authentication);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // CreditCardBill
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/bills")
    public ResponseEntity<List<CreditCardBillDto>> findBills(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.findBills(id, authentication));
    }

    @GetMapping("/{id}/bills/{billId}")
    public ResponseEntity<CreditCardBillDto> findBillById(
            @PathVariable Long id,
            @PathVariable Long billId,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.findBillById(id, billId, authentication));
    }

    @PutMapping("/{id}/bills/{billId}/pay")
    public ResponseEntity<CreditCardBillDto> payBill(
            @PathVariable Long id,
            @PathVariable Long billId,
            @RequestBody @Valid CreditCardBillPayDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.payBill(id, billId, dto, authentication));
    }

    // -------------------------------------------------------------------------
    // CreditCardPurchase
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/purchases")
    public ResponseEntity<CreditCardPurchaseDto> createPurchase(
            @PathVariable Long id,
            @RequestBody @Valid CreditCardPurchaseCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creditCardService.createPurchase(id, dto, authentication));
    }

    @GetMapping("/{id}/purchases")
    public ResponseEntity<List<CreditCardPurchaseDto>> findPurchases(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.findPurchases(id, authentication));
    }

    @DeleteMapping("/{id}/purchases/{purchaseId}")
    public ResponseEntity<Void> deletePurchase(
            @PathVariable Long id,
            @PathVariable Long purchaseId,
            Authentication authentication) {
        creditCardService.deletePurchase(id, purchaseId, authentication);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // CreditCardInstallment
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/purchases/{purchaseId}/installments")
    public ResponseEntity<CreditCardInstallmentDto> createInstallment(
            @PathVariable Long id,
            @PathVariable Long purchaseId,
            @RequestBody @Valid CreditCardInstallmentCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creditCardService.createInstallment(id, purchaseId, dto, authentication));
    }

    @GetMapping("/{id}/purchases/{purchaseId}/installments")
    public ResponseEntity<List<CreditCardInstallmentDto>> findInstallments(
            @PathVariable Long id,
            @PathVariable Long purchaseId,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.findInstallments(id, purchaseId, authentication));
    }

    @PutMapping("/{id}/purchases/{purchaseId}/installments/{installmentId}")
    public ResponseEntity<CreditCardInstallmentDto> updateInstallment(
            @PathVariable Long id,
            @PathVariable Long purchaseId,
            @PathVariable Long installmentId,
            @RequestBody @Valid CreditCardInstallmentUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(
                creditCardService.updateInstallment(id, purchaseId, installmentId, dto, authentication));
    }

    @DeleteMapping("/{id}/purchases/{purchaseId}/installments/{installmentId}")
    public ResponseEntity<Void> deleteInstallment(
            @PathVariable Long id,
            @PathVariable Long purchaseId,
            @PathVariable Long installmentId,
            Authentication authentication) {
        creditCardService.deleteInstallment(id, purchaseId, installmentId, authentication);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // CreditCardRefund
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/bills/{billId}/refunds")
    public ResponseEntity<CreditCardRefundDto> createRefund(
            @PathVariable Long id,
            @PathVariable Long billId,
            @RequestBody @Valid CreditCardRefundCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creditCardService.createRefund(id, billId, dto, authentication));
    }

    @GetMapping("/{id}/bills/{billId}/refunds")
    public ResponseEntity<List<CreditCardRefundDto>> findRefunds(
            @PathVariable Long id,
            @PathVariable Long billId,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.findRefunds(id, billId, authentication));
    }

    @DeleteMapping("/{id}/bills/{billId}/refunds/{refundId}")
    public ResponseEntity<Void> deleteRefund(
            @PathVariable Long id,
            @PathVariable Long billId,
            @PathVariable Long refundId,
            Authentication authentication) {
        creditCardService.deleteRefund(id, billId, refundId, authentication);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // RecurringPurchase
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/recurring")
    public ResponseEntity<RecurringPurchaseDto> createRecurringPurchase(
            @PathVariable Long id,
            @RequestBody @Valid RecurringPurchaseCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creditCardService.createRecurringPurchase(id, dto, authentication));
    }

    @GetMapping("/{id}/recurring")
    public ResponseEntity<List<RecurringPurchaseDto>> findRecurringPurchases(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.findRecurringPurchases(id, authentication));
    }

    @PatchMapping("/{id}/recurring/{recurringId}")
    public ResponseEntity<RecurringPurchaseDto> updateRecurringPurchase(
            @PathVariable Long id,
            @PathVariable Long recurringId,
            @RequestBody @Valid RecurringPurchaseUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(
                creditCardService.updateRecurringPurchase(id, recurringId, dto, authentication));
    }

    @DeleteMapping("/{id}/recurring/{recurringId}")
    public ResponseEntity<Void> deleteRecurringPurchase(
            @PathVariable Long id,
            @PathVariable Long recurringId,
            Authentication authentication) {
        creditCardService.deleteRecurringPurchase(id, recurringId, authentication);
        return ResponseEntity.noContent().build();
    }
}