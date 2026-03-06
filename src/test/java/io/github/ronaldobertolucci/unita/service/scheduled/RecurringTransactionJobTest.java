package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.finance.PeriodicityType;
import io.github.ronaldobertolucci.unita.model.finance.RecurrencePeriodicity;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.pocket.RecurringTransaction;
import io.github.ronaldobertolucci.unita.repository.RecurringTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringTransactionJobTest {

    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private RecurringTransactionJobProcessor processor;

    @InjectMocks private RecurringTransactionJob job;

    private Cash pocket;

    @BeforeEach
    void setUp() {
        pocket = new Cash();
        pocket.setId(1L);
    }

    @Test
    void execute_WhenDailyAndNeverGenerated_ShouldCallProcessor() {
        LocalDate today = LocalDate.now();
        RecurringTransaction rt = buildRecurring(PeriodicityType.DAILY, today.minusDays(5), null, null);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(processor).process(eq(rt.getId()), eq(today));
    }

    @Test
    void execute_WhenDailyAndAlreadyGeneratedToday_ShouldSkip() {
        LocalDate today = LocalDate.now();
        RecurringTransaction rt = buildRecurring(PeriodicityType.DAILY, today.minusDays(5), null, today);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(processor, never()).process(any(), any());
    }

    @Test
    void execute_WhenMonthlyAndSameDayOfMonth_ShouldCallProcessor() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(2).withDayOfMonth(today.getDayOfMonth());
        RecurringTransaction rt = buildRecurring(PeriodicityType.MONTHLY, startDate, null, null);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(processor).process(eq(rt.getId()), eq(today));
    }

    @Test
    void execute_WhenMonthlyAndAlreadyGeneratedThisMonth_ShouldSkip() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(2).withDayOfMonth(today.getDayOfMonth());
        LocalDate lastGenerated = today.withDayOfMonth(1);
        RecurringTransaction rt = buildRecurring(PeriodicityType.MONTHLY, startDate, null, lastGenerated);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(processor, never()).process(any(), any());
    }

    @Test
    void execute_WhenMonthlyAndDifferentDayOfMonth_ShouldSkip() {
        LocalDate today = LocalDate.now();
        int otherDay = today.getDayOfMonth() == 1 ? 2 : 1;
        LocalDate startDate = today.withDayOfMonth(otherDay).minusMonths(1);
        RecurringTransaction rt = buildRecurring(PeriodicityType.MONTHLY, startDate, null, null);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(processor, never()).process(any(), any());
    }

    @Test
    void execute_WhenWeeklyAndSameDayOfWeek_ShouldCallProcessor() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusWeeks(3);
        RecurringTransaction rt = buildRecurring(PeriodicityType.WEEKLY, startDate, null, null);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(processor).process(eq(rt.getId()), eq(today));
    }

    @Test
    void execute_WhenYearlyAndSameMonthAndDay_ShouldCallProcessor() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusYears(1);
        RecurringTransaction rt = buildRecurring(PeriodicityType.YEARLY, startDate, null, null);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(processor).process(eq(rt.getId()), eq(today));
    }

    @Test
    void execute_WhenYearlyAndAlreadyGeneratedThisYear_ShouldSkip() {
        LocalDate today = LocalDate.now();
        LocalDate lastGenerated = today.withDayOfYear(1);
        RecurringTransaction rt = buildRecurring(PeriodicityType.YEARLY, today.minusYears(1), null, lastGenerated);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(processor, never()).process(any(), any());
    }

    @Test
    void execute_WhenNoActiveRecurrings_ShouldNotCallProcessor() {
        when(recurringTransactionRepository.findAllActive(any())).thenReturn(List.of());

        job.execute();

        verify(processor, never()).process(any(), any());
    }

    @Test
    void execute_WhenProcessorThrows_ShouldContinueAndNotRethrow() {
        LocalDate today = LocalDate.now();
        RecurringTransaction rt1 = buildRecurring(PeriodicityType.DAILY, today.minusDays(5), null, null);
        rt1.setId(1L);
        RecurringTransaction rt2 = buildRecurring(PeriodicityType.DAILY, today.minusDays(3), null, null);
        rt2.setId(2L);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt1, rt2));
        doThrow(new RuntimeException("DB error")).when(processor).process(eq(1L), any());

        job.execute();

        verify(processor).process(eq(1L), eq(today));
        verify(processor).process(eq(2L), eq(today));
    }

    @Test
    void execute_WhenMultiple_ShouldProcessOnlyEligible() {
        LocalDate today = LocalDate.now();
        RecurringTransaction rt1 = buildRecurring(PeriodicityType.DAILY, today.minusDays(5), null, null);
        rt1.setId(1L);
        RecurringTransaction rt2 = buildRecurring(PeriodicityType.DAILY, today.minusDays(3), null, today);
        rt2.setId(2L);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt1, rt2));

        job.execute();

        verify(processor, times(1)).process(eq(1L), eq(today));
        verify(processor, never()).process(eq(2L), any());
    }

    private RecurringTransaction buildRecurring(PeriodicityType type, LocalDate startDate,
                                                LocalDate endDate, LocalDate lastGeneratedDate) {
        RecurrencePeriodicity periodicity = new RecurrencePeriodicity();
        periodicity.setId(1L);
        periodicity.setType(type);

        RecurringTransaction rt = RecurringTransaction.builder()
                .pocket(pocket)
                .description("Recorrente")
                .amount(new BigDecimal("200.00"))
                .direction(io.github.ronaldobertolucci.unita.model.finance.Direction.EXPENSE)
                .periodicity(periodicity)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        rt.setId(1L);
        rt.setLastGeneratedDate(lastGeneratedDate);
        return rt;
    }
}