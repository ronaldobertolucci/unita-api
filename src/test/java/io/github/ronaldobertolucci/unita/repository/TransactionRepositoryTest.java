package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
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
class TransactionRepositoryTest extends BaseRepositoryTest {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private CashRepository cashRepository;

    private Cash pocket;
    private Cash otherPocket;

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
    }

    @Test
    void findAllByPocketId_ShouldReturnOnlyTransactionsOfPocket() {
        saveTransaction(pocket, Direction.INCOME, new BigDecimal("100.00"));
        saveTransaction(pocket, Direction.EXPENSE, new BigDecimal("40.00"));
        saveTransaction(otherPocket, Direction.INCOME, new BigDecimal("200.00"));

        List<Transaction> result = transactionRepository.findAllByPocketId(pocket.getId());

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(t -> t.getPocket().getId().equals(pocket.getId())));
    }

    @Test
    void findAllByPocketId_ShouldReturnOrderedByDateDesc() {
        saveTransaction(pocket, Direction.INCOME, new BigDecimal("100.00"), LocalDate.of(2024, 1, 1));
        saveTransaction(pocket, Direction.INCOME, new BigDecimal("200.00"), LocalDate.of(2024, 3, 1));
        saveTransaction(pocket, Direction.INCOME, new BigDecimal("300.00"), LocalDate.of(2024, 2, 1));

        List<Transaction> result = transactionRepository.findAllByPocketId(pocket.getId());

        assertEquals(LocalDate.of(2024, 3, 1), result.get(0).getTransactionDate());
        assertEquals(LocalDate.of(2024, 2, 1), result.get(1).getTransactionDate());
        assertEquals(LocalDate.of(2024, 1, 1), result.get(2).getTransactionDate());
    }

    @Test
    void findAllByPocketId_WhenNone_ShouldReturnEmpty() {
        assertTrue(transactionRepository.findAllByPocketId(pocket.getId()).isEmpty());
    }

    @Test
    void findByIdAndPocketId_WhenOwner_ShouldReturnTransaction() {
        Transaction saved = saveTransaction(pocket, Direction.INCOME, new BigDecimal("100.00"));

        Optional<Transaction> found = transactionRepository.findByIdAndPocketId(saved.getId(), pocket.getId());

        assertTrue(found.isPresent());
    }

    @Test
    void findByIdAndPocketId_WhenWrongPocket_ShouldReturnEmpty() {
        Transaction saved = saveTransaction(pocket, Direction.INCOME, new BigDecimal("100.00"));

        assertTrue(transactionRepository.findByIdAndPocketId(saved.getId(), otherPocket.getId()).isEmpty());
    }

    @Test
    void calculateBalanceByPocketId_ShouldSumIncomeMinusExpense() {
        saveTransaction(pocket, Direction.INCOME, new BigDecimal("500.00"));
        saveTransaction(pocket, Direction.INCOME, new BigDecimal("300.00"));
        saveTransaction(pocket, Direction.EXPENSE, new BigDecimal("200.00"));

        BigDecimal balance = transactionRepository.calculateBalanceByPocketId(pocket.getId());

        assertEquals(0, new BigDecimal("600.00").compareTo(balance));
    }

    @Test
    void calculateBalanceByPocketId_WhenNoTransactions_ShouldReturnZero() {
        BigDecimal balance = transactionRepository.calculateBalanceByPocketId(pocket.getId());

        assertEquals(0, BigDecimal.ZERO.compareTo(balance));
    }

    @Test
    void calculateBalanceByPocketId_WhenOnlyExpenses_ShouldReturnNegative() {
        saveTransaction(pocket, Direction.EXPENSE, new BigDecimal("150.00"));

        BigDecimal balance = transactionRepository.calculateBalanceByPocketId(pocket.getId());

        assertEquals(0, new BigDecimal("-150.00").compareTo(balance));
    }

    private Transaction saveTransaction(Cash pocket, Direction direction, BigDecimal amount) {
        return saveTransaction(pocket, direction, amount, LocalDate.now());
    }

    private Transaction saveTransaction(Cash pocket, Direction direction, BigDecimal amount, LocalDate date) {
        return transactionRepository.save(Transaction.builder()
                .pocket(pocket)
                .amount(amount)
                .direction(direction)
                .transactionDate(date)
                .description("Transação teste")
                .build());
    }
}