package io.github.ronaldobertolucci.unita.service.dashboard;

import io.github.ronaldobertolucci.unita.dto.dashboard.CategorySummaryDto;
import io.github.ronaldobertolucci.unita.dto.dashboard.DashboardDto;
import io.github.ronaldobertolucci.unita.dto.dashboard.FinancialSummaryDto;
import io.github.ronaldobertolucci.unita.dto.dashboard.MonthlyFinancialSummaryDto;
import io.github.ronaldobertolucci.unita.dto.investment.AssetSummaryDto;
import io.github.ronaldobertolucci.unita.dto.pocket.PocketSummaryDto;
import io.github.ronaldobertolucci.unita.model.investment.AssetCategory;
import io.github.ronaldobertolucci.unita.model.investment.AssetStatus;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.CreditCardInstallmentRepository;
import io.github.ronaldobertolucci.unita.repository.CreditCardRefundRepository;
import io.github.ronaldobertolucci.unita.repository.TransactionRepository;
import io.github.ronaldobertolucci.unita.service.investment.AssetService;
import io.github.ronaldobertolucci.unita.service.pocket.PocketService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private PocketService pocketService;
    @Mock private AssetService assetService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CreditCardInstallmentRepository creditCardInstallmentRepository;
    @Mock private CreditCardRefundRepository creditCardRefundRepository;
    @Mock private Authentication authentication;

    @InjectMocks
    private DashboardService dashboardService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        when(authentication.getPrincipal()).thenReturn(currentUser);
    }

    // -------------------------------------------------------------------------
    // getDashboard
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_ShouldGroupPocketsByType() {
        when(pocketService.findMyPockets(any())).thenReturn(List.of(
                new PocketSummaryDto(1L, "BankAccount", "Banco A", new BigDecimal("1000.00")),
                new PocketSummaryDto(2L, "BankAccount", "Banco B", new BigDecimal("500.00")),
                new PocketSummaryDto(3L, "Cash", "Espécie", new BigDecimal("200.00"))
        ));
        when(assetService.findAll(any())).thenReturn(List.of());
        when(creditCardInstallmentRepository.sumInstallmentsByUserIdAndOpenBills(any())).thenReturn(BigDecimal.ZERO);
        when(creditCardRefundRepository.sumRefundsByUserIdAndOpenBills(any())).thenReturn(BigDecimal.ZERO);

        DashboardDto result = dashboardService.getDashboard(authentication);

        assertEquals(2, result.pockets().size());
        CategorySummaryDto bankAccount = result.pockets().stream()
                .filter(p -> "BankAccount".equals(p.category())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1500.00").compareTo(bankAccount.total()));
    }

    @Test
    void getDashboard_ShouldGroupInvestmentsByCategory() {
        when(pocketService.findMyPockets(any())).thenReturn(List.of());
        when(assetService.findAll(any())).thenReturn(List.of(
                buildAssetSummary("RENDA_FIXA", new BigDecimal("1000.00")),
                buildAssetSummary("RENDA_FIXA", new BigDecimal("500.00")),
                buildAssetSummary("PREVIDENCIA", new BigDecimal("3000.00"))
        ));
        when(creditCardInstallmentRepository.sumInstallmentsByUserIdAndOpenBills(any())).thenReturn(BigDecimal.ZERO);
        when(creditCardRefundRepository.sumRefundsByUserIdAndOpenBills(any())).thenReturn(BigDecimal.ZERO);

        DashboardDto result = dashboardService.getDashboard(authentication);

        assertEquals(2, result.investments().size());
        CategorySummaryDto rendaFixa = result.investments().stream()
                .filter(i -> "RENDA_FIXA".equals(i.category())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1500.00").compareTo(rendaFixa.total()));
    }

    @Test
    void getDashboard_ShouldCalculateTotalOpenBillsAsInstallmentsMinusRefunds() {
        when(pocketService.findMyPockets(any())).thenReturn(List.of());
        when(assetService.findAll(any())).thenReturn(List.of());
        when(creditCardInstallmentRepository.sumInstallmentsByUserIdAndOpenBills(currentUser.getId()))
                .thenReturn(new BigDecimal("1000.00"));
        when(creditCardRefundRepository.sumRefundsByUserIdAndOpenBills(currentUser.getId()))
                .thenReturn(new BigDecimal("200.00"));

        DashboardDto result = dashboardService.getDashboard(authentication);

        assertEquals(0, new BigDecimal("800.00").compareTo(result.totalOpenBills()));
    }

    @Test
    void getDashboard_WhenNoPocketsOrInvestments_ShouldReturnEmptyLists() {
        when(pocketService.findMyPockets(any())).thenReturn(List.of());
        when(assetService.findAll(any())).thenReturn(List.of());
        when(creditCardInstallmentRepository.sumInstallmentsByUserIdAndOpenBills(any())).thenReturn(BigDecimal.ZERO);
        when(creditCardRefundRepository.sumRefundsByUserIdAndOpenBills(any())).thenReturn(BigDecimal.ZERO);

        DashboardDto result = dashboardService.getDashboard(authentication);

        assertTrue(result.pockets().isEmpty());
        assertTrue(result.investments().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalOpenBills()));
    }

    // -------------------------------------------------------------------------
    // getFinancialSummary
    // -------------------------------------------------------------------------

    @Test
    void getFinancialSummary_ShouldReturnIncomesFromTransactionsOnly() {
        when(transactionRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(
                eq(currentUser.getId()), eq("INCOME"), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"Salário", "INCOME", new BigDecimal("4000.00")}));
        when(transactionRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(
                eq(currentUser.getId()), eq("EXPENSE"), any(), any()))
                .thenReturn(List.of());
        when(creditCardInstallmentRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(
                eq(currentUser.getId()), eq("EXPENSE"), any(), any()))
                .thenReturn(List.of());

        FinancialSummaryDto result = dashboardService.getFinancialSummary(null, null, authentication);

        assertEquals(1, result.incomes().size());
        assertEquals("Salário", result.incomes().get(0).category());
        assertEquals(0, new BigDecimal("4000.00").compareTo(result.incomes().get(0).total()));
    }

    @Test
    void getFinancialSummary_ShouldMergeExpensesFromTransactionsAndInstallments() {
        when(transactionRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(
                eq(currentUser.getId()), eq("INCOME"), any(), any()))
                .thenReturn(List.of());
        when(transactionRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(
                eq(currentUser.getId()), eq("EXPENSE"), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"Alimentação", "EXPENSE", new BigDecimal("300.00")}));
        when(creditCardInstallmentRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(
                eq(currentUser.getId()), eq("EXPENSE"), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"Alimentação", new BigDecimal("200.00")}));

        FinancialSummaryDto result = dashboardService.getFinancialSummary(null, null, authentication);

        assertEquals(1, result.expenses().size());
        assertEquals(0, new BigDecimal("500.00").compareTo(result.expenses().get(0).total()));
    }

    @Test
    void getFinancialSummary_WhenNoData_ShouldReturnEmptyLists() {
        when(transactionRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(creditCardInstallmentRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(any(), any(), any(), any()))
                .thenReturn(List.of());

        FinancialSummaryDto result = dashboardService.getFinancialSummary(null, null, authentication);

        assertTrue(result.incomes().isEmpty());
        assertTrue(result.expenses().isEmpty());
    }

    // -------------------------------------------------------------------------
    // getMonthlyFinancialSummary
    // -------------------------------------------------------------------------

    @Test
    void getMonthlyFinancialSummary_ShouldGroupByMonth() {
        when(transactionRepository.sumAmountByMonthAndCategoryTypeAndUserIdAndPeriod(
                eq(currentUser.getId()), any(), any()))
                .thenReturn(List.of(
                        new Object[]{"2025-01", "INCOME", new BigDecimal("4000.00")},
                        new Object[]{"2025-01", "EXPENSE", new BigDecimal("500.00")},
                        new Object[]{"2025-02", "INCOME", new BigDecimal("3500.00")}
                ));
        when(creditCardInstallmentRepository.sumExpenseAmountByMonthAndUserIdAndPeriod(
                eq(currentUser.getId()), any(), any()))
                .thenReturn(List.of());

        List<MonthlyFinancialSummaryDto> result = dashboardService.getMonthlyFinancialSummary(
                null, null, authentication);

        assertEquals(2, result.size());
        MonthlyFinancialSummaryDto jan = result.get(0);
        assertEquals("2025-01", jan.month());
        assertEquals(0, new BigDecimal("4000.00").compareTo(jan.totalIncome()));
        assertEquals(0, new BigDecimal("500.00").compareTo(jan.totalExpense()));
    }

    @Test
    void getMonthlyFinancialSummary_ShouldMergeInstallmentExpensesByMonth() {
        when(transactionRepository.sumAmountByMonthAndCategoryTypeAndUserIdAndPeriod(
                eq(currentUser.getId()), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"2025-01", "EXPENSE", new BigDecimal("300.00")}
                ));
        when(creditCardInstallmentRepository.sumExpenseAmountByMonthAndUserIdAndPeriod(
                eq(currentUser.getId()), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"2025-01", new BigDecimal("200.00")}
                ));

        List<MonthlyFinancialSummaryDto> result = dashboardService.getMonthlyFinancialSummary(
                null, null, authentication);

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("500.00").compareTo(result.get(0).totalExpense()));
    }

    @Test
    void getMonthlyFinancialSummary_WhenNoData_ShouldReturnEmptyList() {
        when(transactionRepository.sumAmountByMonthAndCategoryTypeAndUserIdAndPeriod(any(), any(), any()))
                .thenReturn(List.of());
        when(creditCardInstallmentRepository.sumExpenseAmountByMonthAndUserIdAndPeriod(any(), any(), any()))
                .thenReturn(List.of());

        List<MonthlyFinancialSummaryDto> result = dashboardService.getMonthlyFinancialSummary(
                null, null, authentication);

        assertTrue(result.isEmpty());
    }

    @Test
    void getMonthlyFinancialSummary_ShouldReturnResultsSortedByMonth() {
        when(transactionRepository.sumAmountByMonthAndCategoryTypeAndUserIdAndPeriod(
                eq(currentUser.getId()), any(), any()))
                .thenReturn(List.of(
                        new Object[]{"2025-03", "INCOME", new BigDecimal("1000.00")},
                        new Object[]{"2025-01", "INCOME", new BigDecimal("2000.00")}
                ));
        when(creditCardInstallmentRepository.sumExpenseAmountByMonthAndUserIdAndPeriod(any(), any(), any()))
                .thenReturn(List.of());

        List<MonthlyFinancialSummaryDto> result = dashboardService.getMonthlyFinancialSummary(
                null, null, authentication);

        assertEquals("2025-01", result.get(0).month());
        assertEquals("2025-03", result.get(1).month());
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private AssetSummaryDto buildAssetSummary(String category, BigDecimal currentValue) {
        return new AssetSummaryDto(1L, "Asset", AssetCategory.valueOf(category), AssetStatus.ACTIVE, "Banco", currentValue,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}