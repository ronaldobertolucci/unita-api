package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.card.CreditCard;
import io.github.ronaldobertolucci.unita.model.card.RecurringPurchase;
import io.github.ronaldobertolucci.unita.model.finance.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.RecurringPurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringPurchaseJobTest {

    @Mock private RecurringPurchaseRepository recurringPurchaseRepository;
    @Mock private RecurringPurchaseJobProcessor processor;

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
    void execute_WhenDailyAndNeverGenerated_ShouldCallProcessor() {
        LocalDate today = LocalDate.now();
        RecurringPurchase rp = buildRecurring(PeriodicityType.DAILY, today.minusDays(5), null, null);

        when(recurringPurchaseRepository.findAllActive(today)).thenReturn(List.of(rp));

        job.execute();

        verify(processor).process(eq(rp.getId()), eq(today));
    }

    @Test
    void execute_WhenAlreadyGeneratedThisMonth_ShouldSkip() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = YearMonth.from(today).minusMonths(1).atDay(
                Math.min(today.getDayOfMonth(), YearMonth.from(today).minusMonths(1).lengthOfMonth())
        );
        LocalDate lastGenerated = today.withDayOfMonth(1);
        RecurringPurchase rp = buildRecurring(PeriodicityType.MONTHLY, startDate, null, lastGenerated);

        when(recurringPurchaseRepository.findAllActive(today)).thenReturn(List.of(rp));

        job.execute();

        verify(processor, never()).process(any(), any());
    }

    @Test
    void execute_WhenMonthlyAndDifferentDayOfMonth_ShouldSkip() {
        LocalDate today = LocalDate.now();
        int otherDay = today.getDayOfMonth() == 1 ? 2 : 1;
        LocalDate startDate = today.withDayOfMonth(otherDay).minusMonths(1);
        RecurringPurchase rp = buildRecurring(PeriodicityType.MONTHLY, startDate, null, null);

        when(recurringPurchaseRepository.findAllActive(today)).thenReturn(List.of(rp));

        job.execute();

        verify(processor, never()).process(any(), any());
    }

    @Test
    void execute_WhenWeeklyAndSameDayOfWeek_ShouldCallProcessor() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusWeeks(3);
        RecurringPurchase rp = buildRecurring(PeriodicityType.WEEKLY, startDate, null, null);

        when(recurringPurchaseRepository.findAllActive(today)).thenReturn(List.of(rp));

        job.execute();

        verify(processor).process(eq(rp.getId()), eq(today));
    }

    @Test
    void execute_WhenYearlyAndSameMonthAndDay_ShouldCallProcessor() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusYears(1);
        RecurringPurchase rp = buildRecurring(PeriodicityType.YEARLY, startDate, null, null);

        when(recurringPurchaseRepository.findAllActive(today)).thenReturn(List.of(rp));

        job.execute();

        verify(processor).process(eq(rp.getId()), eq(today));
    }

    @Test
    void execute_WhenYearlyAndAlreadyGeneratedThisYear_ShouldSkip() {
        LocalDate today = LocalDate.now();
        LocalDate lastGenerated = today.withDayOfYear(1);
        RecurringPurchase rp = buildRecurring(PeriodicityType.YEARLY, today.minusYears(1), null, lastGenerated);

        when(recurringPurchaseRepository.findAllActive(today)).thenReturn(List.of(rp));

        job.execute();

        verify(processor, never()).process(any(), any());
    }

    @Test
    void execute_WhenNoActiveRecurrings_ShouldNotCallProcessor() {
        when(recurringPurchaseRepository.findAllActive(any())).thenReturn(List.of());

        job.execute();

        verify(processor, never()).process(any(), any());
    }

    @Test
    void execute_WhenProcessorThrows_ShouldContinueAndNotRethrow() {
        LocalDate today = LocalDate.now();
        RecurringPurchase rp1 = buildRecurring(PeriodicityType.DAILY, today.minusDays(5), null, null);
        rp1.setId(1L);
        RecurringPurchase rp2 = buildRecurring(PeriodicityType.DAILY, today.minusDays(3), null, null);
        rp2.setId(2L);

        when(recurringPurchaseRepository.findAllActive(today)).thenReturn(List.of(rp1, rp2));
        doThrow(new RuntimeException("DB error")).when(processor).process(eq(1L), any());

        job.execute();

        verify(processor).process(eq(1L), eq(today));
        verify(processor).process(eq(2L), eq(today));
    }

    @Test
    void execute_WhenMultiple_ShouldProcessOnlyEligible() {
        LocalDate today = LocalDate.now();
        RecurringPurchase rp1 = buildRecurring(PeriodicityType.DAILY, today.minusDays(5), null, null);
        rp1.setId(1L);
        RecurringPurchase rp2 = buildRecurring(PeriodicityType.DAILY, today.minusDays(3), null, today);
        rp2.setId(2L);

        when(recurringPurchaseRepository.findAllActive(today)).thenReturn(List.of(rp1, rp2));

        job.execute();

        verify(processor, times(1)).process(eq(1L), eq(today));
        verify(processor, never()).process(eq(2L), any());
    }

    private RecurringPurchase buildRecurring(PeriodicityType type, LocalDate startDate,
                                             LocalDate endDate, LocalDate lastGeneratedDate) {
        RecurrencePeriodicity periodicity = new RecurrencePeriodicity();
        periodicity.setId(1L);
        periodicity.setType(type);

        RecurringPurchase rp = RecurringPurchase.builder()
                .creditCard(creditCard)
                .description("Netflix")
                .amount(new BigDecimal("49.90"))
                .periodicity(periodicity)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        rp.setId(1L);
        rp.setLastGeneratedDate(lastGeneratedDate);
        return rp;
    }
}