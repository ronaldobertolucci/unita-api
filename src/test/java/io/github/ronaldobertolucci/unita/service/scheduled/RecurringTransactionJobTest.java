package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.finance.PeriodicityType;
import io.github.ronaldobertolucci.unita.model.finance.RecurrencePeriodicity;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.pocket.RecurringTransaction;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import io.github.ronaldobertolucci.unita.repository.RecurringTransactionRepository;
import io.github.ronaldobertolucci.unita.repository.TransactionRepository;
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
class RecurringTransactionJobTest {

    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private RecurringTransactionJob job;

    private Cash pocket;

    @BeforeEach
    void setUp() {
        pocket = new Cash();
        pocket.setId(1L);
    }

    @Test
    void execute_WhenDailyAndNeverGenerated_ShouldGenerateTransaction() {
        LocalDate today = LocalDate.now();
        RecurringTransaction rt = buildRecurring(PeriodicityType.DAILY, today.minusDays(5), null, null);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(transactionRepository).save(any(Transaction.class));
        verify(recurringTransactionRepository).save(rt);
        assertEquals(today, rt.getLastGeneratedDate());
    }

    @Test
    void execute_WhenDailyAndAlreadyGeneratedToday_ShouldSkip() {
        LocalDate today = LocalDate.now();
        RecurringTransaction rt = buildRecurring(PeriodicityType.DAILY, today.minusDays(5), null, today);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void execute_WhenMonthlyAndSameDayOfMonth_ShouldGenerate() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(2).withDayOfMonth(today.getDayOfMonth());
        RecurringTransaction rt = buildRecurring(PeriodicityType.MONTHLY, startDate, null, null);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void execute_WhenMonthlyAndAlreadyGeneratedThisMonth_ShouldSkip() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(2).withDayOfMonth(today.getDayOfMonth());
        LocalDate lastGenerated = today.withDayOfMonth(1); // mesmo mês
        RecurringTransaction rt = buildRecurring(PeriodicityType.MONTHLY, startDate, null, lastGenerated);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void execute_WhenMonthlyAndDifferentDayOfMonth_ShouldSkip() {
        LocalDate today = LocalDate.now();
        int otherDay = today.getDayOfMonth() == 1 ? 2 : 1;
        LocalDate startDate = today.withDayOfMonth(otherDay).minusMonths(1);
        RecurringTransaction rt = buildRecurring(PeriodicityType.MONTHLY, startDate, null, null);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void execute_WhenWeeklyAndSameDayOfWeek_ShouldGenerate() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusWeeks(3); // mesmo dia da semana
        RecurringTransaction rt = buildRecurring(PeriodicityType.WEEKLY, startDate, null, null);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void execute_WhenYearlyAndSameMonthAndDay_ShouldGenerate() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusYears(1);
        RecurringTransaction rt = buildRecurring(PeriodicityType.YEARLY, startDate, null, null);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void execute_WhenYearlyAndAlreadyGeneratedThisYear_ShouldSkip() {
        LocalDate today = LocalDate.now();
        LocalDate lastGenerated = today.withDayOfYear(1); // mesmo ano
        RecurringTransaction rt = buildRecurring(PeriodicityType.YEARLY, today.minusYears(1), null, lastGenerated);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void execute_ShouldGenerateTransactionWithCorrectFields() {
        LocalDate today = LocalDate.now();
        RecurringTransaction rt = buildRecurring(PeriodicityType.DAILY, today.minusDays(1), null, null);

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt));

        job.execute();

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());

        Transaction saved = captor.getValue();
        assertEquals(pocket, saved.getPocket());
        assertEquals(new BigDecimal("200.00"), saved.getAmount());
        assertEquals(Direction.EXPENSE, saved.getDirection());
        assertEquals(today, saved.getTransactionDate());
        assertEquals("Recorrente", saved.getDescription());
    }

    @Test
    void execute_WhenNoActiveRecurrings_ShouldDoNothing() {
        when(recurringTransactionRepository.findAllActive(any())).thenReturn(List.of());

        job.execute();

        verify(transactionRepository, never()).save(any());
        verify(recurringTransactionRepository, never()).save(any());
    }

    @Test
    void execute_WhenMultipleRecurrings_ShouldProcessEachIndependently() {
        LocalDate today = LocalDate.now();
        RecurringTransaction rt1 = buildRecurring(PeriodicityType.DAILY, today.minusDays(5), null, null);
        RecurringTransaction rt2 = buildRecurring(PeriodicityType.DAILY, today.minusDays(3), null, today); // já gerado

        when(recurringTransactionRepository.findAllActive(today)).thenReturn(List.of(rt1, rt2));

        job.execute();

        verify(transactionRepository, times(1)).save(any(Transaction.class));
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
                .direction(Direction.EXPENSE)
                .periodicity(periodicity)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        rt.setId(1L);
        rt.setLastGeneratedDate(lastGeneratedDate);
        return rt;
    }
}