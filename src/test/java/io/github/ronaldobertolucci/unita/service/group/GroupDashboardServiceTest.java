package io.github.ronaldobertolucci.unita.service.group;

import io.github.ronaldobertolucci.unita.dto.dashboard.*;
import io.github.ronaldobertolucci.unita.model.group.GroupMembership;
import io.github.ronaldobertolucci.unita.model.group.GroupSharePermission;
import io.github.ronaldobertolucci.unita.model.group.ShareType;
import io.github.ronaldobertolucci.unita.model.investment.Indexer;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.GroupMembershipRepository;
import io.github.ronaldobertolucci.unita.repository.GroupSharePermissionRepository;
import io.github.ronaldobertolucci.unita.service.dashboard.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupDashboardServiceTest {

    @Mock private GroupMembershipRepository membershipRepository;
    @Mock private GroupSharePermissionRepository permissionRepository;
    @Mock private DashboardService dashboardService;
    @Mock private Authentication authentication;

    @InjectMocks
    private GroupDashboardService groupDashboardService;

    private User currentUser;
    private User memberUser;
    private static final Long GROUP_ID = 1L;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setEmail("current@test.com");

        memberUser = new User();
        memberUser.setId(2L);
        memberUser.setEmail("member@test.com");
        memberUser.setFirstName("João");
        memberUser.setLastName("Silva");

        when(authentication.getPrincipal()).thenReturn(currentUser);
    }

    // -------------------------------------------------------------------------
    // getGroupDashboard
    // -------------------------------------------------------------------------

    @Test
    void getGroupDashboard_WhenNotMember_ShouldThrowAccessDeniedException() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> groupDashboardService.getGroupDashboard(GROUP_ID, authentication));
    }

    @Test
    void getGroupDashboard_WhenAllPermissionsEnabled_ShouldReturnFullData() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID)).thenReturn(List.of(buildMembership(memberUser)));
        when(permissionRepository.findAllByGroupIdAndUserId(GROUP_ID, memberUser.getId()))
                .thenReturn(List.of(
                        buildPermission(ShareType.BALANCE, true),
                        buildPermission(ShareType.INVESTMENTS, true),
                        buildPermission(ShareType.CREDIT_CARD_BILLS, true)
                ));
        when(dashboardService.getPocketSummaryByUserId(memberUser.getId()))
                .thenReturn(List.of(new CategorySummaryDto("Cash", new BigDecimal("500.00"))));
        when(dashboardService.getInvestmentSummaryByUserId(memberUser.getId()))
                .thenReturn(List.of(new CategorySummaryDto("RENDA_FIXA", new BigDecimal("1000.00"))));
        when(dashboardService.getTotalOpenBillsByUserId(memberUser.getId()))
                .thenReturn(new BigDecimal("300.00"));

        GroupDashboardDto result = groupDashboardService.getGroupDashboard(GROUP_ID, authentication);

        assertEquals(1, result.members().size());
        GroupMemberDashboardDto member = result.members().get(0);
        assertNotNull(member.pockets());
        assertNotNull(member.investments());
        assertEquals(0, new BigDecimal("300.00").compareTo(member.totalOpenBills()));
    }

    @Test
    void getGroupDashboard_WhenBalanceDisabled_ShouldReturnNullPockets() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID)).thenReturn(List.of(buildMembership(memberUser)));
        when(permissionRepository.findAllByGroupIdAndUserId(GROUP_ID, memberUser.getId()))
                .thenReturn(List.of(
                        buildPermission(ShareType.BALANCE, false),
                        buildPermission(ShareType.INVESTMENTS, true),
                        buildPermission(ShareType.CREDIT_CARD_BILLS, true)
                ));
        when(dashboardService.getInvestmentSummaryByUserId(memberUser.getId())).thenReturn(List.of());
        when(dashboardService.getTotalOpenBillsByUserId(memberUser.getId())).thenReturn(BigDecimal.ZERO);

        GroupDashboardDto result = groupDashboardService.getGroupDashboard(GROUP_ID, authentication);

        assertNull(result.members().get(0).pockets());
        assertNotNull(result.members().get(0).investments());
    }

    @Test
    void getGroupDashboard_WhenInvestmentsDisabled_ShouldReturnNullInvestments() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID)).thenReturn(List.of(buildMembership(memberUser)));
        when(permissionRepository.findAllByGroupIdAndUserId(GROUP_ID, memberUser.getId()))
                .thenReturn(List.of(
                        buildPermission(ShareType.BALANCE, true),
                        buildPermission(ShareType.INVESTMENTS, false),
                        buildPermission(ShareType.CREDIT_CARD_BILLS, false)
                ));
        when(dashboardService.getPocketSummaryByUserId(memberUser.getId())).thenReturn(List.of());

        GroupDashboardDto result = groupDashboardService.getGroupDashboard(GROUP_ID, authentication);

        assertNotNull(result.members().get(0).pockets());
        assertNull(result.members().get(0).investments());
        assertNull(result.members().get(0).totalOpenBills());
    }

    @Test
    void getGroupDashboard_ShouldIncludeAllMembers() {
        User anotherMember = new User();
        anotherMember.setId(3L);

        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID))
                .thenReturn(List.of(buildMembership(memberUser), buildMembership(anotherMember)));
        when(permissionRepository.findAllByGroupIdAndUserId(eq(GROUP_ID), any())).thenReturn(List.of());

        GroupDashboardDto result = groupDashboardService.getGroupDashboard(GROUP_ID, authentication);

        assertEquals(2, result.members().size());
    }

    // -------------------------------------------------------------------------
    // getGroupFinancialSummary
    // -------------------------------------------------------------------------

    @Test
    void getGroupFinancialSummary_WhenNotMember_ShouldThrowAccessDeniedException() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> groupDashboardService.getGroupFinancialSummary(GROUP_ID, null, null, authentication));
    }

    @Test
    void getGroupFinancialSummary_WhenBothPermissionsEnabled_ShouldReturnFullData() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID)).thenReturn(List.of(buildMembership(memberUser)));
        when(permissionRepository.findAllByGroupIdAndUserId(GROUP_ID, memberUser.getId()))
                .thenReturn(List.of(
                        buildPermission(ShareType.INCOME_BY_CATEGORY, true),
                        buildPermission(ShareType.EXPENSES_BY_CATEGORY, true)
                ));
        when(dashboardService.getIncomesByUserId(eq(memberUser.getId()), any(), any()))
                .thenReturn(List.of(new CategorySummaryDto("Salário", new BigDecimal("4000.00"))));
        when(dashboardService.getExpensesByUserId(eq(memberUser.getId()), any(), any()))
                .thenReturn(List.of(new CategorySummaryDto("Alimentação", new BigDecimal("800.00"))));

        GroupFinancialSummaryDto result = groupDashboardService.getGroupFinancialSummary(
                GROUP_ID, null, null, authentication);

        assertNotNull(result.members().get(0).incomes());
        assertNotNull(result.members().get(0).expenses());
    }

    @Test
    void getGroupFinancialSummary_WhenIncomeDisabled_ShouldReturnNullIncomes() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID)).thenReturn(List.of(buildMembership(memberUser)));
        when(permissionRepository.findAllByGroupIdAndUserId(GROUP_ID, memberUser.getId()))
                .thenReturn(List.of(
                        buildPermission(ShareType.INCOME_BY_CATEGORY, false),
                        buildPermission(ShareType.EXPENSES_BY_CATEGORY, true)
                ));
        when(dashboardService.getExpensesByUserId(eq(memberUser.getId()), any(), any()))
                .thenReturn(List.of());

        GroupFinancialSummaryDto result = groupDashboardService.getGroupFinancialSummary(
                GROUP_ID, null, null, authentication);

        assertNull(result.members().get(0).incomes());
        assertNotNull(result.members().get(0).expenses());
    }

    // -------------------------------------------------------------------------
    // getGroupMonthlyFinancialSummary
    // -------------------------------------------------------------------------

    @Test
    void getGroupMonthlyFinancialSummary_WhenNotMember_ShouldThrowAccessDeniedException() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> groupDashboardService.getGroupMonthlyFinancialSummary(GROUP_ID, null, null, authentication));
    }

    @Test
    void getGroupMonthlyFinancialSummary_WhenBothPermissionsEnabled_ShouldReturnMonthlyData() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID)).thenReturn(List.of(buildMembership(memberUser)));
        when(permissionRepository.findAllByGroupIdAndUserId(GROUP_ID, memberUser.getId()))
                .thenReturn(List.of(
                        buildPermission(ShareType.INCOME_BY_CATEGORY, true),
                        buildPermission(ShareType.EXPENSES_BY_CATEGORY, true)
                ));
        when(dashboardService.getMonthlyIncomeByUserId(eq(memberUser.getId()), any(), any()))
                .thenReturn(java.util.Map.of("2025-01", new BigDecimal("4000.00")));
        when(dashboardService.getMonthlyExpenseByUserId(eq(memberUser.getId()), any(), any()))
                .thenReturn(java.util.Map.of("2025-01", new BigDecimal("800.00")));

        GroupMonthlyDto result = groupDashboardService.getGroupMonthlyFinancialSummary(
                GROUP_ID, null, null, authentication);

        List<GroupMonthlyFinancialSummaryDto> monthly = result.members().get(0).monthly();
        assertNotNull(monthly);
        assertEquals(1, monthly.size());
        assertEquals("2025-01", monthly.get(0).month());
        assertEquals(0, new BigDecimal("4000.00").compareTo(monthly.get(0).totalIncome()));
        assertEquals(0, new BigDecimal("800.00").compareTo(monthly.get(0).totalExpense()));
    }

    @Test
    void getGroupMonthlyFinancialSummary_WhenBothPermissionsDisabled_ShouldReturnNullMonthly() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID)).thenReturn(List.of(buildMembership(memberUser)));
        when(permissionRepository.findAllByGroupIdAndUserId(GROUP_ID, memberUser.getId()))
                .thenReturn(List.of(
                        buildPermission(ShareType.INCOME_BY_CATEGORY, false),
                        buildPermission(ShareType.EXPENSES_BY_CATEGORY, false)
                ));

        GroupMonthlyDto result = groupDashboardService.getGroupMonthlyFinancialSummary(
                GROUP_ID, null, null, authentication);

        assertNull(result.members().get(0).monthly());
    }

    @Test
    void getGroupMonthlyFinancialSummary_WhenOnlyIncomeEnabled_ShouldReturnNullTotalExpense() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID)).thenReturn(List.of(buildMembership(memberUser)));
        when(permissionRepository.findAllByGroupIdAndUserId(GROUP_ID, memberUser.getId()))
                .thenReturn(List.of(
                        buildPermission(ShareType.INCOME_BY_CATEGORY, true),
                        buildPermission(ShareType.EXPENSES_BY_CATEGORY, false)
                ));
        when(dashboardService.getMonthlyIncomeByUserId(eq(memberUser.getId()), any(), any()))
                .thenReturn(java.util.Map.of("2025-01", new BigDecimal("4000.00")));

        GroupMonthlyDto result = groupDashboardService.getGroupMonthlyFinancialSummary(
                GROUP_ID, null, null, authentication);

        GroupMonthlyFinancialSummaryDto month = result.members().get(0).monthly().get(0);
        assertNotNull(month.totalIncome());
        assertNull(month.totalExpense());
    }

    // -------------------------------------------------------------------------
