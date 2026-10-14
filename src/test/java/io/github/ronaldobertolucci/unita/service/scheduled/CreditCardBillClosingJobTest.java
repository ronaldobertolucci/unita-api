package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import io.github.ronaldobertolucci.unita.repository.CreditCardBillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditCardBillClosingJobTest {

    @Mock private io.github.ronaldobertolucci.unita.service.finance.FinancialConcurrencyService concurrency;

    @Mock private CreditCardBillRepository creditCardBillRepository;

    @InjectMocks private CreditCardBillClosingJob job;

    @BeforeEach void lockedCards() { when(concurrency.lockAllCards()).thenReturn(List.of(1L, 2L)); }

    @Test
    void execute_ShouldCallCloseAllOverdueWithCorrectStatuses() {
        when(creditCardBillRepository.closeOverdueForLockedCards(any(), any(), any(), eq(List.of(1L, 2L)))).thenReturn(3);

        job.execute();

        verify(creditCardBillRepository).closeOverdueForLockedCards(
                any(LocalDate.class),
                eq(CreditCardBillStatus.OPEN),
                eq(CreditCardBillStatus.CLOSED), eq(List.of(1L, 2L)));
    }

    @Test
    void execute_WhenNoBillsToClose_ShouldCompleteWithoutError() {
        when(creditCardBillRepository.closeOverdueForLockedCards(any(), any(), any(), eq(List.of(1L, 2L)))).thenReturn(0);

        job.execute();

        verify(creditCardBillRepository).closeOverdueForLockedCards(any(), any(), any(), eq(List.of(1L, 2L)));
    }

    @Test
    void execute_ShouldPassTodayAsDate() {
        LocalDate today = LocalDate.now();
        when(creditCardBillRepository.closeOverdueForLockedCards(any(), any(), any(), eq(List.of(1L, 2L)))).thenReturn(1);

        job.execute();

        verify(creditCardBillRepository).closeOverdueForLockedCards(
                eq(today),
                eq(CreditCardBillStatus.OPEN),
                eq(CreditCardBillStatus.CLOSED), eq(List.of(1L, 2L)));
    }

    @Test
    void execute_WhenRepositoryThrows_ShouldRethrowException() {
        when(creditCardBillRepository.closeOverdueForLockedCards(any(), any(), any(), eq(List.of(1L, 2L))))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> job.execute());
    }
    @Test void execute_WhenNoCards_ShouldSkipBulkUpdate() {
        when(concurrency.lockAllCards()).thenReturn(List.of());
        job.execute();
        verifyNoInteractions(creditCardBillRepository);
    }

}