package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.finance.Category;
import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.finance.PeriodicityType;
import io.github.ronaldobertolucci.unita.model.finance.RecurrencePeriodicity;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.pocket.RecurringTransaction;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import io.github.ronaldobertolucci.unita.repository.RecurringTransactionRepository;
import io.github.ronaldobertolucci.unita.repository.TransactionRepository;
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
class RecurringTransactionJobProcessorTest {

    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private RecurringTransactionJobProcessor processor;

    private Cash pocket;
    private Category category;
    private RecurringTransaction recurringTransaction;

    @BeforeEach
    void setUp() {
        pocket = new Cash();
        pocket.setId(1L);

        category = new Category();
        category.setId(5L);

        RecurrencePeriodicity periodicity = new RecurrencePeriodicity();
        periodicity.setId(1L);
        periodicity.setType(PeriodicityType.DAILY);

        recurringTransaction = RecurringTransaction.builder()
                .pocket(pocket)
                .description("Recorrente")
                .amount(new BigDecimal("200.00"))
                .direction(Direction.EXPENSE)
                .periodicity(periodicity)
                .startDate(LocalDate.now().minusDays(5))
                .category(category)
                .build();
        recurringTransaction.setId(1L);
    }

    @Test
    void process_WhenRecurringTransactionExists_ShouldCreateTransactionWithCorrectFields() {
        LocalDate today = LocalDate.now();
        when(recurringTransactionRepository.findById(1L)).thenReturn(Optional.of(recurringTransaction));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        processor.process(1L, today);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());

        Transaction saved = captor.getValue();
        assertEquals(pocket, saved.getPocket());
        assertEquals(new BigDecimal("200.00"), saved.getAmount());
        assertEquals(Direction.EXPENSE, saved.getDirection());
        assertEquals(today, saved.getTransactionDate());
        assertEquals("Recorrente", saved.getDescription());
        assertEquals(category, saved.getCategory());
    }

    @Test
    void process_WhenRecurringTransactionExists_ShouldUpdateLastGeneratedDate() {
        LocalDate today = LocalDate.now();
        when(recurringTransactionRepository.findById(1L)).thenReturn(Optional.of(recurringTransaction));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        processor.process(1L, today);

        assertEquals(today, recurringTransaction.getLastGeneratedDate());
        verify(recurringTransactionRepository).save(recurringTransaction);
    }

    @Test
    void process_WhenRecurringTransactionNotFound_ShouldThrowEntityNotFoundException() {
        when(recurringTransactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> processor.process(99L, LocalDate.now()));

        verify(transactionRepository, never()).save(any());
        verify(recurringTransactionRepository, never()).save(any());
    }
}