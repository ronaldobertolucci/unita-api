package io.github.ronaldobertolucci.unita.service.card;

import io.github.ronaldobertolucci.unita.dto.card.*;
import io.github.ronaldobertolucci.unita.model.card.*;
import io.github.ronaldobertolucci.unita.model.finance.*;
import io.github.ronaldobertolucci.unita.model.pocket.Pocket;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.category.CategoryService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final CreditCardBillRepository creditCardBillRepository;
    private final CreditCardPurchaseRepository creditCardPurchaseRepository;
    private final CreditCardInstallmentRepository creditCardInstallmentRepository;
    private final CreditCardRefundRepository creditCardRefundRepository;
    private final PocketRepository pocketRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final CardBrandRepository cardBrandRepository;
    private final TransactionRepository transactionRepository;
    private final CreditCardBillResolverService billResolverService;
    private final CategoryService categoryService;

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

        CreditCard saved = creditCardRepository.save(creditCard);
        billResolverService.createInitialBill(saved, LocalDate.now());

        return new CreditCardDto(saved);
    }

    public List<CreditCardDto> findMyCreditCards(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return creditCardRepository.findAllByUserId(currentUser.getId())
                .stream()
                .sorted(Comparator.comparing(creditCard -> creditCard.getLegalEntity().getCorporateName()))
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
    public CreditCardDto updateCreditCard(Long id, CreditCardUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        CreditCard creditCard = creditCardRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Credit card not found"));

        if (dto.closingDay() == null && dto.dueDay() == null && dto.creditLimit() == null) {
            throw new IllegalArgumentException("At least one field must be provided");
        }

        if (dto.closingDay() != null) {
            creditCard.setClosingDay(dto.closingDay());
        }
        if (dto.dueDay() != null) {
            creditCard.setDueDay(dto.dueDay());
        }
        if (dto.creditLimit() != null) {
            creditCard.setCreditLimit(dto.creditLimit());
        }

        creditCardRepository.save(creditCard);

        int newClosingDay = creditCard.getClosingDay();
        int newDueDay = creditCard.getDueDay();

        List<CreditCardBill> openBills = creditCardBillRepository
                .findOpenBillsFromToday(creditCard.getId(), LocalDate.now());

        List<CreditCardBill> billsToUpdate = openBills.isEmpty()
                ? List.of()
                : openBills.subList(1, openBills.size());

        CreditCardBill previous = openBills.isEmpty() ? null : openBills.get(0);

        for (CreditCardBill bill : billsToUpdate) {
            LocalDate periodStart = previous.getClosingDate();
            LocalDate newClosingDate = periodStart.plusMonths(1)
                    .withDayOfMonth(Math.min(newClosingDay, periodStart.plusMonths(1).lengthOfMonth()));

            LocalDate dueDateMonth = newDueDay > newClosingDay ? newClosingDate : newClosingDate.plusMonths(1);
            LocalDate newDueDate = dueDateMonth.withDayOfMonth(Math.min(newDueDay, dueDateMonth.lengthOfMonth()));

            bill.setPeriodStart(periodStart);
            bill.setClosingDate(newClosingDate);
            bill.setDueDate(newDueDate);
            bill.setClosingDay(newClosingDay);
            bill.setDueDay(newDueDay);

            previous = bill;
        }

        creditCardBillRepository.saveAll(billsToUpdate);

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

        Category paymentCategory = categoryService.findSystemByName("Pagamento de Cartão");
        if (!EnumSet.of(CategoryType.EXPENSE, CategoryType.NEUTRAL).contains(paymentCategory.getType())) {
            throw new IllegalArgumentException("Category type " + paymentCategory.getType() + " is not allowed in this context");
        }

        Transaction transaction = Transaction.builder()
                .pocket(pocket)
                .amount(totalAmount)
                .direction(Direction.EXPENSE)
                .transactionDate(LocalDate.now())
                .description("Pagamento fatura - " + bill.getCreditCard().getLegalEntity().getCorporateName()
                        + " ..." + bill.getCreditCard().getLastFourDigits()
                        + " (" + bill.getDueDate() + ")")
                .category(paymentCategory)
                .build();

        transactionRepository.save(transaction);

        bill.setStatus(CreditCardBillStatus.PAID);
        bill.setPaymentTransaction(transaction);
        creditCardBillRepository.save(bill);

        return toBillDto(bill);
    }

    @Transactional
    public CreditCardBillDto reopenBill(Long creditCardId, Long billId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCardBill bill = creditCardBillRepository.findByIdAndCreditCardId(billId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));

        if (bill.getStatus() != CreditCardBillStatus.CLOSED) {
            throw new IllegalStateException("Only closed bills can be reopened");
        }

        bill.setStatus(CreditCardBillStatus.OPEN);
        return toBillDto(creditCardBillRepository.save(bill));
    }

    @Transactional
    public CreditCardBillDto closeBill(Long creditCardId, Long billId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCardBill bill = creditCardBillRepository.findByIdAndCreditCardId(billId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));

        if (bill.getStatus() != CreditCardBillStatus.OPEN) {
            throw new IllegalStateException("Only open bills can be closed");
        }

        bill.setStatus(CreditCardBillStatus.CLOSED);
        return toBillDto(creditCardBillRepository.save(bill));
    }

    public BillStatementDto findBillStatement(Long creditCardId, Long billId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        if (!creditCardBillRepository.existsByIdAndCreditCardId(billId, creditCardId)) {
            throw new EntityNotFoundException("Credit card bill not found");
        }

        List<BillInstallmentDto> installments = creditCardInstallmentRepository.findAllByBillId(billId)
                .stream()
                .map(BillInstallmentDto::new)
                .toList();

        List<CreditCardRefundDto> refunds = creditCardRefundRepository.findAllByBillId(billId)
                .stream()
                .map(CreditCardRefundDto::new)
                .toList();

        return new BillStatementDto(installments, refunds);
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

        Category category = categoryService.resolveCategory(dto.categoryId(), currentUser,
                EnumSet.of(CategoryType.EXPENSE, CategoryType.NEUTRAL));

        LocalDate installmentMonth = purchase
                .getPurchaseDate()
                .plusMonths(dto.installmentNumber() - 1);
        LocalDate installmentDate = installmentMonth
                .withDayOfMonth(Math.min(purchase.getPurchaseDate().getDayOfMonth(), installmentMonth.lengthOfMonth()));

        CreditCardBill bill = billResolverService.findOrCreateForDate(creditCard, installmentDate);

        if (bill.getStatus() == CreditCardBillStatus.PAID) {
            throw new IllegalStateException("Cannot add installment to a paid bill");
        }
        if (bill.getStatus() == CreditCardBillStatus.CLOSED) {
            throw new IllegalStateException("Bill is closed. Reopen it before adding installments");
        }

        CreditCardInstallment installment = CreditCardInstallment.builder()
                .purchase(purchase)
                .installmentNumber(dto.installmentNumber())
                .installmentDate(installmentDate)
                .amount(dto.amount())
                .creditCardBill(bill)
                .category(category)
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

        Category category = categoryService.resolveCategory(dto.categoryId(), currentUser,
                EnumSet.of(CategoryType.INCOME, CategoryType.NEUTRAL));

        CreditCardRefund refund = CreditCardRefund.builder()
                .creditCardBill(bill)
                .description(dto.description())
                .amount(dto.amount())
                .refundDate(dto.refundDate())
                .category(category)
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

}