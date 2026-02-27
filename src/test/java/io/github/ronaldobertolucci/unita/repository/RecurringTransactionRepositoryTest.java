package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.Category;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.finance.RecurrencePeriodicity;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.pocket.RecurringTransaction;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecurringTransactionRepositoryTest extends BaseRepositoryTest {

    @Autowired private RecurringTransactionRepository recurringTransactionRepository;
    @Autowired private CashRepository cashRepository;
    @Autowired private RecurrencePeriodicityRepository periodicityRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Cash pocket;
    private Cash otherPocket;
    private RecurrencePeriodicity periodicity;
    private Category category;

    @BeforeEach
    void setUp() {
        User user = saveUser("user@test.com");
        User other = saveUser("other@test.com");

        Cash cash = new Cash();
        cash.setUser(user);
        pocket = cashRepository.save(cash);

        Cash otherCash = new Cash();
        otherCash.setUser(other);
        otherPocket = cashRepository.save(otherCash);

        periodicity = periodicityRepository.findAll().stream()
                .filter(p -> p.getName().equals("Mensal"))
                .findFirst()
                .orElseThrow();

        category = categoryRepository.save(Category.builder()
                .user(user)
                .name("Categoria Teste")
                .type(CategoryType.EXPENSE)
                .system(false)
                .build());
    }

    @Test
    void findAllByPocketId_ShouldReturnOnlyRecurringOfPocket() {
        saveRecurring(pocket, LocalDate.now().minusDays(1), null);
        saveRecurring(otherPocket, LocalDate.now().minusDays(1), null);

        List<RecurringTransaction> result = recurringTransactionRepository.findAllByPocketId(pocket.getId());

        assertEquals(1, result.size());
        assertEquals(pocket.getId(), result.get(0).getPocket().getId());
    }

    @Test
    void findAllByPocketId_ShouldReturnOrderedByStartDateAsc() {
        saveRecurring(pocket, LocalDate.of(2024, 3, 1), null);
        saveRecurring(pocket, LocalDate.of(2024, 1, 1), null);
        saveRecurring(pocket, LocalDate.of(2024, 2, 1), null);

        List<RecurringTransaction> result = recurringTransactionRepository.findAllByPocketId(pocket.getId());

        assertEquals(LocalDate.of(2024, 1, 1), result.get(0).getStartDate());
        assertEquals(LocalDate.of(2024, 3, 1), result.get(2).getStartDate());
    }

    @Test
    void findAllByPocketId_ShouldFetchPeriodicity() {
        saveRecurring(pocket, LocalDate.now(), null);

        List<RecurringTransaction> result = recurringTransactionRepository.findAllByPocketId(pocket.getId());

        assertNotNull(result.get(0).getPeriodicity());
    }

    @Test
    void findAllActive_ShouldReturnOnlyActiveRecurrings() {
        LocalDate today = LocalDate.now();
        saveRecurring(pocket, today.minusMonths(1), null);            // ativo (sem fim)
        saveRecurring(pocket, today.minusMonths(2), today.plusDays(5)); // ativo (fim no futuro)
        saveRecurring(pocket, today.minusMonths(3), today.minusDays(1)); // inativo (fim no passado)
        saveRecurring(otherPocket, today.plusDays(1), null);            // inativo (não começou)

        List<RecurringTransaction> active = recurringTransactionRepository.findAllActive(today);

        assertEquals(2, active.size());
    }

    @Test
    void findAllActive_ShouldFetchPocketAndPeriodicity() {
        saveRecurring(pocket, LocalDate.now().minusDays(1), null);

        List<RecurringTransaction> active = recurringTransactionRepository.findAllActive(LocalDate.now());

        assertNotNull(active.get(0).getPocket());
        assertNotNull(active.get(0).getPeriodicity());
    }

    @Test
    void findByIdAndPocketId_WhenMatch_ShouldReturnRecurring() {
        RecurringTransaction saved = saveRecurring(pocket, LocalDate.now(), null);

        Optional<RecurringTransaction> found = recurringTransactionRepository.findByIdAndPocketId(saved.getId(), pocket.getId());

        assertTrue(found.isPresent());
    }

    @Test
    void findByIdAndPocketId_WhenWrongPocket_ShouldReturnEmpty() {
        RecurringTransaction saved = saveRecurring(pocket, LocalDate.now(), null);

        assertTrue(recurringTransactionRepository.findByIdAndPocketId(saved.getId(), otherPocket.getId()).isEmpty());
    }

    private RecurringTransaction saveRecurring(Cash pocket, LocalDate startDate, LocalDate endDate) {
        return recurringTransactionRepository.save(RecurringTransaction.builder()
                .pocket(pocket)
                .description("Recorrente teste")
                .amount(new BigDecimal("100.00"))
                .direction(Direction.EXPENSE)
                .periodicity(periodicity)
                .startDate(startDate)
                .endDate(endDate)
                .category(category)
                .build());
    }
}