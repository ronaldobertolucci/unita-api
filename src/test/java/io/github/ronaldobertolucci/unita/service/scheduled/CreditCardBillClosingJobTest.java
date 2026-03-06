package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import io.github.ronaldobertolucci.unita.repository.CreditCardBillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditCardBillClosingJobTest {

    @Mock private CreditCardBillRepository creditCardBillRepository;

    @InjectMocks private CreditCardBillClosingJob job;

    @Test
    void execute_ShouldCallCloseAllOverdueWithCorrectStatuses() {
        when(creditCardBillRepository.closeAllOverdue(any(), any(), any())).thenReturn(3);

        job.execute();

        verify(creditCardBillRepository).closeAllOverdue(
                any(LocalDate.class),
                eq(CreditCardBillStatus.OPEN),
                eq(CreditCardBillStatus.CLOSED));
    }

    @Test
    void execute_WhenNoBillsToClose_ShouldCompleteWithoutError() {
        when(creditCardBillRepository.closeAllOverdue(any(), any(), any())).thenReturn(0);

        job.execute();

        verify(creditCardBillRepository).closeAllOverdue(any(), any(), any());
    }

    @Test
    void execute_ShouldPassTodayAsDate() {
        LocalDate today = LocalDate.now();
        when(creditCardBillRepository.closeAllOverdue(any(), any(), any())).thenReturn(1);

        job.execute();

        verify(creditCardBillRepository).closeAllOverdue(
                eq(today),
                eq(CreditCardBillStatus.OPEN),
                eq(CreditCardBillStatus.CLOSED));
    }

    @Test
    void execute_WhenRepositoryThrows_ShouldRethrowException() {
        when(creditCardBillRepository.closeAllOverdue(any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () -> job.execute());
    }
}