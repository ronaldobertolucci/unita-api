package io.github.ronaldobertolucci.unita.service.card;

import io.github.ronaldobertolucci.unita.model.card.CreditCard;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBill;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import io.github.ronaldobertolucci.unita.repository.CreditCardBillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditCardBillResolverServiceTest {

    @Mock
    private CreditCardBillRepository creditCardBillRepository;

    @InjectMocks
    private CreditCardBillResolverService creditCardBillResolverService;

    private CreditCard creditCard;

    @BeforeEach
    void setUp() {
        // closingDay=10, dueDay=5 — padrão usado na maioria dos testes
        creditCard = CreditCard.builder()
                .id(1L)
                .closingDay(10)
                .dueDay(5)
                .build();
    }

    // -------------------------------------------------------------------------
    // findOrCreateForDate — bill existente
    // -------------------------------------------------------------------------

    @Test
    void findOrCreateForDate_WhenBillAlreadyExists_ShouldReturnExistingBillWithoutSaving() {
        LocalDate purchaseDate = LocalDate.of(2025, 1, 5);
        CreditCardBill existingBill = CreditCardBill.builder()
                .id(1L)
                .creditCard(creditCard)
                .closingDate(LocalDate.of(2025, 1, 10))
                .dueDate(LocalDate.of(2025, 2, 5))
                .status(CreditCardBillStatus.OPEN)
                .build();
        when(creditCardBillRepository.findFirstByCreditCardIdAndClosingDateAfterPurchaseDate(
                eq(1L), eq(purchaseDate), any(PageRequest.class)))
                .thenReturn(List.of(existingBill));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, purchaseDate);

        assertThat(result).isEqualTo(existingBill);
        assertThat(result.getId()).isEqualTo(1L);
        verify(creditCardBillRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // findOrCreateForDate — criação de nova bill
    // -------------------------------------------------------------------------

    @Test
    void findOrCreateForDate_WhenNoBillExists_AndPurchaseBeforeClosingDay_ShouldCreateBillClosingInSameMonth() {
        // Compra dia 05/Jan, fechamento dia 10 → fecha em Jan, vence em Fev
        LocalDate purchaseDate = LocalDate.of(2025, 1, 5);
        when(creditCardBillRepository.findFirstByCreditCardIdAndClosingDateAfterPurchaseDate(
                any(), any(), any())).thenReturn(List.of());
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, purchaseDate);

        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 1, 10));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 2, 5));
        assertThat(result.getStatus()).isEqualTo(CreditCardBillStatus.OPEN);
        assertThat(result.getCreditCard()).isEqualTo(creditCard);
        verify(creditCardBillRepository).save(any(CreditCardBill.class));
    }

    @Test
    void findOrCreateForDate_WhenNoBillExists_AndPurchaseOnClosingDay_ShouldCreateBillClosingInNextMonth() {
        // Compra no próprio dia de fechamento (10/Jan) → deve empurrar para o mês seguinte
        LocalDate purchaseDate = LocalDate.of(2025, 1, 10);
        when(creditCardBillRepository.findFirstByCreditCardIdAndClosingDateAfterPurchaseDate(
                any(), any(), any())).thenReturn(List.of());
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, purchaseDate);

        // closingDate inicial = Jan 10 → NOT isAfter Jan 10 → avança para Fev
        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 2, 10));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 3, 5));
    }

    @Test
    void findOrCreateForDate_WhenNoBillExists_AndPurchaseAfterClosingDay_ShouldCreateBillClosingInNextMonth() {
        // Compra dia 15/Jan, fechamento dia 10 → closingDate inicial não é depois da compra → avança
        LocalDate purchaseDate = LocalDate.of(2025, 1, 15);
        when(creditCardBillRepository.findFirstByCreditCardIdAndClosingDateAfterPurchaseDate(
                any(), any(), any())).thenReturn(List.of());
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, purchaseDate);

        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 2, 10));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 3, 5));
    }

    @Test
    void findOrCreateForDate_WhenClosingDayExceedsFebruaryLength_ShouldClampClosingDateToLastDay() {
        // closingDay=31 em mês curto: fevereiro tem 28 dias → deve usar dia 28
        CreditCard cardWith31 = CreditCard.builder()
                .id(2L)
                .closingDay(31)
                .dueDay(5)
                .build();
        LocalDate purchaseDate = LocalDate.of(2025, 2, 5);
        when(creditCardBillRepository.findFirstByCreditCardIdAndClosingDateAfterPurchaseDate(
                any(), any(), any())).thenReturn(List.of());
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(cardWith31, purchaseDate);

        // closingDate = Fev 28 (min(31, 28)) → isAfter Fev 5 ✓
        // dueDateMonth = Mar 28 (Fev 28 + 1 mês)
        // dueDate = Mar 05 (min(5, 31))
        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 3, 5));
    }

    @Test
    void findOrCreateForDate_WhenDueDayExceedsFebruaryLength_ShouldClampDueDateToLastDay() {
        // dueDay=31 e dueDateMonth cai em fevereiro → deve usar dia 28
        CreditCard cardWithDue31 = CreditCard.builder()
                .id(3L)
                .closingDay(31)
                .dueDay(31)
                .build();
        LocalDate purchaseDate = LocalDate.of(2025, 1, 5);
        when(creditCardBillRepository.findFirstByCreditCardIdAndClosingDateAfterPurchaseDate(
                any(), any(), any())).thenReturn(List.of());
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(cardWithDue31, purchaseDate);

        // closingDate = Jan 31 (min(31,31)) → isAfter Jan 5 ✓
        // dueDateMonth = Jan 31 + 1 mês = Fev 28
        // dueDate = Fev 28 (min(31, 28))
        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 1, 31));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 2, 28));
    }

    @Test
    void findOrCreateForDate_WhenDueDayIsGreaterThanClosingDay_ShouldSetDueDateInSameMonthAsClosing() {
        // closingDay=10, dueDay=15 → dueDay > closingDay → vencimento no mesmo mês do fechamento
        CreditCard cardWithDueDayAfterClosing = CreditCard.builder()
                .id(4L)
                .closingDay(10)
                .dueDay(15)
                .build();
        LocalDate purchaseDate = LocalDate.of(2025, 1, 5);
        when(creditCardBillRepository.findFirstByCreditCardIdAndClosingDateAfterPurchaseDate(
                any(), any(), any())).thenReturn(List.of());
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(cardWithDueDayAfterClosing, purchaseDate);

        // closingDate = Jan 10 (isAfter Jan 5 ✓)
        // dueDay(15) > closingDay(10) → mesmo mês → Jan 15
        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 1, 10));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 1, 15));
    }

    // -------------------------------------------------------------------------
    // findOrCreateForDate com limitDate
    // -------------------------------------------------------------------------

    @Test
    void findOrCreateForDate_WithLimitDate_WhenBillExistsInWindow_ShouldReturnExistingBillWithoutSaving() {
        LocalDate purchaseDate = LocalDate.of(2025, 1, 5);
        LocalDate limitDate = LocalDate.of(2025, 3, 1);
        CreditCardBill existingBill = CreditCardBill.builder()
                .id(1L)
                .creditCard(creditCard)
                .closingDate(LocalDate.of(2025, 1, 10))
                .dueDate(LocalDate.of(2025, 2, 5))
                .status(CreditCardBillStatus.OPEN)
                .build();
        when(creditCardBillRepository.findBillForPurchaseDate(
                eq(1L), eq(purchaseDate), eq(limitDate), any(PageRequest.class)))
                .thenReturn(List.of(existingBill));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, purchaseDate, limitDate);

        assertThat(result).isEqualTo(existingBill);
        assertThat(result.getId()).isEqualTo(1L);
        verify(creditCardBillRepository, never()).save(any());
    }

    @Test
    void findOrCreateForDate_WithLimitDate_WhenNoBillInWindow_ShouldCreateBill() {
        // Compra dia 05/Jan, limitDate 01/Mar → cria fatura fechando em Jan
        LocalDate purchaseDate = LocalDate.of(2025, 1, 5);
        LocalDate limitDate = LocalDate.of(2025, 3, 1);
        when(creditCardBillRepository.findBillForPurchaseDate(
                any(), any(), any(), any())).thenReturn(List.of());
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, purchaseDate, limitDate);

        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 1, 10));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 2, 5));
        assertThat(result.getStatus()).isEqualTo(CreditCardBillStatus.OPEN);
        verify(creditCardBillRepository).save(any(CreditCardBill.class));
    }

    @Test
    void findOrCreateForDate_WithLimitDate_WhenPurchaseAfterClosingDay_ShouldCreateBillInNextMonth() {
        // Compra dia 15/Jan, fechamento dia 10 → fatura fecha em Fev, fora do limite Mar
        // Mas como não existe fatura, cria com base na purchaseDate
        LocalDate purchaseDate = LocalDate.of(2025, 1, 15);
        LocalDate limitDate = LocalDate.of(2025, 3, 1);
        when(creditCardBillRepository.findBillForPurchaseDate(
                any(), any(), any(), any())).thenReturn(List.of());
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, purchaseDate, limitDate);

        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 2, 10));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 3, 5));
        verify(creditCardBillRepository).save(any(CreditCardBill.class));
    }

    @Test
    void findOrCreateForDate_WithLimitDate_WhenBillExistsOutsideWindow_ShouldCreateNewBill() {
        // Existe fatura em Abr, mas limitDate é Mar → não é encontrada → cria nova
        LocalDate purchaseDate = LocalDate.of(2025, 1, 31);
        LocalDate limitDate = LocalDate.of(2025, 3, 1);
        when(creditCardBillRepository.findBillForPurchaseDate(
                any(), any(), any(), any())).thenReturn(List.of());
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, purchaseDate, limitDate);

        // purchaseDate 31/Jan, closingDay 10 → closingDate inicial Jan 10 → NOT isAfter Jan 31 → avança para Fev 10
        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 2, 10));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 3, 5));
        verify(creditCardBillRepository).save(any(CreditCardBill.class));
    }
}