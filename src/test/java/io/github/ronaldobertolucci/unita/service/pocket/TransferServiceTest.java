package io.github.ronaldobertolucci.unita.service.pocket;

import io.github.ronaldobertolucci.unita.dto.pocket.TransferCreateDto;
import io.github.ronaldobertolucci.unita.dto.pocket.TransferDto;
import io.github.ronaldobertolucci.unita.model.finance.Category;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.pocket.BankAccount;
import io.github.ronaldobertolucci.unita.model.pocket.BankAccountStatus;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.pocket.FgtsEmployerAccount;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.GroupMembershipRepository;
import io.github.ronaldobertolucci.unita.repository.PocketRepository;
import io.github.ronaldobertolucci.unita.repository.TransactionRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private PocketRepository pocketRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private GroupMembershipRepository groupMembershipRepository;
    @Mock
    private Authentication authentication;
    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private TransferService transferService;

    private User currentUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);

        targetUser = new User();
        targetUser.setId(2L);

        when(authentication.getPrincipal()).thenReturn(currentUser);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private BankAccount buildBankAccount(Long id, User user) {
        LegalEntity le = new LegalEntity();
        le.setId(10L);
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco Teste");
        BankAccount account = BankAccount.builder()
                .user(user).legalEntity(le).number("12345-6")
                .agency("0001").status(BankAccountStatus.ACTIVE).build();
        account.setId(id);
        return account;
    }

    private Cash buildCash(Long id, User user) {
        Cash cash = new Cash();
        cash.setId(id);
        cash.setUser(user);
        return cash;
    }

    private FgtsEmployerAccount buildFgts(Long id, User user) {
        FgtsEmployerAccount fgts = new FgtsEmployerAccount();
        fgts.setId(id);
        fgts.setUser(user);
        return fgts;
    }

    private TransferCreateDto buildDto(Long sourceId, Long targetId, BigDecimal amount) {
        return new TransferCreateDto(sourceId, targetId, amount, "Transferência");
    }

    private Category buildCategory(Long id, CategoryType type) {
        Category c = Category.builder()
                .user(null).name("Categoria").type(type).system(false).build();
        c.setId(id);
        return c;
    }

    // -------------------------------------------------------------------------
    // Transfer
    // -------------------------------------------------------------------------

    @Test
    void transfer_WhenBankAccountToBankAccount_ShouldCreateTwoTransactions() {
        BankAccount source = buildBankAccount(1L, currentUser);
        BankAccount target = buildBankAccount(2L, targetUser);
        TransferCreateDto dto = buildDto(1L, 2L, new BigDecimal("200.00"));

        when(categoryService.findSystemByName("Transferência Enviada")).thenReturn(buildCategory(1L, CategoryType.NEUTRAL));
        when(categoryService.findSystemByName("Transferência Recebida")).thenReturn(buildCategory(2L, CategoryType.NEUTRAL));
        when(pocketRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(source));
        when(pocketRepository.findById(2L)).thenReturn(Optional.of(target));
        when(groupMembershipRepository.existsSharedGroup(currentUser.getId(), targetUser.getId())).thenReturn(true);
        when(transactionRepository.calculateBalanceByPocketId(1L)).thenReturn(new BigDecimal("500.00"));
        when(transactionRepository.save(any())).thenReturn(mock(Transaction.class));

        TransferDto result = transferService.transfer(dto, authentication);

        assertNotNull(result);
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void transfer_WhenCashToCash_ShouldCreateTwoTransactions() {
        Cash source = buildCash(1L, currentUser);
        Cash target = buildCash(2L, targetUser);
        TransferCreateDto dto = buildDto(1L, 2L, new BigDecimal("100.00"));

        when(categoryService.findSystemByName("Transferência Enviada")).thenReturn(buildCategory(1L, CategoryType.NEUTRAL));
        when(categoryService.findSystemByName("Transferência Recebida")).thenReturn(buildCategory(2L, CategoryType.NEUTRAL));
        when(pocketRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(source));
        when(pocketRepository.findById(2L)).thenReturn(Optional.of(target));
        when(groupMembershipRepository.existsSharedGroup(currentUser.getId(), targetUser.getId())).thenReturn(true);
        when(transactionRepository.calculateBalanceByPocketId(1L)).thenReturn(new BigDecimal("500.00"));
        when(transactionRepository.save(any())).thenReturn(mock(Transaction.class));

        TransferDto result = transferService.transfer(dto, authentication);

        assertNotNull(result);
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void transfer_WhenBankAccountToCash_ShouldCreateTwoTransactions() {
        BankAccount source = buildBankAccount(1L, currentUser);
        Cash target = buildCash(2L, targetUser);
        TransferCreateDto dto = buildDto(1L, 2L, new BigDecimal("150.00"));

        when(categoryService.findSystemByName("Transferência Enviada")).thenReturn(buildCategory(1L, CategoryType.NEUTRAL));
        when(categoryService.findSystemByName("Transferência Recebida")).thenReturn(buildCategory(2L, CategoryType.NEUTRAL));
        when(pocketRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(source));
        when(pocketRepository.findById(2L)).thenReturn(Optional.of(target));
        when(groupMembershipRepository.existsSharedGroup(currentUser.getId(), targetUser.getId())).thenReturn(true);
        when(transactionRepository.calculateBalanceByPocketId(1L)).thenReturn(new BigDecimal("500.00"));
        when(transactionRepository.save(any())).thenReturn(mock(Transaction.class));

        TransferDto result = transferService.transfer(dto, authentication);

        assertNotNull(result);
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void transfer_WhenSourcePocketNotFound_ShouldThrow() {
        when(pocketRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> transferService.transfer(buildDto(99L, 2L, new BigDecimal("100.00")), authentication));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_WhenTargetPocketNotFound_ShouldThrow() {
        BankAccount source = buildBankAccount(1L, currentUser);
        when(pocketRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(source));
        when(pocketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> transferService.transfer(buildDto(1L, 99L, new BigDecimal("100.00")), authentication));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_WhenSourcePocketIsInvalidType_ShouldThrow() {
        FgtsEmployerAccount source = buildFgts(1L, currentUser);
        when(pocketRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(source));

        assertThrows(IllegalArgumentException.class,
                () -> transferService.transfer(buildDto(1L, 2L, new BigDecimal("100.00")), authentication));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_WhenTargetPocketIsInvalidType_ShouldThrow() {
        BankAccount source = buildBankAccount(1L, currentUser);
        FgtsEmployerAccount target = buildFgts(2L, targetUser);
        when(pocketRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(source));
        when(pocketRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThrows(IllegalArgumentException.class,
                () -> transferService.transfer(buildDto(1L, 2L, new BigDecimal("100.00")), authentication));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_WhenSamePocket_ShouldThrow() {
        BankAccount source = buildBankAccount(1L, currentUser);
        when(pocketRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(source));
        when(pocketRepository.findById(1L)).thenReturn(Optional.of(source));

        assertThrows(IllegalArgumentException.class,
                () -> transferService.transfer(buildDto(1L, 1L, new BigDecimal("100.00")), authentication));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_WhenNoSharedGroup_ShouldThrow() {
        BankAccount source = buildBankAccount(1L, currentUser);
        BankAccount target = buildBankAccount(2L, targetUser);
        when(pocketRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(source));
        when(pocketRepository.findById(2L)).thenReturn(Optional.of(target));
        when(groupMembershipRepository.existsSharedGroup(currentUser.getId(), targetUser.getId())).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> transferService.transfer(buildDto(1L, 2L, new BigDecimal("100.00")), authentication));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transfer_WhenInsufficientBalance_ShouldThrow() {
        BankAccount source = buildBankAccount(1L, currentUser);
        BankAccount target = buildBankAccount(2L, targetUser);
        when(pocketRepository.findByIdAndUserId(1L, currentUser.getId())).thenReturn(Optional.of(source));
        when(pocketRepository.findById(2L)).thenReturn(Optional.of(target));
        when(groupMembershipRepository.existsSharedGroup(currentUser.getId(), targetUser.getId())).thenReturn(true);
        when(transactionRepository.calculateBalanceByPocketId(1L)).thenReturn(new BigDecimal("50.00"));

        assertThrows(IllegalArgumentException.class,
                () -> transferService.transfer(buildDto(1L, 2L, new BigDecimal("200.00")), authentication));
        verify(transactionRepository, never()).save(any());
    }
}