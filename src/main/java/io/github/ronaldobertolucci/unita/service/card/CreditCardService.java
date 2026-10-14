package io.github.ronaldobertolucci.unita.service.card;

import io.github.ronaldobertolucci.unita.service.finance.FinancialConcurrencyService;
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

    private final FinancialConcurrencyService concurrency;

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

        LegalEntity legalEntity = legalEntityRepository.findByIdAndUserId(dto.legalEntityId(), currentUser.getId())
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

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CreditCardDto updateCreditCard(Long id, CreditCardUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(id, currentUser.getId());
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

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteCreditCard(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(id, currentUser.getId());
        if (!creditCardRepository.existsByIdAndUserId(id, currentUser.getId())) {
            throw new EntityNotFoundException("Credit card not found");
        }
        if (creditCardBillRepository.existsByCreditCardIdAndPaymentTransactionIsNotNull(id)) {
            throw new IllegalStateException("Credit card contains bill payments and cannot be deleted");
        }
        if (creditCardBillRepository.existsProtectedCompositionByCardId(id)) {
            throw new IllegalStateException("Credit card contains protected bills and cannot be deleted");
        }
        creditCardRefundRepository.deleteAllByCardId(id);
        creditCardInstallmentRepository.deleteAllByCardId(id);
        creditCardRepository.deleteById(id);
        creditCardRepository.flush();
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

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CreditCardBillDto payBill(Long creditCardId, Long billId, CreditCardBillPayDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(creditCardId, currentUser.getId());
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCardBill bill = creditCardBillRepository.findByIdAndCreditCardId(billId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));

        if (bill.getStatus() != CreditCardBillStatus.CLOSED || bill.getPaymentTransaction() != null) {
            throw new IllegalStateException("Only closed bills can be paid");
        }

        concurrency.lockOwnedPocket(dto.pocketId(), currentUser.getId());
        Pocket pocket = pocketRepository.findByIdAndUserId(dto.pocketId(), currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pocket not found"));

        BigDecimal totalInstallments = creditCardInstallmentRepository.sumAmountByBillId(billId);
        BigDecimal totalRefunds = creditCardRefundRepository.sumAmountByBillId(billId);
        BigDecimal totalAmount = totalInstallments.subtract(totalRefunds);
        if (totalAmount.signum() <= 0) {
            throw new IllegalStateException("Only bills with a positive total can be paid");
        }

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
        transaction.setPaymentBill(bill);
        creditCardBillRepository.save(bill);

        return toBillDto(bill);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CreditCardBillDto reopenBill(Long creditCardId, Long billId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(creditCardId, currentUser.getId());
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCardBill bill = creditCardBillRepository.findByIdAndCreditCardId(billId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));

        if (bill.getStatus() != CreditCardBillStatus.CLOSED || bill.getPaymentTransaction() != null) {
            throw new IllegalStateException("Only closed bills can be reopened");
        }

        bill.setStatus(CreditCardBillStatus.OPEN);
        return toBillDto(creditCardBillRepository.save(bill));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CreditCardBillDto closeBill(Long creditCardId, Long billId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(creditCardId, currentUser.getId());
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCardBill bill = creditCardBillRepository.findByIdAndCreditCardId(billId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));

        if (bill.getStatus() != CreditCardBillStatus.OPEN || bill.getPaymentTransaction() != null) {
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

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CreditCardPurchaseDto createPurchase(Long creditCardId, CreditCardPurchaseCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(creditCardId, currentUser.getId());
        CreditCard creditCard = creditCardRepository.findByIdAndUserId(creditCardId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Credit card not found"));

        List<PurchaseInstallmentPlanDto> plan = PurchasePlan.validate(dto);
        List<CreditCardInstallment> installments = resolvePlan(creditCard, dto, plan, currentUser);
        CreditCardPurchase purchase = CreditCardPurchase.builder().creditCard(creditCard).build();
        applyPurchase(purchase, dto);
        creditCardPurchaseRepository.save(purchase);
        return savePlan(purchase, installments);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CreditCardPurchaseDto updatePurchase(Long creditCardId, Long purchaseId, CreditCardPurchaseCreateDto dto,
                                                 Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(creditCardId, user.getId());
        CreditCardPurchase purchase = creditCardPurchaseRepository.findByIdAndCreditCardId(purchaseId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase not found"));
        requireUnreferencedPurchase(purchaseId);
        List<PurchaseInstallmentPlanDto> plan = PurchasePlan.validate(dto);
        List<CreditCardInstallment> old = creditCardInstallmentRepository.findAllByPurchaseId(purchaseId);
        old.forEach(i -> {
            if (!i.getCreditCardBill().getCreditCard().getId().equals(creditCardId)) {
                throw new EntityNotFoundException("Credit card bill not found");
            }
            requireEditableComposition(i.getCreditCardBill());
        });
        List<CreditCardInstallment> replacement = resolvePlan(purchase.getCreditCard(), dto, plan, user);
        // Flush removal before inserting the same (purchase_id, installment_number).
        // Entity deletion keeps the managed purchase attached; all changes share this transaction.
        creditCardInstallmentRepository.deleteAll(old);
        creditCardInstallmentRepository.flush();
        applyPurchase(purchase, dto);
        return savePlan(purchase, replacement);
    }

    private List<CreditCardInstallment> resolvePlan(CreditCard card, CreditCardPurchaseCreateDto dto,
                                                    List<PurchaseInstallmentPlanDto> plan, User user) {
        Category defaultCategory = categoryService.resolveCategory(dto.categoryId(), user,
                EnumSet.of(CategoryType.EXPENSE, CategoryType.NEUTRAL));
        return plan.stream().map(entry -> {
            Category category = entry.categoryId() == null || entry.categoryId().equals(dto.categoryId())
                    ? defaultCategory : categoryService.resolveCategory(entry.categoryId(), user,
                    EnumSet.of(CategoryType.EXPENSE, CategoryType.NEUTRAL));
            LocalDate date = dto.purchaseDate().plusMonths(entry.installmentNumber() - 1);
            CreditCardBill bill = billResolverService.findOrCreateForDate(card, date);
            requireEditableComposition(bill);
            return CreditCardInstallment.builder().installmentNumber(entry.installmentNumber())
                    .amount(entry.amount().setScale(2)).installmentDate(date).creditCardBill(bill).category(category).build();
        }).toList();
    }

    private void applyPurchase(CreditCardPurchase purchase, CreditCardPurchaseCreateDto dto) {
        purchase.setDescription(dto.description());
        purchase.setTotalValue(dto.totalValue().setScale(2));
        purchase.setPurchaseDate(dto.purchaseDate());
        purchase.setInstallmentsCount(dto.installmentsCount());
    }

    private CreditCardPurchaseDto savePlan(CreditCardPurchase purchase, List<CreditCardInstallment> installments) {
        installments.forEach(i -> { i.setPurchase(purchase); creditCardInstallmentRepository.save(i); });
        creditCardInstallmentRepository.flush();
        return new CreditCardPurchaseDto(purchase, installments.stream().map(CreditCardInstallmentDto::new).toList());
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CreditCardPurchaseDto findPurchase(Long creditCardId, Long purchaseId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, user.getId());
        CreditCardPurchase purchase = creditCardPurchaseRepository.findByIdAndCreditCardId(purchaseId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase not found"));
        return toPurchaseDto(purchase);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<CreditCardPurchaseDto> findPurchases(Long creditCardId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateCreditCardOwnership(creditCardId, currentUser.getId());
        return creditCardPurchaseRepository.findAllByCreditCardId(creditCardId).stream().map(this::toPurchaseDto).toList();
    }

    private CreditCardPurchaseDto toPurchaseDto(CreditCardPurchase purchase) {
        return new CreditCardPurchaseDto(purchase, creditCardInstallmentRepository.findAllByPurchaseId(purchase.getId())
                .stream().map(CreditCardInstallmentDto::new).toList());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deletePurchase(Long creditCardId, Long purchaseId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(creditCardId, currentUser.getId());
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCardPurchase purchase = creditCardPurchaseRepository.findByIdAndCreditCardId(purchaseId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase not found"));

        requireUnreferencedPurchase(purchaseId);
        creditCardInstallmentRepository.findAllByPurchaseId(purchaseId)
                .forEach(i -> requireEditableComposition(i.getCreditCardBill()));
        creditCardInstallmentRepository.deleteAllByPurchaseId(purchaseId);
        creditCardPurchaseRepository.deleteById(purchaseId);
        creditCardPurchaseRepository.flush();
    }

    // -------------------------------------------------------------------------
    // CreditCardInstallment
    // -------------------------------------------------------------------------

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CreditCardInstallmentDto createInstallment(Long creditCardId, Long purchaseId,
                                                      CreditCardInstallmentCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(creditCardId, currentUser.getId());
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        creditCardPurchaseRepository.findByIdAndCreditCardId(purchaseId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase not found"));
        throw new IllegalStateException("Individual installment creation is not allowed. Submit the complete purchase plan instead");
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
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

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CreditCardInstallmentDto updateInstallment(Long creditCardId, Long purchaseId, Long installmentId,
                                                      CreditCardInstallmentUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(creditCardId, currentUser.getId());
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        creditCardPurchaseRepository.findByIdAndCreditCardId(purchaseId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase not found"));

        CreditCardInstallment installment = creditCardInstallmentRepository.findByIdAndPurchaseId(installmentId, purchaseId)
                .orElseThrow(() -> new EntityNotFoundException("Installment not found"));

        CreditCardBill currentBill = installment.getCreditCardBill();
        requireEditableComposition(currentBill);

        CreditCardBill newBill = creditCardBillRepository.findByIdAndCreditCardId(dto.creditCardBillId(), creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));
        requireEditableComposition(newBill);
        if (dto.amount().compareTo(installment.getAmount()) != 0) {
            throw new IllegalStateException("Moving an installment must preserve its amount");
        }
        // D02: an explicit move uses the destination's inclusive period start.
        // A same-bill request keeps the original date.
        if (!currentBill.getId().equals(newBill.getId())) {
            installment.setInstallmentDate(newBill.getPeriodStart());
        }
        installment.setCreditCardBill(newBill);

        return new CreditCardInstallmentDto(creditCardInstallmentRepository.save(installment));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteInstallment(Long creditCardId, Long purchaseId, Long installmentId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(creditCardId, currentUser.getId());
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        creditCardPurchaseRepository.findByIdAndCreditCardId(purchaseId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase not found"));

        CreditCardInstallment installment = creditCardInstallmentRepository.findByIdAndPurchaseId(installmentId, purchaseId)
                .orElseThrow(() -> new EntityNotFoundException("Installment not found"));

        requireEditableComposition(installment.getCreditCardBill());
        throw new IllegalStateException("Individual installment deletion is not allowed. Delete the eligible purchase instead");
    }

    // -------------------------------------------------------------------------
    // CreditCardRefund
    // -------------------------------------------------------------------------

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CreditCardRefundDto createRefund(Long creditCardId, Long billId,
                                            CreditCardRefundCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(creditCardId, currentUser.getId());
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCardBill bill = creditCardBillRepository.findByIdAndCreditCardId(billId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));

        requireEditableComposition(bill);

        validateRefund(dto);
        CreditCardPurchase purchase = null;
        if (dto.purchaseId() != null) {
            purchase = creditCardPurchaseRepository.findByIdAndCreditCardId(dto.purchaseId(), creditCardId)
                    .orElseThrow(() -> new EntityNotFoundException("Purchase not found"));
            BigDecimal accumulated = creditCardRefundRepository.sumAmountByPurchaseId(purchase.getId());
            if (accumulated.add(dto.amount()).compareTo(purchase.getTotalValue()) > 0) {
                throw new IllegalArgumentException("amount: accumulated refunds must not exceed purchase totalValue");
            }
        }
        Category category = categoryService.resolveCategory(dto.categoryId(), currentUser,
                EnumSet.of(CategoryType.INCOME, CategoryType.NEUTRAL));

        CreditCardRefund refund = CreditCardRefund.builder()
                .creditCardBill(bill)
                .purchase(purchase)
                .description(dto.description())
                .amount(dto.amount().setScale(2))
                .refundDate(dto.refundDate())
                .category(category)
                .build();

        return new CreditCardRefundDto(creditCardRefundRepository.save(refund));
    }

    @Transactional(readOnly = true)
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

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteRefund(Long creditCardId, Long billId, Long refundId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        concurrency.lockOwnedCard(creditCardId, currentUser.getId());
        validateCreditCardOwnership(creditCardId, currentUser.getId());

        CreditCardBill bill = creditCardBillRepository.findByIdAndCreditCardId(billId, creditCardId)
                .orElseThrow(() -> new EntityNotFoundException("Credit card bill not found"));

        CreditCardRefund refund = creditCardRefundRepository.findByIdAndBillId(refundId, billId)
                .orElseThrow(() -> new EntityNotFoundException("Refund not found"));

        requireEditableComposition(bill);
        creditCardRefundRepository.delete(refund);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void requireEditableComposition(CreditCardBill bill) {
        if (bill.getStatus() != CreditCardBillStatus.OPEN || bill.getPaymentTransaction() != null) {
            throw new IllegalStateException("Bill composition is protected. Only open bills without a payment can be changed");
        }
    }

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

    private void requireUnreferencedPurchase(Long purchaseId) {
        if (creditCardRefundRepository.existsByPurchaseId(purchaseId)) {
            throw new IllegalStateException("Purchase is referenced by refunds and cannot be changed or deleted");
        }
    }

    private void validateRefund(CreditCardRefundCreateDto dto) {
        if (dto == null) throw new IllegalArgumentException("refund: required");
        if (dto.description() == null || dto.description().isBlank() || dto.description().length() > 255)
            throw new IllegalArgumentException("description: required, maximum 255 characters");
        if (dto.amount() == null || dto.amount().signum() <= 0 || dto.amount().scale() > 2
                || dto.amount().compareTo(new BigDecimal("9999999999999.99")) > 0)
            throw new IllegalArgumentException("amount: must be positive with at most 13 integer digits and 2 decimal places");
        if (dto.refundDate() == null) throw new IllegalArgumentException("refundDate: required");
        if (dto.categoryId() == null || dto.categoryId() <= 0)
            throw new IllegalArgumentException("categoryId: required positive ID");
        if (dto.purchaseId() != null && dto.purchaseId() <= 0)
            throw new IllegalArgumentException("purchaseId: must be positive");
    }

}
