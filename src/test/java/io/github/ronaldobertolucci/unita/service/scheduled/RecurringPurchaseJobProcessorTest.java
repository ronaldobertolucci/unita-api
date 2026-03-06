package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.card.*;
import io.github.ronaldobertolucci.unita.model.finance.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.CreditCardInstallmentRepository;
import io.github.ronaldobertolucci.unita.repository.CreditCardPurchaseRepository;
import io.github.ronaldobertolucci.unita.repository.RecurringPurchaseRepository;
import io.github.ronaldobertolucci.unita.service.card.CreditCardBillResolverService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringPurchaseJobProcessorTest {

    @Mock private RecurringPurchaseRepository recurringPurchaseRepository;
    @Mock private CreditCardPurchaseRepository creditCardPurchaseRepository;
    @Mock private CreditCardInstallmentRepository creditCardInstallmentRepository;
    @Mock private CreditCardBillResolverService billResolverService;

    @InjectMocks private RecurringPurchaseJobProcessor processor;

    private CreditCard creditCard;
    private Category category;
    private RecurringPurchase recurringPurchase;
    private CreditCardBill bill;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);

        LegalEntity le = new LegalEntity();
        le.setId(10L);
        le.setCorporateName("Banco");

        CardBrand brand = new CardBrand();
        brand.setId(20L);
        brand.setName("Visa");

        creditCard = CreditCard.builder()
                .user(user).legalEntity(le).lastFourDigits("1234")
                .cardBrand(brand).creditLimit(new BigDecimal("5000")).closingDay(10).dueDay(20)
                .build();
        creditCard.setId(1L);

        category = new Category();
        category.setId(5L);

        RecurrencePeriodicity periodicity = new RecurrencePeriodicity();
        periodicity.setId(1L);
        periodicity.setType(PeriodicityType.DAILY);

        recurringPurchase = RecurringPurchase.builder()
                .creditCard(creditCard)
                .description("Netflix")
                .amount(new BigDecimal("49.90"))
                .periodicity(periodicity)
                .startDate(LocalDate.now().minusDays(5))
                .category(category)
                .build();
        recurringPurchase.setId(1L);

        bill = CreditCardBill.builder()
                .creditCard(creditCard)
                .closingDate(LocalDate.of(2024, 2, 10))
                .dueDate(LocalDate.of(2024, 3, 10))
                .status(CreditCardBillStatus.OPEN)
                .build();
        bill.setId(5L);
    }

    @Test
    void process_WhenRecurringPurchaseExists_ShouldCreatePurchaseWithCorrectFields() {
        LocalDate today = LocalDate.now();
        CreditCardPurchase savedPurchase = buildSavedPurchase(today);

        when(recurringPurchaseRepository.findById(1L)).thenReturn(Optional.of(recurringPurchase));
        when(creditCardPurchaseRepository.save(any())).thenReturn(savedPurchase);
        when(billResolverService.findOrCreateForDate(any(), any())).thenReturn(bill);

        processor.process(1L, today);

        ArgumentCaptor<CreditCardPurchase> captor = ArgumentCaptor.forClass(CreditCardPurchase.class);
        verify(creditCardPurchaseRepository).save(captor.capture());

        CreditCardPurchase saved = captor.getValue();
        assertEquals(creditCard, saved.getCreditCard());
        assertEquals("Netflix", saved.getDescription());
        assertEquals(new BigDecimal("49.90"), saved.getTotalValue());
        assertEquals(today, saved.getPurchaseDate());
        assertEquals(1, saved.getInstallmentsCount());
    }

    @Test
    void process_WhenRecurringPurchaseExists_ShouldCreateInstallmentWithCategoryAndCorrectFields() {
        LocalDate today = LocalDate.now();
        CreditCardPurchase savedPurchase = buildSavedPurchase(today);

        when(recurringPurchaseRepository.findById(1L)).thenReturn(Optional.of(recurringPurchase));
        when(creditCardPurchaseRepository.save(any())).thenReturn(savedPurchase);
        when(billResolverService.findOrCreateForDate(any(), any())).thenReturn(bill);

        processor.process(1L, today);

        ArgumentCaptor<CreditCardInstallment> captor = ArgumentCaptor.forClass(CreditCardInstallment.class);
        verify(creditCardInstallmentRepository).save(captor.capture());

        CreditCardInstallment installment = captor.getValue();
        assertEquals(1, installment.getInstallmentNumber());
        assertEquals(new BigDecimal("49.90"), installment.getAmount());
        assertEquals(bill, installment.getCreditCardBill());
        assertEquals(category, installment.getCategory());
    }

    @Test
    void process_WhenRecurringPurchaseExists_ShouldResolveBillForToday() {
        LocalDate today = LocalDate.now();
        CreditCardPurchase savedPurchase = buildSavedPurchase(today);

        when(recurringPurchaseRepository.findById(1L)).thenReturn(Optional.of(recurringPurchase));
        when(creditCardPurchaseRepository.save(any())).thenReturn(savedPurchase);
        when(billResolverService.findOrCreateForDate(any(), any())).thenReturn(bill);

        processor.process(1L, today);

        verify(billResolverService).findOrCreateForDate(creditCard, today);
    }

    @Test
    void process_WhenRecurringPurchaseExists_ShouldUpdateLastGeneratedDate() {
        LocalDate today = LocalDate.now();
        CreditCardPurchase savedPurchase = buildSavedPurchase(today);

        when(recurringPurchaseRepository.findById(1L)).thenReturn(Optional.of(recurringPurchase));
        when(creditCardPurchaseRepository.save(any())).thenReturn(savedPurchase);
        when(billResolverService.findOrCreateForDate(any(), any())).thenReturn(bill);

        processor.process(1L, today);

        assertEquals(today, recurringPurchase.getLastGeneratedDate());
        verify(recurringPurchaseRepository).save(recurringPurchase);
    }

    @Test
    void process_WhenRecurringPurchaseNotFound_ShouldThrowEntityNotFoundException() {
        when(recurringPurchaseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> processor.process(99L, LocalDate.now()));

        verify(creditCardPurchaseRepository, never()).save(any());
        verify(creditCardInstallmentRepository, never()).save(any());
        verify(recurringPurchaseRepository, never()).save(any());
    }

    private CreditCardPurchase buildSavedPurchase(LocalDate purchaseDate) {
        CreditCardPurchase purchase = CreditCardPurchase.builder()
                .creditCard(creditCard)
                .description("Netflix")
                .totalValue(new BigDecimal("49.90"))
                .purchaseDate(purchaseDate)
                .installmentsCount(1)
                .build();
        purchase.setId(2L);
        return purchase;
    }
}