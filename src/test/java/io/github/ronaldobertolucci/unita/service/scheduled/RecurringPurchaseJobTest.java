package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.card.*;
import io.github.ronaldobertolucci.unita.model.finance.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.CreditCardInstallmentRepository;
import io.github.ronaldobertolucci.unita.repository.CreditCardPurchaseRepository;
import io.github.ronaldobertolucci.unita.repository.RecurringPurchaseRepository;
import io.github.ronaldobertolucci.unita.service.card.CreditCardBillResolverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringPurchaseJobTest {

    @Mock private RecurringPurchaseRepository recurringPurchaseRepository;
    @Mock private CreditCardPurchaseRepository creditCardPurchaseRepository;
    @Mock private CreditCardInstallmentRepository creditCardInstallmentRepository;
    @Mock private CreditCardBillResolverService billResolverService;

    @InjectMocks private RecurringPurchaseJob job;

    private CreditCard creditCard;

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
    }

    @Test
    void execute_WhenDailyAndNeverGenerated_ShouldCreatePurchaseAndInstallment() {
        LocalDate today = LocalDate.now();
        RecurringPurchase rp = buildRecurring(PeriodicityType.DAILY, today.minusDays(5), null, null);
        CreditCardBill bill = buildBill();
        CreditCardPurchase savedPurchase = buildPurchase();

        when(recurringPurchaseRepository.findAllActive(today)).thenReturn(List.of(rp));
        when(creditCardPurchaseRepository.save(any())).thenReturn(savedPurchase);
        when(billResolverService.findOrCreateForDate(any(), any())).thenReturn(bill);
        when(creditCardInstallmentRepository.save(any())).thenReturn(mock(CreditCardInstallment.class));

        job.execute();

        verify(creditCardPurchaseRepository).save(any(CreditCardPurchase.class));
        verify(billResolverService).findOrCreateForDate(creditCard, today);
        verify(creditCardInstallmentRepository).save(any(CreditCardInstallment.class));
        verify(recurringPurchaseRepository).save(rp);
        assertEquals(today, rp.getLastGeneratedDate());
    }

    @Test
    void execute_WhenAlreadyGeneratedThisMonth_ShouldSkip() {
        LocalDate today = LocalDate.now();
        LocalDate lastGenerated = today.withDayOfMonth(1); // mesmo mês
        RecurringPurchase rp = buildRecurring(PeriodicityType.MONTHLY,
                today.minusMonths(1).withDayOfMonth(today.getDayOfMonth()), null, lastGenerated);

        when(recurringPurchaseRepository.findAllActive(today)).thenReturn(List.of(rp));

        job.execute();

        verify(creditCardPurchaseRepository, never()).save(any());
        verify(billResolverService, never()).findOrCreateForDate(any(), any());
    }

    @Test
    void execute_ShouldCreateInstallmentWithCorrectFields() {
        LocalDate today = LocalDate.now();
        RecurringPurchase rp = buildRecurring(PeriodicityType.DAILY, today.minusDays(1), null, null);
        CreditCardBill bill = buildBill();
        CreditCardPurchase savedPurchase = buildPurchase();

        when(recurringPurchaseRepository.findAllActive(today)).thenReturn(List.of(rp));
        when(creditCardPurchaseRepository.save(any())).thenReturn(savedPurchase);
        when(billResolverService.findOrCreateForDate(any(), any())).thenReturn(bill);
        when(creditCardInstallmentRepository.save(any())).thenReturn(mock(CreditCardInstallment.class));

        job.execute();

        ArgumentCaptor<CreditCardInstallment> captor = ArgumentCaptor.forClass(CreditCardInstallment.class);
        verify(creditCardInstallmentRepository).save(captor.capture());

        CreditCardInstallment installment = captor.getValue();
        assertEquals(1, installment.getInstallmentNumber());
        assertEquals(new BigDecimal("49.90"), installment.getAmount());
        assertEquals(bill, installment.getCreditCardBill());
    }

    @Test
    void execute_WhenNoActiveRecurrings_ShouldDoNothing() {
        when(recurringPurchaseRepository.findAllActive(any())).thenReturn(List.of());

        job.execute();

        verify(creditCardPurchaseRepository, never()).save(any());
        verify(billResolverService, never()).findOrCreateForDate(any(), any());
    }

    @Test
    void execute_WhenMultiple_ShouldProcessOnlyEligible() {
        LocalDate today = LocalDate.now();
        RecurringPurchase rp1 = buildRecurring(PeriodicityType.DAILY, today.minusDays(5), null, null);
        RecurringPurchase rp2 = buildRecurring(PeriodicityType.DAILY, today.minusDays(3), null, today); // já gerado

        CreditCardBill bill = buildBill();
        CreditCardPurchase savedPurchase = buildPurchase();

        when(recurringPurchaseRepository.findAllActive(today)).thenReturn(List.of(rp1, rp2));
        when(creditCardPurchaseRepository.save(any())).thenReturn(savedPurchase);
        when(billResolverService.findOrCreateForDate(any(), any())).thenReturn(bill);
        when(creditCardInstallmentRepository.save(any())).thenReturn(mock(CreditCardInstallment.class));

        job.execute();

        verify(creditCardPurchaseRepository, times(1)).save(any());
    }

    private RecurringPurchase buildRecurring(PeriodicityType type, LocalDate startDate,
                                             LocalDate endDate, LocalDate lastGeneratedDate) {
        RecurrencePeriodicity periodicity = new RecurrencePeriodicity();
        periodicity.setId(1L);
        periodicity.setType(type);

        RecurringPurchase rp = RecurringPurchase.builder()
                .creditCard(creditCard).description("Netflix")
                .amount(new BigDecimal("49.90")).periodicity(periodicity)
                .startDate(startDate).endDate(endDate).build();
        rp.setId(1L);
        rp.setLastGeneratedDate(lastGeneratedDate);
        return rp;
    }

    private CreditCardBill buildBill() {
        CreditCardBill bill = CreditCardBill.builder()
                .creditCard(creditCard).closingDate(LocalDate.of(2024, 2, 10))
                .dueDate(LocalDate.of(2024, 3, 10)).status(CreditCardBillStatus.OPEN).build();
        bill.setId(5L);
        return bill;
    }

    private CreditCardPurchase buildPurchase() {
        CreditCardPurchase purchase = CreditCardPurchase.builder()
                .creditCard(creditCard).description("Netflix")
                .totalValue(new BigDecimal("49.90")).purchaseDate(LocalDate.now()).installmentsCount(1).build();
        purchase.setId(2L);
        return purchase;
    }
}