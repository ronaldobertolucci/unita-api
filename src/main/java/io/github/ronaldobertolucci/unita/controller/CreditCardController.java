package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.card.*;
import io.github.ronaldobertolucci.unita.service.card.CreditCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<io.github.ronaldobertolucci.unita.dto.exception.ErrorResponseDto> invalidJson(
            jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new io.github.ronaldobertolucci.unita.dto.exception.ErrorResponseDto(
                400, "Bad Request", "Invalid JSON, field type, date or installmentMode", request.getRequestURI()));
    }

    // -------------------------------------------------------------------------
    // CreditCard
    // -------------------------------------------------------------------------

    @PostMapping
    @Operation(description = "Requires a legal entity owned by the authenticated user; group membership does not share private registrations.")
    @ApiResponse(responseCode = "404", description = "Registration unavailable: nonexistent or owned by another user")
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

    @io.swagger.v3.oas.annotations.Operation(summary = "Excluir cartão elegível", description = "Retorna 409 quando existem faturas CLOSED/PAID ou referência de pagamento, preservando o vínculo financeiro.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Vínculo financeiro protegido; operação rejeitada integralmente")
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

    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Concurrency Timeout: espera por bloqueio excedida; consultar estado antes de repetir escrita")
    @Operation(description = "Pagamento serializado com alterações do cartão e da conta. Segunda chamada para fatura paga retorna 409, sem novo débito; sem chave de idempotência.")
    @ApiResponse(responseCode = "409", description = "Estado diferente de CLOSED ou Concurrency Conflict com rollback")
    @PutMapping("/{id}/bills/{billId}/pay")
    public ResponseEntity<CreditCardBillDto> payBill(
            @PathVariable Long id,
            @PathVariable Long billId,
            @RequestBody @Valid CreditCardBillPayDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.payBill(id, billId, dto, authentication));
    }

    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Concurrency Timeout: espera por bloqueio excedida; consultar estado antes de repetir escrita")
    @ApiResponse(responseCode = "409", description = "Invalid bill state; PAID cannot be reopened")
    @PutMapping("/{id}/bills/{billId}/reopen")
    public ResponseEntity<CreditCardBillDto> reopenBill(
            @PathVariable Long id,
            @PathVariable Long billId,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.reopenBill(id, billId, authentication));
    }

    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Concurrency Timeout: espera por bloqueio excedida; consultar estado antes de repetir escrita")
    @ApiResponse(responseCode = "409", description = "Invalid bill state; PAID cannot be reopened")
    @PutMapping("/{id}/bills/{billId}/close")
    public ResponseEntity<CreditCardBillDto> closeBill(
            @PathVariable Long id,
            @PathVariable Long billId,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.closeBill(id, billId, authentication));
    }

    @GetMapping("/{id}/bills/{billId}/installments")
    public ResponseEntity<BillStatementDto> findBillStatement(
            @PathVariable Long id,
            @PathVariable Long billId,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.findBillStatement(id, billId, authentication));
    }

    // -------------------------------------------------------------------------
    // CreditCardPurchase
    // -------------------------------------------------------------------------

    @Operation(description = "Creates the entire purchase and plan atomically. AUTOMATIC defaults to cents truncated with remainder in the last installment; CUSTOM requires a complete plan. 1..120 installments; positive NUMERIC(15,2). Dates anchored to purchaseDate. No idempotency key: repeated POST creates another purchase.")
    @ApiResponse(responseCode = "201", description = "Complete purchase and installment plan created atomically")
    @ApiResponse(responseCode = "400", description = "Invalid input or inconsistent plan; nothing persisted")
    @ApiResponse(responseCode = "404", description = "Unavailable card/category")
    @ApiResponse(responseCode = "409", description = "An affected bill is not OPEN or has payment; nothing persisted")
    @ApiResponse(responseCode = "503", description = "Concurrency Timeout; consult state before retrying")
    @PostMapping("/{id}/purchases")
    public ResponseEntity<CreditCardPurchaseDto> createPurchase(
            @PathVariable Long id,
            @RequestBody @Valid CreditCardPurchaseCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creditCardService.createPurchase(id, dto, authentication));
    }

    @Operation(description = "Replaces the complete purchase plan atomically. All old and new bills must be OPEN without payment. Installment IDs are replaced. No optimistic version or idempotency key; serialized edits validate current states.")
    @ApiResponse(responseCode = "400", description = "Invalid complete plan; previous purchase preserved")
    @ApiResponse(responseCode = "404", description = "Unavailable card, purchase or category")
    @ApiResponse(responseCode = "409", description = "Protected old or new bill; previous purchase preserved")
    @ApiResponse(responseCode = "503", description = "Concurrency Timeout; consult state before retrying")
    @PutMapping("/{id}/purchases/{purchaseId}")
    public ResponseEntity<CreditCardPurchaseDto> updatePurchase(@PathVariable Long id, @PathVariable Long purchaseId,
            @RequestBody @Valid CreditCardPurchaseCreateDto dto, Authentication authentication) {
        return ResponseEntity.ok(creditCardService.updatePurchase(id, purchaseId, dto, authentication));
    }

    @GetMapping("/{id}/purchases/{purchaseId}")
    public ResponseEntity<CreditCardPurchaseDto> findPurchase(@PathVariable Long id, @PathVariable Long purchaseId,
                                                             Authentication authentication) {
        return ResponseEntity.ok(creditCardService.findPurchase(id, purchaseId, authentication));
    }

    @GetMapping("/{id}/purchases")
    public ResponseEntity<List<CreditCardPurchaseDto>> findPurchases(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(creditCardService.findPurchases(id, authentication));
    }

    @Operation(description = "Composition requires OPEN bills without payment; protected composition is preserved atomically")
    @ApiResponse(responseCode = "409", description = "Protected bill composition or incomplete purchase")
    @ApiResponse(responseCode = "503", description = "Concurrency Timeout; reload state before retrying")
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

    @ApiResponse(responseCode = "409", description = "Individual installment creation retired")
    @Operation(deprecated = true, description = "Retired staged creation: always returns 409 for an owned existing purchase. Use POST/PUT of the complete purchase plan, including legacy repairs.")
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

    @Operation(description = "Both bills must be OPEN without payment. Amount must remain unchanged. Moving to another bill sets installmentDate to destination periodStart; same-bill requests preserve date and number.")
    @ApiResponse(responseCode = "409", description = "Protected origin/destination or changed installment amount")
    @ApiResponse(responseCode = "503", description = "Concurrency Timeout; reload state before retrying")
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

    @Operation(description = "Individual installment deletion is blocked; delete the whole eligible purchase")
    @ApiResponse(responseCode = "409", description = "Protected bill composition or incomplete purchase")
    @ApiResponse(responseCode = "503", description = "Concurrency Timeout; reload state before retrying")
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

    @Operation(description = "Composition requires OPEN bills without payment; protected composition is preserved atomically")
    @ApiResponse(responseCode = "409", description = "Protected bill composition or incomplete purchase")
    @ApiResponse(responseCode = "503", description = "Concurrency Timeout; reload state before retrying")
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

    @Operation(description = "Composition requires OPEN bills without payment; protected composition is preserved atomically")
    @ApiResponse(responseCode = "409", description = "Protected bill composition or incomplete purchase")
    @ApiResponse(responseCode = "503", description = "Concurrency Timeout; reload state before retrying")
    @DeleteMapping("/{id}/bills/{billId}/refunds/{refundId}")
    public ResponseEntity<Void> deleteRefund(
            @PathVariable Long id,
            @PathVariable Long billId,
            @PathVariable Long refundId,
            Authentication authentication) {
        creditCardService.deleteRefund(id, billId, refundId, authentication);
        return ResponseEntity.noContent().build();
    }
}