// getGroupIssuerRiskSummary
// -------------------------------------------------------------------------

    @Test
    void getGroupIssuerRiskSummary_WhenNotMember_ShouldThrowAccessDeniedException() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> groupDashboardService.getGroupIssuerRiskSummary(GROUP_ID, authentication));
    }

    @Test
    void getGroupIssuerRiskSummary_WhenInvestmentsEnabled_ShouldReturnData() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID)).thenReturn(List.of(buildMembership(memberUser)));
        when(permissionRepository.findAllByGroupIdAndUserId(GROUP_ID, memberUser.getId()))
                .thenReturn(List.of(buildPermission(ShareType.INVESTMENTS, true)));
        when(dashboardService.getIssuerRiskSummaryByUserId(memberUser.getId()))
                .thenReturn(List.of(new IssuerRiskSummaryDto("Banco Teste", new BigDecimal("1500.00"))));

        GroupIssuerRiskDto result = groupDashboardService.getGroupIssuerRiskSummary(GROUP_ID, authentication);

        assertEquals(1, result.members().size());
        assertNotNull(result.members().get(0).issuerRisk());
        assertEquals(1, result.members().get(0).issuerRisk().size());
    }

    @Test
    void getGroupIssuerRiskSummary_WhenInvestmentsDisabled_ShouldReturnNullIssuerRisk() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID)).thenReturn(List.of(buildMembership(memberUser)));
        when(permissionRepository.findAllByGroupIdAndUserId(GROUP_ID, memberUser.getId()))
                .thenReturn(List.of(buildPermission(ShareType.INVESTMENTS, false)));

        GroupIssuerRiskDto result = groupDashboardService.getGroupIssuerRiskSummary(GROUP_ID, authentication);

        assertNull(result.members().get(0).issuerRisk());
    }

    // -------------------------------------------------------------------------
    // getGroupIndexerSummary
    // -------------------------------------------------------------------------

    @Test
    void getGroupIndexerSummary_WhenNotMember_ShouldThrowAccessDeniedException() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> groupDashboardService.getGroupIndexerSummary(GROUP_ID, authentication));
    }

    @Test
    void getGroupIndexerSummary_WhenInvestmentsEnabled_ShouldReturnData() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID)).thenReturn(List.of(buildMembership(memberUser)));
        when(permissionRepository.findAllByGroupIdAndUserId(GROUP_ID, memberUser.getId()))
                .thenReturn(List.of(buildPermission(ShareType.INVESTMENTS, true)));
        when(dashboardService.getIndexerSummaryByUserId(memberUser.getId()))
                .thenReturn(List.of(
                        new IndexerSummaryDto(Indexer.CDI, new BigDecimal("1500.00")),
                        new IndexerSummaryDto(Indexer.IPCA, new BigDecimal("2000.00"))
                ));

        GroupIndexerSummaryDto result = groupDashboardService.getGroupIndexerSummary(GROUP_ID, authentication);

        assertEquals(1, result.members().size());
        assertNotNull(result.members().get(0).indexerSummary());
        assertEquals(2, result.members().get(0).indexerSummary().size());
    }

    @Test
    void getGroupIndexerSummary_WhenInvestmentsDisabled_ShouldReturnNullIndexerSummary() {
        when(membershipRepository.existsByUserIdAndGroupId(currentUser.getId(), GROUP_ID)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(GROUP_ID)).thenReturn(List.of(buildMembership(memberUser)));
        when(permissionRepository.findAllByGroupIdAndUserId(GROUP_ID, memberUser.getId()))
                .thenReturn(List.of(buildPermission(ShareType.INVESTMENTS, false)));

        GroupIndexerSummaryDto result = groupDashboardService.getGroupIndexerSummary(GROUP_ID, authentication);

        assertNull(result.members().get(0).indexerSummary());
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private GroupMembership buildMembership(User user) {
        return GroupMembership.builder().user(user).build();
    }

    private GroupSharePermission buildPermission(ShareType shareType, boolean enabled) {
        return GroupSharePermission.builder()
                .shareType(shareType)
                .enabled(enabled)
                .build();
    }
}