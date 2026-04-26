package io.github.ronaldobertolucci.unita.service.pocket;


import io.github.ronaldobertolucci.unita.dto.pocket.*;
import io.github.ronaldobertolucci.unita.model.finance.*;
import io.github.ronaldobertolucci.unita.model.pocket.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.category.CategoryService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PocketServiceTest {

    @Mock
    private PocketRepository pocketRepository;
    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private CashRepository cashRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private LegalEntityRepository legalEntityRepository;
    @Mock
    private BankAccountTypeRepository bankAccountTypeRepository;
    @Mock
    private CategoryService categoryService;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private PocketService pocketService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setEmail("user@test.com");
        when(authentication.getPrincipal()).thenReturn(currentUser);
    }

    // -------------------------------------------------------------------------
    // BankAccount
    // -------------------------------------------------------------------------

    @Test
    void createBankAccount_WhenValid_ShouldPersistAndReturnDto() {
        BankAccountCreateDto dto = new BankAccountCreateDto(10L, "20", "12345-6", 1L);
        LegalEntity le = buildLegalEntity(10L);
        BankAccountType type = buildBankAccountType(1L, "Corrente");
        BankAccount saved = BankAccount.builder()
                .user(currentUser).legalEntity(le).number("12345-6")
                .agency("0001").bankAccountType(type).status(BankAccountStatus.ACTIVE).build();
        saved.setId(1L);

        when(legalEntityRepository.findById(10L)).thenReturn(Optional.of(le));
        when(bankAccountTypeRepository.findById(1L)).thenReturn(Optional.of(type));
        when(bankAccountRepository.save(any())).thenReturn(saved);

        BankAccountDto result = pocketService.createBankAccount(dto, authentication);

        assertNotNull(result);
        verify(bankAccountRepository).save(any(BankAccount.class));
    }

    @Test
    void createBankAccount_WhenLegalEntityNotFound_ShouldThrow() {
        BankAccountCreateDto dto = new BankAccountCreateDto(99L, "20", "12345-6", 1L);
        when(legalEntityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> pocketService.createBankAccount(dto, authentication));
        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    void createBankAccount_WhenBankAccountTypeNotFound_ShouldThrow() {
        BankAccountCreateDto dto = new BankAccountCreateDto(10L, "99", "12345-6", 1L);
        when(legalEntityRepository.findById(10L)).thenReturn(Optional.of(buildLegalEntity(10L)));
        when(bankAccountTypeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> pocketService.createBankAccount(dto, authentication));
    }

    @Test
    void findBankAccountById_WhenOwner_ShouldReturnDto() {
        BankAccount account = buildBankAccount(1L);
        when(bankAccountRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(account));

        BankAccountDto result = pocketService.findBankAccountById(1L, authentication);

        assertNotNull(result);
    }

    @Test
    void findBankAccountById_WhenNotFound_ShouldThrow() {
        when(bankAccountRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> pocketService.findBankAccountById(99L, authentication));
    }

    @Test
    void updateBankAccount_WhenOwner_ShouldUpdateStatus() {
        BankAccount account = buildBankAccount(1L);
        BankAccountUpdateDto dto = new BankAccountUpdateDto(BankAccountStatus.INACTIVE);
        when(bankAccountRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(account));
        when(bankAccountRepository.save(any())).thenReturn(account);
        when(pocketRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);
        when(transactionRepository.calculateBalanceByPocketId(1L)).thenReturn(BigDecimal.ZERO);

        BankAccountDto result = pocketService.updateBankAccount(1L, dto, authentication);

        assertNotNull(result);
        verify(bankAccountRepository).save(account);
    }

    @Test
    void updateBankAccount_WhenNotFound_ShouldThrow() {
        when(bankAccountRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> pocketService.updateBankAccount(99L, new BankAccountUpdateDto(BankAccountStatus.INACTIVE), authentication));
    }

    @Test
    void deleteBankAccount_WhenOwner_ShouldDelete() {
        when(bankAccountRepository.existsByIdAndUserId(1L, currentUser.getId())).thenReturn(true);

        pocketService.deleteBankAccount(1L, authentication);

        verify(bankAccountRepository).deleteById(1L);
    }

    @Test
    void deleteBankAccount_WhenNotOwner_ShouldThrow() {
        when(bankAccountRepository.existsByIdAndUserId(99L, currentUser.getId())).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> pocketService.deleteBankAccount(99L, authentication));
        verify(bankAccountRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------
    // Cash
    // -------------------------------------------------------------------------

    @Test
    void createCash_WhenNoneExists_ShouldPersistAndReturnDto() {
        when(cashRepository.existsByUserId(currentUser.getId())).thenReturn(false);
        Cash saved = new Cash();
        saved.setId(1L);
        saved.setUser(currentUser);
        when(cashRepository.save(any())).thenReturn(saved);

        CashDto result = pocketService.createCash(authentication);

        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.balance());
        verify(cashRepository).save(any(Cash.class));
    }

    @Test
    void createCash_WhenAlreadyExists_ShouldThrowIllegalStateException() {
        when(cashRepository.existsByUserId(currentUser.getId())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> pocketService.createCash(authentication));
        verify(cashRepository, never()).save(any());
    }

    @Test
    void findCash_WhenExists_ShouldReturnDtoWithBalance() {
        Cash cash = new Cash();
        cash.setId(1L);
        cash.setUser(currentUser);
        when(cashRepository.findByUserId(currentUser.getId())).thenReturn(Optional.of(cash));
        when(transactionRepository.calculateBalanceByPocketId(1L)).thenReturn(new BigDecimal("350.00"));

        CashDto result = pocketService.findCash(authentication);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("350.00").compareTo(result.balance()));
    }

    @Test
    void findCash_WhenNotFound_ShouldThrow() {
        when(cashRepository.findByUserId(currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> pocketService.findCash(authentication));
    }

    // -------------------------------------------------------------------------
    // Transaction
    // -------------------------------------------------------------------------

    @Test
    void createTransaction_WhenPocketOwned_ShouldPersistAndReturnDto() {
        Cash pocket = buildCash(5L);
        TransactionCreateDto dto = new TransactionCreateDto(
                new BigDecimal("100.00"), Direction.INCOME, LocalDate.now(), "Salário", 1L);

        when(categoryService.resolveCategory(eq(1L), any(), any()))
                .thenReturn(buildCategory(1L, CategoryType.INCOME));
        when(pocketRepository.findByIdAndUserId(5L, currentUser.getId())).thenReturn(Optional.of(pocket));

        Category neutral = buildCategory(1L, CategoryType.NEUTRAL);
        Transaction saved = Transaction.builder()
                .category(neutral)
                .pocket(pocket).amount(dto.amount()).direction(dto.direction())
                .transactionDate(dto.transactionDate()).description(dto.description()).build();

        saved.setId(1L);
        when(transactionRepository.save(any())).thenReturn(saved);

        TransactionDto result = pocketService.createTransaction(5L, dto, authentication);

        assertNotNull(result);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void createTransaction_WhenCategoryTypeIncompatible_ShouldThrow() {
        when(authentication.getPrincipal()).thenReturn(currentUser);
        Pocket pocket = buildCash(1L);
        when(pocketRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(pocket));
        when(categoryService.resolveCategory(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("Category type INCOME is not allowed in this context"));

        TransactionCreateDto dto = new TransactionCreateDto(
                new BigDecimal("100.00"), Direction.EXPENSE, LocalDate.now(), "Compra", 1L);

        assertThrows(IllegalArgumentException.class,
                () -> pocketService.createTransaction(1L, dto, authentication));
    }

    @Test
    void createTransaction_WhenPocketNotFound_ShouldThrow() {
        when(pocketRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> pocketService.createTransaction(99L, new TransactionCreateDto(
                        BigDecimal.TEN, Direction.INCOME, LocalDate.now(), "Desc", 1L), authentication));
    }

    @Test
    void findBalance_WhenPocketOwned_ShouldReturnBalance() {
        when(pocketRepository.existsByIdAndUserId(5L, currentUser.getId())).thenReturn(true);
        when(transactionRepository.calculateBalanceByPocketId(5L)).thenReturn(new BigDecimal("500.00"));

        BigDecimal result = pocketService.findBalance(5L, authentication);

        assertEquals(0, new BigDecimal("500.00").compareTo(result));
    }

    @Test
    void findBalance_WhenPocketNotOwned_ShouldThrow() {
        when(pocketRepository.existsByIdAndUserId(99L, currentUser.getId())).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> pocketService.findBalance(99L, authentication));
    }

    @Test
    void deleteTransaction_WhenOwned_ShouldDelete() {
        Cash pocket = buildCash(5L);
        Transaction tx = Transaction.builder().pocket(pocket).build();
        tx.setId(10L);

        when(pocketRepository.existsByIdAndUserId(5L, currentUser.getId())).thenReturn(true);
        when(transactionRepository.findByIdAndPocketId(10L, 5L)).thenReturn(Optional.of(tx));

        pocketService.deleteTransaction(5L, 10L, authentication);

        verify(transactionRepository).delete(tx);
    }

    @Test
    void deleteTransaction_WhenPocketNotOwned_ShouldThrow() {
        when(pocketRepository.existsByIdAndUserId(99L, currentUser.getId())).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> pocketService.deleteTransaction(99L, 1L, authentication));
        verify(transactionRepository, never()).delete(any());
    }

    @Test
    void deleteTransaction_WhenTransactionNotFound_ShouldThrow() {
        when(pocketRepository.existsByIdAndUserId(5L, currentUser.getId())).thenReturn(true);
        when(transactionRepository.findByIdAndPocketId(99L, 5L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> pocketService.deleteTransaction(5L, 99L, authentication));
    }

    @Test
    void findTransactions_WithoutDates_ShouldReturnAll() {
        Cash pocket = buildCash(5L);

        Category neutral = buildCategory(1L, CategoryType.NEUTRAL);
        Transaction tx = Transaction.builder()
                .category(neutral)
                .pocket(pocket).amount(new BigDecimal("100.00")).direction(Direction.INCOME)
                .transactionDate(LocalDate.of(2025, 1, 10)).description("Salário").build();
        tx.setId(1L);

        when(pocketRepository.existsByIdAndUserId(5L, currentUser.getId())).thenReturn(true);
        when(transactionRepository.findAllByPocketIdAndPeriod(5L, null, null)).thenReturn(List.of(tx));

        List<TransactionDto> result = pocketService.findTransactions(5L, null, null, authentication);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Salário", result.get(0).description());
        verify(transactionRepository).findAllByPocketIdAndPeriod(5L, null, null);
    }

    @Test
    void findTransactions_WithStartAndEndDate_ShouldReturnFiltered() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);
        Cash pocket = buildCash(5L);

        Category neutral = buildCategory(1L, CategoryType.NEUTRAL);
        Transaction tx = Transaction.builder()
                .category(neutral)
                .pocket(pocket).amount(new BigDecimal("100.00")).direction(Direction.INCOME)
                .transactionDate(LocalDate.of(2025, 1, 10)).description("Salário").build();
        tx.setId(1L);

        when(pocketRepository.existsByIdAndUserId(5L, currentUser.getId())).thenReturn(true);
        when(transactionRepository.findAllByPocketIdAndPeriod(5L, start, end)).thenReturn(List.of(tx));

        List<TransactionDto> result = pocketService.findTransactions(5L, start, end, authentication);

        assertEquals(1, result.size());
        verify(transactionRepository).findAllByPocketIdAndPeriod(5L, start, end);
    }

    @Test
    void findTransactions_WhenStartDateIsAfterEndDate_ShouldThrowIllegalArgumentException() {
        LocalDate start = LocalDate.of(2025, 1, 31);
        LocalDate end = LocalDate.of(2025, 1, 1);

        when(pocketRepository.existsByIdAndUserId(5L, currentUser.getId())).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> pocketService.findTransactions(5L, start, end, authentication));
        verify(transactionRepository, never()).findAllByPocketIdAndPeriod(any(), any(), any());
    }

    @Test
    void findTransactions_WhenPocketNotOwned_ShouldThrow() {
        when(pocketRepository.existsByIdAndUserId(99L, currentUser.getId())).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> pocketService.findTransactions(99L, null, null, authentication));
        verify(transactionRepository, never()).findAllByPocketIdAndPeriod(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private LegalEntity buildLegalEntity(Long id) {
        LegalEntity le = new LegalEntity();
        le.setId(id);
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco Teste");
        return le;
    }

    private BankAccountType buildBankAccountType(Long id, String name) {
        BankAccountType type = new BankAccountType();
        type.setId(id);
        type.setName(name);
        return type;
    }

    private BankAccount buildBankAccount(Long id) {
        BankAccount account = BankAccount.builder()
                .user(currentUser)
                .legalEntity(buildLegalEntity(10L))
                .number("12345-6")
                .agency("0001")
                .bankAccountType(buildBankAccountType(20L, "Corrente"))
                .status(BankAccountStatus.ACTIVE)
                .build();
        account.setId(id);
        return account;
    }

    private Cash buildCash(Long id) {
        Cash cash = new Cash();
        cash.setId(id);
        cash.setUser(currentUser);
        return cash;
    }

    private Category buildCategory(Long id, CategoryType type) {
        Category c = Category.builder()
                .user(null).name("Categoria").type(type).system(false).build();
        c.setId(id);
        return c;
    }
}