package io.github.ronaldobertolucci.unita.service.group;

import io.github.ronaldobertolucci.unita.dto.group.*;
import io.github.ronaldobertolucci.unita.model.card.CreditCard;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBill;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.group.Group;
import io.github.ronaldobertolucci.unita.model.group.GroupMembership;
import io.github.ronaldobertolucci.unita.model.group.GroupSharePermission;
import io.github.ronaldobertolucci.unita.model.group.ShareType;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupShareServiceTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMembershipRepository groupMembershipRepository;
    @Mock
    private GroupSharePermissionRepository groupSharePermissionRepository;
    @Mock
    private PocketRepository pocketRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CreditCardBillRepository creditCardBillRepository;
    @Mock
    private CreditCardInstallmentRepository creditCardInstallmentRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private GroupShareService groupShareService;

    private User currentUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setFirstName("Maria");
        currentUser.setLastName("Souza");

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setFirstName("João");
        otherUser.setLastName("Silva");

        when(authentication.getPrincipal()).thenReturn(currentUser);
    }

    // -------------------------------------------------------------------------
    // updatePermissions
    // -------------------------------------------------------------------------

    @Test
    void updatePermissions_WhenNewPermission_ShouldCreateAndReturn() {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));

        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(groupSharePermissionRepository.findAllByGroupIdAndUserId(1L, 1L)).thenReturn(List.of());
        when(groupRepository.getReferenceById(1L)).thenReturn(buildGroup(1L));

        GroupSharePermission saved = buildPermission(ShareType.BALANCE, true);
        when(groupSharePermissionRepository.saveAll(any())).thenReturn(List.of(saved));

        List<GroupSharePermissionDto> result = groupShareService.updatePermissions(1L, dto, authentication);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ShareType.BALANCE, result.get(0).shareType());
        assertTrue(result.get(0).enabled());
        verify(groupSharePermissionRepository).saveAll(any());
    }

    @Test
    void updatePermissions_WhenExistingPermission_ShouldUpdateAndReturn() {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, false)));

        GroupSharePermission existing = buildPermission(ShareType.BALANCE, true);
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(groupSharePermissionRepository.findAllByGroupIdAndUserId(1L, 1L)).thenReturn(List.of(existing));
        when(groupSharePermissionRepository.saveAll(any())).thenReturn(List.of(buildPermission(ShareType.BALANCE, false)));

        List<GroupSharePermissionDto> result = groupShareService.updatePermissions(1L, dto, authentication);

        assertFalse(result.get(0).enabled());
    }

    @Test
    void updatePermissions_WhenGroupNotFound_ShouldThrow() {
        when(groupRepository.existsById(99L)).thenReturn(false);

        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));

        assertThrows(EntityNotFoundException.class,
                () -> groupShareService.updatePermissions(99L, dto, authentication));
        verify(groupSharePermissionRepository, never()).saveAll(any());
    }

    @Test
    void updatePermissions_WhenNotMember_ShouldThrow() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(false);

        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));

        assertThrows(IllegalArgumentException.class,
                () -> groupShareService.updatePermissions(1L, dto, authentication));
        verify(groupSharePermissionRepository, never()).saveAll(any());
    }

    // -------------------------------------------------------------------------
    // getBalance
    // -------------------------------------------------------------------------

    @Test
    void getBalance_WhenMembersEnabled_ShouldReturnBalances() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(groupSharePermissionRepository.findEnabledUserIdsByGroupIdAndShareType(1L, ShareType.BALANCE))
                .thenReturn(List.of(2L));
        when(groupMembershipRepository.findByGroupIdWithUsers(1L))
                .thenReturn(List.of(buildMembership(otherUser)));

        Cash cash = buildCash(10L, otherUser);
        when(pocketRepository.findAllByUserId(2L)).thenReturn(List.of(cash));
        when(transactionRepository.calculateBalanceByPocketId(10L)).thenReturn(new BigDecimal("500.00"));

        List<GroupMemberBalanceDto> result = groupShareService.getBalance(1L, authentication);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("João Silva", result.get(0).memberName());
        assertEquals(1, result.get(0).pockets().size());
        assertEquals(0, new BigDecimal("500.00").compareTo(result.get(0).pockets().get(0).balance()));
    }

    @Test
    void getBalance_WhenNoMembersEnabled_ShouldReturnEmptyList() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(groupSharePermissionRepository.findEnabledUserIdsByGroupIdAndShareType(1L, ShareType.BALANCE))
                .thenReturn(List.of());
        when(groupMembershipRepository.findByGroupIdWithUsers(1L)).thenReturn(List.of());

        List<GroupMemberBalanceDto> result = groupShareService.getBalance(1L, authentication);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getBalance_WhenGroupNotFound_ShouldThrow() {
        when(groupRepository.existsById(99L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> groupShareService.getBalance(99L, authentication));
    }

    @Test
    void getBalance_WhenNotMember_ShouldThrow() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> groupShareService.getBalance(1L, authentication));
    }

    // -------------------------------------------------------------------------
    // getCreditCardBills
    // -------------------------------------------------------------------------

    @Test
    void getCreditCardBills_WhenMembersEnabled_ShouldReturnBills() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(groupSharePermissionRepository.findEnabledUserIdsByGroupIdAndShareType(1L, ShareType.CREDIT_CARD_BILLS))
                .thenReturn(List.of(2L));
        when(groupMembershipRepository.findByGroupIdWithUsers(1L))
                .thenReturn(List.of(buildMembership(otherUser)));

        CreditCardBill bill = buildCreditCardBill(1L);
        when(creditCardBillRepository.findAllByUserId(2L)).thenReturn(List.of(bill));
        when(creditCardInstallmentRepository.sumAmountByBillId(1L)).thenReturn(new BigDecimal("300.00"));

        List<GroupMemberCreditCardBillsDto> result = groupShareService.getCreditCardBills(1L, authentication);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("João Silva", result.get(0).memberName());
        assertEquals(1, result.get(0).bills().size());
        assertEquals(0, new BigDecimal("300.00").compareTo(result.get(0).bills().get(0).totalAmount()));
    }

    @Test
    void getCreditCardBills_WhenGroupNotFound_ShouldThrow() {
        when(groupRepository.existsById(99L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> groupShareService.getCreditCardBills(99L, authentication));
    }

    @Test
    void getCreditCardBills_WhenNotMember_ShouldThrow() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> groupShareService.getCreditCardBills(1L, authentication));
    }

    // -------------------------------------------------------------------------
    // getExpenses
    // -------------------------------------------------------------------------

    @Test
    void getExpenses_WhenMembersEnabled_ShouldReturnAggregated() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(groupSharePermissionRepository.findEnabledUserIdsByGroupIdAndShareType(1L, ShareType.EXPENSES_BY_CATEGORY))
                .thenReturn(List.of(2L));
        when(groupMembershipRepository.findByGroupIdWithUsers(1L))
                .thenReturn(List.of(buildMembership(otherUser)));

        List<Object[]> rows = List.of(new Object[][]{new Object[]{"Alimentação", new BigDecimal("200.00")}});
        when(transactionRepository.sumAmountByCategoryAndUserIdAndDirection(2L, "EXPENSE", null, null))
                .thenReturn(rows);

        List<GroupMemberCategoryAmountDto> result = groupShareService.getExpenses(1L, null, null, authentication);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("João Silva", result.get(0).memberName());
        assertEquals("Alimentação", result.get(0).categories().get(0).categoryName());
        assertEquals(0, new BigDecimal("200.00").compareTo(result.get(0).categories().get(0).totalAmount()));
    }

    @Test
    void getExpenses_WhenStartDateAfterEndDate_ShouldThrow() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);

        LocalDate start = LocalDate.of(2025, 1, 31);
        LocalDate end = LocalDate.of(2025, 1, 1);

        assertThrows(IllegalArgumentException.class,
                () -> groupShareService.getExpenses(1L, start, end, authentication));
        verify(transactionRepository, never()).sumAmountByCategoryAndUserIdAndDirection(any(), any(), any(), any());
    }

    @Test
    void getExpenses_WhenGroupNotFound_ShouldThrow() {
        when(groupRepository.existsById(99L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> groupShareService.getExpenses(99L, null, null, authentication));
    }

    // -------------------------------------------------------------------------
    // getIncome
    // -------------------------------------------------------------------------

    @Test
    void getIncome_WhenMembersEnabled_ShouldReturnAggregated() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(groupSharePermissionRepository.findEnabledUserIdsByGroupIdAndShareType(1L, ShareType.INCOME_BY_CATEGORY))
                .thenReturn(List.of(2L));
        when(groupMembershipRepository.findByGroupIdWithUsers(1L))
                .thenReturn(List.of(buildMembership(otherUser)));
        List<Object[]> rows = List.of(new Object[][]{new Object[]{"Salário", new BigDecimal("5000.00")}});
        when(transactionRepository.sumAmountByCategoryAndUserIdAndDirection(2L, "INCOME", null, null))
                .thenReturn(rows);

        List<GroupMemberCategoryAmountDto> result = groupShareService.getIncome(1L, null, null, authentication);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Salário", result.get(0).categories().get(0).categoryName());
        assertEquals(0, new BigDecimal("5000.00").compareTo(result.get(0).categories().get(0).totalAmount()));
    }

    @Test
    void getIncome_WhenStartDateAfterEndDate_ShouldThrow() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);

        LocalDate start = LocalDate.of(2025, 1, 31);
        LocalDate end = LocalDate.of(2025, 1, 1);

        assertThrows(IllegalArgumentException.class,
                () -> groupShareService.getIncome(1L, start, end, authentication));
        verify(transactionRepository, never()).sumAmountByCategoryAndUserIdAndDirection(any(), any(), any(), any());
    }

    @Test
    void getIncome_WhenNotMember_ShouldThrow() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> groupShareService.getIncome(1L, null, null, authentication));
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Group buildGroup(Long id) {
        Group g = new Group();
        g.setId(id);
        return g;
    }

    private GroupMembership buildMembership(User user) {
        GroupMembership gm = new GroupMembership();
        gm.setUser(user);
        gm.setGroup(buildGroup(1L));
        return gm;
    }

    private GroupSharePermission buildPermission(ShareType type, boolean enabled) {
        return GroupSharePermission.builder()
                .group(buildGroup(1L))
                .user(currentUser)
                .shareType(type)
                .enabled(enabled)
                .build();
    }

    private Cash buildCash(Long id, User user) {
        Cash cash = new Cash();
        cash.setId(id);
        cash.setUser(user);
        return cash;
    }

    private CreditCardBill buildCreditCardBill(Long id) {
        LegalEntity le = new LegalEntity();
        le.setCorporateName("Banco do Brasil");

        CreditCard card = CreditCard.builder()
                .legalEntity(le)
                .lastFourDigits("1234")
                .build();
        card.setId(1L);

        CreditCardBill bill = CreditCardBill.builder()
                .creditCard(card)
                .closingDate(LocalDate.of(2025, 1, 10))
                .dueDate(LocalDate.of(2025, 2, 5))
                .status(CreditCardBillStatus.OPEN)
                .build();
        bill.setId(id);
        return bill;
    }
}