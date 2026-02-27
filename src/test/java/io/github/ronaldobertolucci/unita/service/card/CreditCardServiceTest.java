package io.github.ronaldobertolucci.unita.service.card;

import io.github.ronaldobertolucci.unita.dto.card.*;
import io.github.ronaldobertolucci.unita.model.card.*;
import io.github.ronaldobertolucci.unita.model.finance.*;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.category.CategoryService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceTest {

    @Mock
    private CreditCardRepository creditCardRepository;
    @Mock
    private CreditCardBillRepository creditCardBillRepository;
    @Mock
    private CreditCardPurchaseRepository creditCardPurchaseRepository;
    @Mock
    private CreditCardInstallmentRepository creditCardInstallmentRepository;
    @Mock
    private CreditCardRefundRepository creditCardRefundRepository;
    @Mock
    private RecurringPurchaseRepository recurringPurchaseRepository;
    @Mock
    private PocketRepository pocketRepository;
    @Mock
    private LegalEntityRepository legalEntityRepository;
    @Mock
    private CardBrandRepository cardBrandRepository;
    @Mock
    private RecurrencePeriodicityRepository recurrencePeriodicityRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CreditCardBillResolverService billResolverService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private CreditCardService creditCardService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        when(authentication.getPrincipal()).thenReturn(currentUser);
    }

    // -------------------------------------------------------------------------
    // CreditCard CRUD
    // -------------------------------------------------------------------------

    @Test
    void createCreditCard_WhenValid_ShouldPersistAndReturnDto() {
        CreditCardCreateDto dto = new CreditCardCreateDto(10L, "1234", 20L, new BigDecimal("5000"), 10, 20);
        LegalEntity le = buildLegalEntity(10L);
        CardBrand brand = buildCardBrand(20L);
        CreditCard saved = buildCard(1L, le, brand);

        when(legalEntityRepository.findById(10L)).thenReturn(Optional.of(le));
        when(cardBrandRepository.findById(20L)).thenReturn(Optional.of(brand));
        when(creditCardRepository.save(any())).thenReturn(saved);

        CreditCardDto result = creditCardService.createCreditCard(dto, authentication);

        assertNotNull(result);
        verify(creditCardRepository).save(any(CreditCard.class));
    }

    @Test
    void createCreditCard_WhenLegalEntityNotFound_ShouldThrow() {
        CreditCardCreateDto dto = new CreditCardCreateDto(99L, "1234", 20L, new BigDecimal("5000"), 10, 20);
        when(legalEntityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> creditCardService.createCreditCard(dto, authentication));
        verify(creditCardRepository, never()).save(any());
    }

    @Test
    void findMyCreditCards_ShouldReturnAllCards() {
        CreditCard c1 = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCard c2 = buildCard(2L, buildLegalEntity(10L), buildCardBrand(20L));
        when(creditCardRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of(c1, c2));

        List<CreditCardDto> result = creditCardService.findMyCreditCards(authentication);

        assertEquals(2, result.size());
    }

    @Test
    void findCreditCardById_WhenOwner_ShouldReturnDto() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        when(creditCardRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(card));

        CreditCardDto result = creditCardService.findCreditCardById(1L, authentication);

        assertNotNull(result);
    }

    @Test
    void findCreditCardById_WhenNotOwner_ShouldThrow() {
        when(creditCardRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> creditCardService.findCreditCardById(99L, authentication));
    }

    @Test
    void updateCreditCard_WhenBothFieldsProvided_ShouldUpdateAndReturn() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardUpdateDto dto = new CreditCardUpdateDto(15, 25);
        when(creditCardRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(card));
        when(creditCardRepository.save(any())).thenReturn(card);

        CreditCardDto result = creditCardService.updateCreditCard(1L, dto, authentication);

        assertNotNull(result);
        assertEquals(15, card.getClosingDay());
        assertEquals(25, card.getDueDay());
        verify(creditCardRepository).save(card);
    }

    @Test
    void updateCreditCard_WhenOnlyClosingDayProvided_ShouldUpdateOnlyClosingDay() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        int originalDueDay = card.getDueDay();
        CreditCardUpdateDto dto = new CreditCardUpdateDto(15, null);
        when(creditCardRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(card));
        when(creditCardRepository.save(any())).thenReturn(card);

        creditCardService.updateCreditCard(1L, dto, authentication);

        assertEquals(15, card.getClosingDay());
        assertEquals(originalDueDay, card.getDueDay());
        verify(creditCardRepository).save(card);
    }

    @Test
    void updateCreditCard_WhenOnlyDueDayProvided_ShouldUpdateOnlyDueDay() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        int originalClosingDay = card.getClosingDay();
        CreditCardUpdateDto dto = new CreditCardUpdateDto(null, 25);
        when(creditCardRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(card));
        when(creditCardRepository.save(any())).thenReturn(card);

        creditCardService.updateCreditCard(1L, dto, authentication);

        assertEquals(originalClosingDay, card.getClosingDay());
        assertEquals(25, card.getDueDay());
        verify(creditCardRepository).save(card);
    }

    @Test
    void updateCreditCard_WhenBothFieldsAreNull_ShouldThrowIllegalArgumentException() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardUpdateDto dto = new CreditCardUpdateDto(null, null);
        when(creditCardRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(card));

        assertThrows(IllegalArgumentException.class,
                () -> creditCardService.updateCreditCard(1L, dto, authentication));
        verify(creditCardRepository, never()).save(any());
    }

    @Test
    void updateCreditCard_WhenNotFound_ShouldThrow() {
        CreditCardUpdateDto dto = new CreditCardUpdateDto(15, null);
        when(creditCardRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> creditCardService.updateCreditCard(99L, dto, authentication));
        verify(creditCardRepository, never()).save(any());
    }

    @Test
    void deleteCreditCard_WhenOwner_ShouldDelete() {
        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);

        creditCardService.deleteCreditCard(1L, authentication);

        verify(creditCardRepository).deleteById(1L);
    }

    @Test
    void deleteCreditCard_WhenNotOwner_ShouldThrow() {
        when(creditCardRepository.existsByIdAndUserId(99L, currentUser.getId())).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> creditCardService.deleteCreditCard(99L, authentication));
        verify(creditCardRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------
    // CreditCardBill
    // -------------------------------------------------------------------------

    @Test
    void payBill_WhenClosed_ShouldCreateTransactionAndMarkAsPaid() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardBill bill = buildBill(5L, card, CreditCardBillStatus.CLOSED);
        Cash pocket = buildCash(3L);
        CreditCardBillPayDto dto = new CreditCardBillPayDto(3L);

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(5L, 1L)).thenReturn(Optional.of(bill));
        when(pocketRepository.findByIdAndUserId(3L, currentUser.getId())).thenReturn(Optional.of(pocket));
        when(transactionRepository.save(any())).thenReturn(mock(Transaction.class));
        when(creditCardBillRepository.save(any())).thenReturn(bill);
        when(creditCardInstallmentRepository.sumAmountByBillId(any())).thenReturn(BigDecimal.ZERO);
        when(creditCardRefundRepository.sumAmountByBillId(any())).thenReturn(BigDecimal.ZERO);
        when(categoryService.findSystemByName("Pagamento de Cartão"))
                .thenReturn(buildCategory(1L, CategoryType.NEUTRAL));

        creditCardService.payBill(1L, 5L, dto, authentication);

        verify(transactionRepository).save(any(Transaction.class));
        verify(creditCardBillRepository).save(bill);
        assertEquals(CreditCardBillStatus.PAID, bill.getStatus());
    }

    @Test
    void payBill_WhenNotClosed_ShouldThrowIllegalStateException() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardBill bill = buildBill(5L, card, CreditCardBillStatus.OPEN);

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(5L, 1L)).thenReturn(Optional.of(bill));

        assertThrows(IllegalStateException.class,
                () -> creditCardService.payBill(1L, 5L, new CreditCardBillPayDto(3L), authentication));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void payBill_WhenBillNotFound_ShouldThrow() {
        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> creditCardService.payBill(1L, 99L, new CreditCardBillPayDto(3L), authentication));
    }

    @Test
    void reopenBill_WhenClosed_ShouldChangeStatusToOpen() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardBill bill = buildBill(5L, card, CreditCardBillStatus.CLOSED);

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(5L, 1L)).thenReturn(Optional.of(bill));
        when(creditCardBillRepository.save(bill)).thenReturn(bill);
        when(creditCardInstallmentRepository.sumAmountByBillId(any())).thenReturn(BigDecimal.ZERO);
        when(creditCardRefundRepository.sumAmountByBillId(any())).thenReturn(BigDecimal.ZERO);

        creditCardService.reopenBill(1L, 5L, authentication);

        assertEquals(CreditCardBillStatus.OPEN, bill.getStatus());
        verify(creditCardBillRepository).save(bill);
    }

    @Test
    void reopenBill_WhenNotClosed_ShouldThrowIllegalStateException() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardBill bill = buildBill(5L, card, CreditCardBillStatus.OPEN);

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(5L, 1L)).thenReturn(Optional.of(bill));

        assertThrows(IllegalStateException.class,
                () -> creditCardService.reopenBill(1L, 5L, authentication));
        verify(creditCardBillRepository, never()).save(any());
    }

    @Test
    void reopenBill_WhenPaid_ShouldThrowIllegalStateException() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardBill bill = buildBill(5L, card, CreditCardBillStatus.PAID);

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(5L, 1L)).thenReturn(Optional.of(bill));

        assertThrows(IllegalStateException.class,
                () -> creditCardService.reopenBill(1L, 5L, authentication));
        verify(creditCardBillRepository, never()).save(any());
    }

    @Test
    void reopenBill_WhenBillNotFound_ShouldThrow() {
        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> creditCardService.reopenBill(1L, 99L, authentication));
    }

    @Test
    void closeBill_WhenOpen_ShouldChangeStatusToClosed() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardBill bill = buildBill(5L, card, CreditCardBillStatus.OPEN);

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(5L, 1L)).thenReturn(Optional.of(bill));
        when(creditCardBillRepository.save(bill)).thenReturn(bill);
        when(creditCardInstallmentRepository.sumAmountByBillId(any())).thenReturn(BigDecimal.ZERO);
        when(creditCardRefundRepository.sumAmountByBillId(any())).thenReturn(BigDecimal.ZERO);

        creditCardService.closeBill(1L, 5L, authentication);

        assertEquals(CreditCardBillStatus.CLOSED, bill.getStatus());
        verify(creditCardBillRepository).save(bill);
    }

    @Test
    void closeBill_WhenNotOpen_ShouldThrowIllegalStateException() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardBill bill = buildBill(5L, card, CreditCardBillStatus.CLOSED);

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(5L, 1L)).thenReturn(Optional.of(bill));

        assertThrows(IllegalStateException.class,
                () -> creditCardService.closeBill(1L, 5L, authentication));
        verify(creditCardBillRepository, never()).save(any());
    }

    @Test
    void closeBill_WhenPaid_ShouldThrowIllegalStateException() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardBill bill = buildBill(5L, card, CreditCardBillStatus.PAID);

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(5L, 1L)).thenReturn(Optional.of(bill));

        assertThrows(IllegalStateException.class,
                () -> creditCardService.closeBill(1L, 5L, authentication));
        verify(creditCardBillRepository, never()).save(any());
    }

    @Test
    void closeBill_WhenBillNotFound_ShouldThrow() {
        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> creditCardService.closeBill(1L, 99L, authentication));
    }

    @Test
    void findBillStatement_WhenValid_ShouldReturnInstallmentsAndRefunds() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardBill bill = buildBill(5L, card, CreditCardBillStatus.OPEN);
        CreditCardPurchase purchase = buildPurchase(2L, card);

        CreditCardInstallment installment = CreditCardInstallment.builder()
                .purchase(purchase).installmentNumber(1)
                .amount(new BigDecimal("100.00")).creditCardBill(bill).build();
        installment.setId(1L);

        CreditCardRefund refund = CreditCardRefund.builder()
                .creditCardBill(bill).description("Estorno")
                .amount(new BigDecimal("50.00")).refundDate(LocalDate.of(2025, 1, 8)).build();
        refund.setId(1L);

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.existsByIdAndCreditCardId(5L, 1L)).thenReturn(true);
        when(creditCardInstallmentRepository.findAllByBillId(5L)).thenReturn(List.of(installment));
        when(creditCardRefundRepository.findAllByBillId(5L)).thenReturn(List.of(refund));

        BillStatementDto result = creditCardService.findBillStatement(1L, 5L, authentication);

        assertNotNull(result);
        assertEquals(1, result.installments().size());
        assertEquals(1, result.refunds().size());
        assertEquals("Compra", result.installments().get(0).description());
        assertEquals("Estorno", result.refunds().get(0).description());
    }

    @Test
    void findBillStatement_WhenBillNotFound_ShouldThrow() {
        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.existsByIdAndCreditCardId(99L, 1L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> creditCardService.findBillStatement(1L, 99L, authentication));
        verifyNoInteractions(creditCardInstallmentRepository);
        verifyNoInteractions(creditCardRefundRepository);
    }

    @Test
    void findBillStatement_WhenCardNotOwned_ShouldThrow() {
        when(creditCardRepository.existsByIdAndUserId(99L, currentUser.getId())).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> creditCardService.findBillStatement(99L, 1L, authentication));
        verifyNoInteractions(creditCardBillRepository);
    }

    @Test
    void findBillStatement_WhenEmpty_ShouldReturnEmptyLists() {
        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.existsByIdAndCreditCardId(5L, 1L)).thenReturn(true);
        when(creditCardInstallmentRepository.findAllByBillId(5L)).thenReturn(List.of());
        when(creditCardRefundRepository.findAllByBillId(5L)).thenReturn(List.of());

        BillStatementDto result = creditCardService.findBillStatement(1L, 5L, authentication);

        assertNotNull(result);
        assertTrue(result.installments().isEmpty());
        assertTrue(result.refunds().isEmpty());
    }

    // -------------------------------------------------------------------------
    // CreditCardInstallment
    // -------------------------------------------------------------------------

    @Test
    void updateInstallment_WhenCurrentBillIsPaid_ShouldThrowIllegalStateException() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardBill paidBill = buildBill(5L, card, CreditCardBillStatus.PAID);
        CreditCardPurchase purchase = buildPurchase(2L, card);
        CreditCardInstallment installment = CreditCardInstallment.builder()
                .purchase(purchase).installmentNumber(1)
                .amount(new BigDecimal("100")).creditCardBill(paidBill).build();
        installment.setId(7L);

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardPurchaseRepository.findByIdAndCreditCardId(2L, 1L)).thenReturn(Optional.of(purchase));
        when(creditCardInstallmentRepository.findByIdAndPurchaseId(7L, 2L)).thenReturn(Optional.of(installment));

        assertThrows(IllegalStateException.class,
                () -> creditCardService.updateInstallment(1L, 2L, 7L,
                        new CreditCardInstallmentUpdateDto(new BigDecimal("100"), 6L), authentication));
    }

    // -------------------------------------------------------------------------
    // RecurringPurchase
    // -------------------------------------------------------------------------

    @Test
    void createRecurringPurchase_WhenValid_ShouldSaveAndGenerateCurrentMonthPurchaseWithStartDate() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        RecurrencePeriodicity periodicity = buildPeriodicity(1L);
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        RecurringPurchaseCreateDto dto = new RecurringPurchaseCreateDto(
                "Netflix", new BigDecimal("49.90"), 1L, startDate, null, 1L);
        CreditCardBill bill = buildBill(5L, card, CreditCardBillStatus.OPEN);

        when(categoryService.resolveCategory(eq(1L), any())).thenReturn(buildCategory(1L, CategoryType.EXPENSE));
        when(creditCardRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(card));
        when(recurrencePeriodicityRepository.findById(1L)).thenReturn(Optional.of(periodicity));

        RecurringPurchase savedRp = RecurringPurchase.builder()
                .creditCard(card).description("Netflix").amount(new BigDecimal("49.90"))
                .periodicity(periodicity).startDate(startDate).build();
        savedRp.setId(1L);
        when(recurringPurchaseRepository.save(any())).thenReturn(savedRp);

        ArgumentCaptor<CreditCardPurchase> purchaseCaptor = ArgumentCaptor.forClass(CreditCardPurchase.class);
        when(creditCardPurchaseRepository.save(purchaseCaptor.capture())).thenReturn(buildPurchase(2L, card));
        when(billResolverService.findOrCreateForDate(any(), any())).thenReturn(bill);
        when(creditCardInstallmentRepository.save(any())).thenReturn(mock(CreditCardInstallment.class));

        RecurringPurchaseDto result = creditCardService.createRecurringPurchase(1L, dto, authentication);

        assertNotNull(result);
        verify(recurringPurchaseRepository).save(any(RecurringPurchase.class));
        verify(creditCardPurchaseRepository).save(any(CreditCardPurchase.class));
        verify(billResolverService).findOrCreateForDate(any(), any());
        assertEquals(startDate, purchaseCaptor.getValue().getPurchaseDate());
    }

    @Test
    void updateRecurringPurchase_WhenOwned_ShouldUpdateAmountAndReturn() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        RecurrencePeriodicity periodicity = buildPeriodicity(1L);
        RecurringPurchase rp = RecurringPurchase.builder()
                .creditCard(card).description("Streaming").amount(new BigDecimal("49.90"))
                .periodicity(periodicity).startDate(LocalDate.of(2025, 1, 1)).build();
        rp.setId(3L);
        RecurringPurchaseUpdateDto dto = new RecurringPurchaseUpdateDto(new BigDecimal("59.90"));

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(recurringPurchaseRepository.findByIdAndCreditCardId(3L, 1L)).thenReturn(Optional.of(rp));
        when(recurringPurchaseRepository.save(rp)).thenReturn(rp);

        RecurringPurchaseDto result = creditCardService.updateRecurringPurchase(1L, 3L, dto, authentication);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("59.90").compareTo(rp.getAmount()));
        verify(recurringPurchaseRepository).save(rp);
    }

    @Test
    void updateRecurringPurchase_WhenCardNotOwned_ShouldThrow() {
        when(creditCardRepository.existsByIdAndUserId(99L, currentUser.getId())).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> creditCardService.updateRecurringPurchase(99L, 1L,
                        new RecurringPurchaseUpdateDto(new BigDecimal("59.90")), authentication));
        verify(recurringPurchaseRepository, never()).save(any());
    }

    @Test
    void updateRecurringPurchase_WhenRecurringNotFound_ShouldThrow() {
        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(recurringPurchaseRepository.findByIdAndCreditCardId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> creditCardService.updateRecurringPurchase(1L, 99L,
                        new RecurringPurchaseUpdateDto(new BigDecimal("59.90")), authentication));
        verify(recurringPurchaseRepository, never()).save(any());
    }

    @Test
    void deleteRecurringPurchase_WhenOwned_ShouldDelete() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        RecurringPurchase rp = RecurringPurchase.builder().creditCard(card).build();
        rp.setId(3L);

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(recurringPurchaseRepository.findByIdAndCreditCardId(3L, 1L)).thenReturn(Optional.of(rp));

        creditCardService.deleteRecurringPurchase(1L, 3L, authentication);

        verify(recurringPurchaseRepository).delete(rp);
    }

    @Test
    void deleteRecurringPurchase_WhenNotFound_ShouldThrow() {
        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(recurringPurchaseRepository.findByIdAndCreditCardId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> creditCardService.deleteRecurringPurchase(1L, 99L, authentication));
    }

    // -------------------------------------------------------------------------
    // CreditCardRefund
    // -------------------------------------------------------------------------

    @Test
    void createRefund_WhenBillExists_ShouldPersistAndReturnDto() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardBill bill = buildBill(5L, card, CreditCardBillStatus.OPEN);
        CreditCardRefundCreateDto dto = new CreditCardRefundCreateDto(
                "Estorno", new BigDecimal("50"), LocalDate.now(), 1L);
        CreditCardRefund saved = CreditCardRefund.builder()
                .creditCardBill(bill).description("Estorno").amount(new BigDecimal("50")).refundDate(LocalDate.now()).build();
        saved.setId(1L);

        when(categoryService.resolveCategory(eq(1L), any())).thenReturn(buildCategory(1L, CategoryType.NEUTRAL));
        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(5L, 1L)).thenReturn(Optional.of(bill));
        when(creditCardRefundRepository.save(any())).thenReturn(saved);

        CreditCardRefundDto result = creditCardService.createRefund(1L, 5L, dto, authentication);

        assertNotNull(result);
        verify(creditCardRefundRepository).save(any(CreditCardRefund.class));
    }

    @Test
    void deleteRefund_WhenOwned_ShouldDelete() {
        CreditCard card = buildCard(1L, buildLegalEntity(10L), buildCardBrand(20L));
        CreditCardBill bill = buildBill(5L, card, CreditCardBillStatus.OPEN);
        CreditCardRefund refund = CreditCardRefund.builder().creditCardBill(bill).build();
        refund.setId(7L);

        when(creditCardRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(creditCardBillRepository.findByIdAndCreditCardId(5L, 1L)).thenReturn(Optional.of(bill));
        when(creditCardRefundRepository.findByIdAndBillId(7L, 5L)).thenReturn(Optional.of(refund));

        creditCardService.deleteRefund(1L, 5L, 7L, authentication);

        verify(creditCardRefundRepository).delete(refund);
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private LegalEntity buildLegalEntity(Long id) {
        LegalEntity le = new LegalEntity();
        le.setId(id);
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco Teste");
        return le;
    }

    private CardBrand buildCardBrand(Long id) {
        CardBrand brand = new CardBrand();
        brand.setId(id);
        brand.setName("Visa");
        return brand;
    }

    private CreditCard buildCard(Long id, LegalEntity le, CardBrand brand) {
        CreditCard card = CreditCard.builder()
                .user(currentUser).legalEntity(le).lastFourDigits("1234")
                .cardBrand(brand).creditLimit(new BigDecimal("5000")).closingDay(10).dueDay(20).build();
        card.setId(id);
        return card;
    }

    private CreditCardBill buildBill(Long id, CreditCard card, CreditCardBillStatus status) {
        CreditCardBill bill = CreditCardBill.builder()
                .creditCard(card).closingDate(LocalDate.of(2024, 1, 10))
                .dueDate(LocalDate.of(2024, 2, 10)).status(status).build();
        bill.setId(id);
        return bill;
    }

    private CreditCardPurchase buildPurchase(Long id, CreditCard card) {
        CreditCardPurchase purchase = CreditCardPurchase.builder()
                .creditCard(card).description("Compra").totalValue(new BigDecimal("100"))
                .purchaseDate(LocalDate.now()).installmentsCount(1).build();
        purchase.setId(id);
        return purchase;
    }

    private Cash buildCash(Long id) {
        Cash cash = new Cash();
        cash.setId(id);
        cash.setUser(currentUser);
        return cash;
    }

    private RecurrencePeriodicity buildPeriodicity(Long id) {
        RecurrencePeriodicity p = new RecurrencePeriodicity();
        p.setId(id);
        p.setName("Mensal");
        p.setType(PeriodicityType.MONTHLY);
        return p;
    }

    private Category buildCategory(Long id, CategoryType type) {
        Category c = Category.builder()
                .user(null).name("Categoria").type(type).system(false).build();
        c.setId(id);
        return c;
    }
}