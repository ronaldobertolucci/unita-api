package io.github.ronaldobertolucci.unita.service.card;

import io.github.ronaldobertolucci.unita.dto.card.*;
import io.github.ronaldobertolucci.unita.model.card.*;
import io.github.ronaldobertolucci.unita.model.finance.CardBrand;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.finance.RecurrencePeriodicity;
import io.github.ronaldobertolucci.unita.model.pocket.Pocket;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final CreditCardBillRepository creditCardBillRepository;
    private final CreditCardPurchaseRepository creditCardPurchaseRepository;
    private final CreditCardInstallmentRepository creditCardInstallmentRepository;
    private final CreditCardRefundRepository creditCardRefundRepository;
    private final RecurringPurchaseRepository recurringPurchaseRepository;
    private final PocketRepository pocketRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final CardBrandRepository cardBrandRepository;
    private final RecurrencePeriodicityRepository recurrencePeriodicityRepository;
    private final TransactionRepository transactionRepository;
    private final CreditCardBillResolverService billResolverService;

    // -------------------------------------------------------------------------
    // CreditCard
    // -------------------------------------------------------------------------

    @Transactional
    public CreditCardDto createCreditCard(CreditCardCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        LegalEntity legalEntity = legalEntityRepository.findById(dto.legalEntityId())
                .orElseThrow(() -> new EntityNotFoundException("Legal entity not found"));

        CardBrand cardBrand = cardBrandRepository.findById(dto.cardBrandId())
                .orElseThrow(() -> new EntityNotFoundException("Card brand not found"));

        CreditCard creditCard = CreditCard.builder()
                .user(currentUser)
                .legalEntity(legalEntity)
                .lastFourDigits(dto.lastFourDigits())
                .cardBrand(cardBrand)
                .creditLimit(dto.creditLimit())
                .closingDay(dto.closingDay())
                .dueDay(dto.dueDay())
                .build();

        return new CreditCardDto(creditCardRepository.save(creditCard));
    }

    public List<CreditCardDto> findMyCreditCards(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return creditCardRepository.findAllByUserId(currentUser.getId())
                .stream()
                .map(CreditCardDto::new)
                .toList();
    }

    public CreditCardDto findCreditCardById(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        CreditCard creditCard = creditCardRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Credit card not found"));
        return new CreditCardDto(creditCard);
    }

    @Transactional
    public void deleteCreditCard(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        if (!creditCardRepository.existsByIdAndUserId(id, currentUser.getId())) {
            throw new EntityNotFoundException("Credit card not found");
        }
        creditCardRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // CreditCardBill
    // -------------------------------------------------------------------------

    public List<CreditCardBillDto> findBills(Long creditCardId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        return creditCardBillRepository.findAllByCreditCardId(creditCardId)
                .stream()
                .map(this::toBillDto)
                .toList();
    }

    public CreditCardBillDto findBillById(Long creditCardId, Long billId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCardBill bill = creditCardBillRepository.findByIdAndCreditCardId(billId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));
        return toBillDto(bill);
    }

    @Transactional
    public CreditCardBillDto payBill(Long creditCardId, Long billId, CreditCardBillPayDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCardBill bill = creditCardBillRepository.findByIdAndCreditCardId(billId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));

        if (bill.getStatus() != CreditCardBillStatus.CLOSED) {
            throw new IllegalStateException("Only closed bills can be paid");
        }

        Pocket pocket = pocketRepository.findByIdAndUserId(dto.pocketId(), currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pocket not found"));

        BigDecimal totalInstallments = creditCardInstallmentRepository.sumAmountByBillId(billId);
        BigDecimal totalRefunds = creditCardRefundRepository.sumAmountByBillId(billId);
        BigDecimal totalAmount = totalInstallments.subtract(totalRefunds);

        Transaction transaction = Transaction.builder()
                .pocket(pocket)
                .amount(totalAmount)
                .direction(Direction.EXPENSE)
                .transactionDate(LocalDate.now())
                .description("Pagamento fatura - " + bill.getCreditCard().getLegalEntity().getCorporateName()
                        + " ..." + bill.getCreditCard().getLastFourDigits()
                        + " (" + bill.getDueDate() + ")")
                .build();

        transactionRepository.save(transaction);

        bill.setStatus(CreditCardBillStatus.PAID);
        bill.setPaymentTransaction(transaction);
        creditCardBillRepository.save(bill);

        return toBillDto(bill);
    }

    // -------------------------------------------------------------------------
    // CreditCardPurchase
    // -------------------------------------------------------------------------

    @Transactional
    public CreditCardPurchaseDto createPurchase(Long creditCardId, CreditCardPurchaseCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        CreditCard creditCard = creditCardRepository.findByIdAndUserId(creditCardId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Credit card not found"));

        CreditCardPurchase purchase = CreditCardPurchase.builder()
                .creditCard(creditCard)
                .description(dto.description())
                .totalValue(dto.totalValue())
                .purchaseDate(dto.purchaseDate())
                .installmentsCount(dto.installmentsCount())
                .build();

        return new CreditCardPurchaseDto(creditCardPurchaseRepository.save(purchase));
    }

    public List<CreditCardPurchaseDto> findPurchases(Long creditCardId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        return creditCardPurchaseRepository.findAllByCreditCardId(creditCardId)
                .stream()
                .map(CreditCardPurchaseDto::new)
                .toList();
    }

    @Transactional
    public void deletePurchase(Long creditCardId, Long purchaseId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCardPurchase purchase = creditCardPurchaseRepository.findByIdAndCreditCardId(purchaseId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase not found"));

        creditCardPurchaseRepository.delete(purchase);
    }

    // -------------------------------------------------------------------------
    // CreditCardInstallment
    // -------------------------------------------------------------------------

    @Transactional
    public CreditCardInstallmentDto createInstallment(Long creditCardId, Long purchaseId,
                                                      CreditCardInstallmentCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCard creditCard = creditCardRepository.findByIdAndUserId(creditCardId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Credit card not found"));

        CreditCardPurchase purchase = creditCardPurchaseRepository.findByIdAndCreditCardId(purchaseId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase not found"));

        CreditCardBill bill = billResolverService.findOrCreateForDate(creditCard, purchase.getPurchaseDate());

        CreditCardInstallment installment = CreditCardInstallment.builder()
                .purchase(purchase)
                .installmentNumber(dto.installmentNumber())
                .amount(dto.amount())
                .creditCardBill(bill)
                .build();

        return new CreditCardInstallmentDto(creditCardInstallmentRepository.save(installment));
    }

    public List<CreditCardInstallmentDto> findInstallments(Long creditCardId, Long purchaseId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        creditCardPurchaseRepository.findByIdAndCreditCardId(purchaseId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase not found"));

        return creditCardInstallmentRepository.findAllByPurchaseId(purchaseId)
                .stream()
                .map(CreditCardInstallmentDto::new)
                .toList();
    }

    @Transactional
    public CreditCardInstallmentDto updateInstallment(Long creditCardId, Long purchaseId, Long installmentId,
                                                      CreditCardInstallmentUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        creditCardPurchaseRepository.findByIdAndCreditCardId(purchaseId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase not found"));

        CreditCardInstallment installment = creditCardInstallmentRepository.findByIdAndPurchaseId(installmentId, purchaseId)
                .orElseThrow(() -> new EntityNotFoundException("Installment not found"));

        CreditCardBill currentBill = installment.getCreditCardBill();
        if (currentBill.getStatus() == CreditCardBillStatus.PAID) {
            throw new IllegalStateException("Cannot move installment from a paid bill");
        }

        CreditCardBill newBill = creditCardBillRepository.findByIdAndCreditCardId(dto.creditCardBillId(), creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));

        installment.setAmount(dto.amount());
        installment.setCreditCardBill(newBill);

        return new CreditCardInstallmentDto(creditCardInstallmentRepository.save(installment));
    }

    @Transactional
    public void deleteInstallment(Long creditCardId, Long purchaseId, Long installmentId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        creditCardPurchaseRepository.findByIdAndCreditCardId(purchaseId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase not found"));

        CreditCardInstallment installment = creditCardInstallmentRepository.findByIdAndPurchaseId(installmentId, purchaseId)
                .orElseThrow(() -> new EntityNotFoundException("Installment not found"));

        creditCardInstallmentRepository.delete(installment);
    }

    // -------------------------------------------------------------------------
    // CreditCardRefund
    // -------------------------------------------------------------------------

    @Transactional
    public CreditCardRefundDto createRefund(Long creditCardId, Long billId,
                                            CreditCardRefundCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCardBill bill = creditCardBillRepository.findByIdAndCreditCardId(billId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));

        CreditCardRefund refund = CreditCardRefund.builder()
                .creditCardBill(bill)
                .description(dto.description())
                .amount(dto.amount())
                .refundDate(dto.refundDate())
                .build();

        return new CreditCardRefundDto(creditCardRefundRepository.save(refund));
    }

    public List<CreditCardRefundDto> findRefunds(Long creditCardId, Long billId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        creditCardBillRepository.findByIdAndCreditCardId(billId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));

        return creditCardRefundRepository.findAllByBillId(billId)
                .stream()
                .map(CreditCardRefundDto::new)
                .toList();
    }

    @Transactional
    public void deleteRefund(Long creditCardId, Long billId, Long refundId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        creditCardBillRepository.findByIdAndCreditCardId(billId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));

        CreditCardRefund refund = creditCardRefundRepository.findByIdAndBillId(refundId, billId)
                .orElseThrow(() -> new EntityNotFoundException("Refund not found"));

        creditCardRefundRepository.delete(refund);
    }

    // -------------------------------------------------------------------------
    // RecurringPurchase
    // -------------------------------------------------------------------------

    @Transactional
    public RecurringPurchaseDto createRecurringPurchase(Long creditCardId, RecurringPurchaseCreateDto dto,
                                                        Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        CreditCard creditCard = creditCardRepository.findByIdAndUserId(creditCardId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Credit card not found"));

        RecurrencePeriodicity periodicity = recurrencePeriodicityRepository.findById(dto.periodicityId())
                .orElseThrow(() -> new EntityNotFoundException("Periodicity not found"));

        RecurringPurchase recurringPurchase = RecurringPurchase.builder()
                .creditCard(creditCard)
                .description(dto.description())
                .amount(dto.amount())
                .periodicity(periodicity)
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .build();

        recurringPurchaseRepository.save(recurringPurchase);

        generateCurrentMonthPurchase(recurringPurchase, creditCard);

        return new RecurringPurchaseDto(recurringPurchase);
    }

    public List<RecurringPurchaseDto> findRecurringPurchases(Long creditCardId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        return recurringPurchaseRepository.findAllByCreditCardId(creditCardId)
                .stream()
                .map(RecurringPurchaseDto::new)
                .toList();
    }

    @Transactional
    public void deleteRecurringPurchase(Long creditCardId, Long recurringId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        RecurringPurchase recurringPurchase = recurringPurchaseRepository.findByIdAndCreditCardId(recurringId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Recurring purchase not found"));

        recurringPurchaseRepository.delete(recurringPurchase);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void validateCreditCardOwnership(Long creditCardId, Long userId) {
        if (!creditCardRepository.existsByIdAndUserId(creditCardId, userId)) {
            throw new EntityNotFoundException("Credit card not found");
        }
    }

    private CreditCardBillDto toBillDto(CreditCardBill bill) {
        BigDecimal totalInstallments = creditCardInstallmentRepository.sumAmountByBillId(bill.getId());
        BigDecimal totalRefunds = creditCardRefundRepository.sumAmountByBillId(bill.getId());
        return new CreditCardBillDto(bill, totalInstallments, totalRefunds);
    }

    private void generateCurrentMonthPurchase(RecurringPurchase recurringPurchase, CreditCard creditCard) {
        LocalDate today = LocalDate.now();

        CreditCardPurchase purchase = CreditCardPurchase.builder()
                .creditCard(creditCard)
                .description(recurringPurchase.getDescription())
                .totalValue(recurringPurchase.getAmount())
                .purchaseDate(today)
                .installmentsCount(1)
                .build();

        creditCardPurchaseRepository.save(purchase);

        CreditCardBill bill = billResolverService.findOrCreateForDate(creditCard, today);

        CreditCardInstallment installment = CreditCardInstallment.builder()
                .purchase(purchase)
                .installmentNumber(1)
                .amount(recurringPurchase.getAmount())
                .creditCardBill(bill)
                .build();

        creditCardInstallmentRepository.save(installment);
    }
}