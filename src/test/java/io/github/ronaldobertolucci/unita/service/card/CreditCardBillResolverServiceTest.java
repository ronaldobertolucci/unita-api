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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        // closingDay=10, dueDay=5
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
    void findOrCreateForDate_WhenBillExistsForPeriod_ShouldReturnExistingBillWithoutSaving() {
        LocalDate installmentDate = LocalDate.of(2025, 1, 5);
        CreditCardBill existingBill = buildBill(1L, LocalDate.of(2024, 12, 10),
                LocalDate.of(2025, 1, 10), CreditCardBillStatus.OPEN, 10, 5);

        when(creditCardBillRepository.findByPeriod(1L, installmentDate))
                .thenReturn(Optional.of(existingBill));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, installmentDate);

        assertThat(result).isEqualTo(existingBill);
        verify(creditCardBillRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // createInitialBill
    // -------------------------------------------------------------------------

    @Test
    void createInitialBill_WhenReferenceBeforeClosingDay_ShouldCreateBillClosingInSameMonth() {
        // referenceDate = 05/Jan, closingDay=10 → closingDate = 10/Jan
        // periodStart = 10/Dez (closingDate - 1 mês com mesmo closingDay)
        LocalDate referenceDate = LocalDate.of(2025, 1, 5);
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.createInitialBill(creditCard, referenceDate);

        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 1, 10));
        assertThat(result.getPeriodStart()).isEqualTo(LocalDate.of(2024, 12, 10));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 2, 5));
        assertThat(result.getClosingDay()).isEqualTo(10);
        assertThat(result.getDueDay()).isEqualTo(5);
        assertThat(result.getStatus()).isEqualTo(CreditCardBillStatus.OPEN);
        verify(creditCardBillRepository).save(any(CreditCardBill.class));
    }

    @Test
    void createInitialBill_WhenReferenceOnClosingDay_ShouldCreateBillClosingInNextMonth() {
        // referenceDate = 10/Jan (mesmo dia do fechamento) → avança para Fev
        LocalDate referenceDate = LocalDate.of(2025, 1, 10);
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.createInitialBill(creditCard, referenceDate);

        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 2, 10));
        assertThat(result.getPeriodStart()).isEqualTo(LocalDate.of(2025, 1, 10));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 3, 5));
    }

    @Test
    void createInitialBill_WhenReferenceAfterClosingDay_ShouldCreateBillClosingInNextMonth() {
        // referenceDate = 15/Jan, closingDay=10 → avança para Fev
        LocalDate referenceDate = LocalDate.of(2025, 1, 15);
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.createInitialBill(creditCard, referenceDate);

        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 2, 10));
        assertThat(result.getPeriodStart()).isEqualTo(LocalDate.of(2025, 1, 10));
    }

    @Test
    void createInitialBill_WhenClosingDayExceedsMonthLength_ShouldClampToLastDay() {
        // closingDay=31, referenceDate=05/Fev → closingDate=28/Fev
        CreditCard card31 = CreditCard.builder().id(2L).closingDay(31).dueDay(5).build();
        LocalDate referenceDate = LocalDate.of(2025, 2, 5);
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.createInitialBill(card31, referenceDate);

        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 3, 5));
    }

    @Test
    void createInitialBill_WhenDueDayGreaterThanClosingDay_ShouldSetDueDateInSameMonth() {
        // closingDay=10, dueDay=15 → dueDay > closingDay → vencimento no mesmo mês
        CreditCard cardDue15 = CreditCard.builder().id(3L).closingDay(10).dueDay(15).build();
        LocalDate referenceDate = LocalDate.of(2025, 1, 5);
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.createInitialBill(cardDue15, referenceDate);

        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 1, 10));
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 1, 15));
    }

    // -------------------------------------------------------------------------
    // findOrCreateForDate — criação encadeada para frente (futuro)
    // -------------------------------------------------------------------------

    @Test
    void findOrCreateForDate_WhenNoBillForPeriod_AndLatestBeforeExists_ShouldCreateChainForward() {
        // Existe fatura de Jan (closingDate=10/Jan), compra em Mar → cria Fev e Mar
        LocalDate installmentDate = LocalDate.of(2025, 3, 5);
        CreditCardBill janBill = buildBill(1L, LocalDate.of(2024, 12, 10),
                LocalDate.of(2025, 1, 10), CreditCardBillStatus.PAID, 10, 5);

        when(creditCardBillRepository.findByPeriod(1L, installmentDate)).thenReturn(Optional.empty());
        when(creditCardBillRepository.findLatestBeforeDate(1L, installmentDate)).thenReturn(Optional.of(janBill));
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, installmentDate);

        // Deve ter criado fatura de Mar (periodStart=10/Fev, closingDate=10/Mar)
        assertThat(result.getClosingDate()).isEqualTo(LocalDate.of(2025, 3, 10));
        assertThat(result.getPeriodStart()).isEqualTo(LocalDate.of(2025, 2, 10));
        // Salva pelo menos 2 faturas (Fev e Mar)
        verify(creditCardBillRepository, atLeast(2)).save(any(CreditCardBill.class));
    }

    @Test
    void findOrCreateForDate_WhenNoBillForPeriod_AndNoLatestBefore_AndLatestExists_ShouldCreateChainBackward() {
        // Não existe fatura anterior à compra, mas existe fatura futura → cria para o passado
        LocalDate installmentDate = LocalDate.of(2025, 1, 5);
        CreditCardBill futureBill = buildBill(1L, LocalDate.of(2025, 2, 10),
                LocalDate.of(2025, 3, 10), CreditCardBillStatus.OPEN, 10, 5);

        when(creditCardBillRepository.findByPeriod(1L, installmentDate)).thenReturn(Optional.empty());
        when(creditCardBillRepository.findLatestBeforeDate(1L, installmentDate)).thenReturn(Optional.empty());
        when(creditCardBillRepository.findLatestByCreditCardId(1L)).thenReturn(Optional.of(futureBill));
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, installmentDate);

        // Deve ter criado fatura que contém 05/Jan
        assertThat(result.getPeriodStart()).isBeforeOrEqualTo(installmentDate);
        assertThat(result.getClosingDate()).isAfter(installmentDate);
        verify(creditCardBillRepository, atLeast(1)).save(any(CreditCardBill.class));
    }

    @Test
    void findOrCreateForDate_WhenNoBillsAtAll_ShouldCreateInitialBillAndChain() {
        // Nenhuma fatura existe ainda
        LocalDate installmentDate = LocalDate.of(2025, 1, 5);

        when(creditCardBillRepository.findByPeriod(1L, installmentDate)).thenReturn(Optional.empty());
        when(creditCardBillRepository.findLatestBeforeDate(1L, installmentDate)).thenReturn(Optional.empty());
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, installmentDate);

        assertThat(result.getPeriodStart()).isBeforeOrEqualTo(installmentDate);
        assertThat(result.getClosingDate()).isAfter(installmentDate);
        verify(creditCardBillRepository, atLeast(1)).save(any(CreditCardBill.class));
    }

    @Test
    void findOrCreateForDate_WhenChainForward_ShouldUseCurrentCardDaysForFutureBills() {
        // Fatura passada com closingDay=10, cartão agora com closingDay=10 também
        // Compra no futuro → novas faturas usam dias do cartão
        LocalDate today = LocalDate.now();
        LocalDate installmentDate = today.plusMonths(2).withDayOfMonth(5);

        CreditCardBill pastBill = buildBill(1L,
                today.minusMonths(1).withDayOfMonth(10),
                today.withDayOfMonth(10),
                CreditCardBillStatus.CLOSED, 10, 5);

        when(creditCardBillRepository.findByPeriod(1L, installmentDate)).thenReturn(Optional.empty());
        when(creditCardBillRepository.findLatestBeforeDate(1L, installmentDate)).thenReturn(Optional.of(pastBill));
        when(creditCardBillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditCardBill result = creditCardBillResolverService.findOrCreateForDate(creditCard, installmentDate);

        assertThat(result.getClosingDay()).isEqualTo(creditCard.getClosingDay());
        assertThat(result.getDueDay()).isEqualTo(creditCard.getDueDay());
    }

    // -------------------------------------------------------------------------
    // CreditCardService — createCreditCard cria fatura inicial
    // -------------------------------------------------------------------------
    // (testado indiretamente via CreditCardServiceTest)

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private CreditCardBill buildBill(Long id, LocalDate periodStart, LocalDate closingDate,
                                     CreditCardBillStatus status, int closingDay, int dueDay) {
        CreditCardBill bill = CreditCardBill.builder()
                .creditCard(creditCard)
                .periodStart(periodStart)
                .closingDate(closingDate)
                .dueDate(closingDate.plusMonths(1))
                .closingDay(closingDay)
                .dueDay(dueDay)
                .status(status)
                .build();
        bill.setId(id);
        return bill;
    }
}