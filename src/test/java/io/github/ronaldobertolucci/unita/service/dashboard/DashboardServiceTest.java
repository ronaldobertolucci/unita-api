package io.github.ronaldobertolucci.unita.service.dashboard;

import io.github.ronaldobertolucci.unita.dto.dashboard.*;
import io.github.ronaldobertolucci.unita.dto.investment.AssetSummaryDto;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private PocketRepository pocketRepository;
    @Mock private AssetRepository assetRepository;
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
        Cash cash1 = buildCash(1L);
        Cash cash2 = buildCash(2L);
        Cash cash3 = buildCash(3L);

        when(pocketRepository.findAllByUserId(currentUser.getId()))
                .thenReturn(List.of(cash1, cash2, cash3));
        when(transactionRepository.calculateBalanceByPocketId(1L)).thenReturn(new BigDecimal("1000.00"));
        when(transactionRepository.calculateBalanceByPocketId(2L)).thenReturn(new BigDecimal("500.00"));
        when(transactionRepository.calculateBalanceByPocketId(3L)).thenReturn(new BigDecimal("200.00"));
        when(creditCardInstallmentRepository.sumInstallmentsByUserIdAndOpenBills(any())).thenReturn(BigDecimal.ZERO);
        when(creditCardRefundRepository.sumRefundsByUserIdAndOpenBills(any())).thenReturn(BigDecimal.ZERO);

        DashboardDto result = dashboardService.getDashboard(authentication);

        assertEquals(1, result.pockets().size());
        assertEquals(0, new BigDecimal("1700.00").compareTo(result.pockets().get(0).total()));
    }

    @Test
    void getDashboard_ShouldGroupInvestmentsByCategory() {
        when(pocketRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of());
        when(assetRepository.findAllByUserIdOrderByName(currentUser.getId())).thenReturn(List.of(
                buildAsset(1L, AssetCategory.RENDA_FIXA, new BigDecimal("1000.00")),
                buildAsset(2L, AssetCategory.RENDA_FIXA, new BigDecimal("500.00")),
                buildAsset(3L, AssetCategory.PREVIDENCIA, new BigDecimal("3000.00"))
        ));
        when(creditCardInstallmentRepository.sumInstallmentsByUserIdAndOpenBills(any())).thenReturn(BigDecimal.ZERO);
        when(creditCardRefundRepository.sumRefundsByUserIdAndOpenBills(any())).thenReturn(BigDecimal.ZERO);

        DashboardDto result = dashboardService.getDashboard(authentication);

        assertEquals(2, result.investments().size());
        CategorySummaryDto rendaFixa = result.investments().stream()
                .filter(i -> AssetCategory.RENDA_FIXA.name().equals(i.category())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1500.00").compareTo(rendaFixa.total()));
    }

    @Test
    void getDashboard_ShouldCalculateTotalOpenBillsAsInstallmentsMinusRefunds() {
        when(pocketRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of());
        when(assetRepository.findAllByUserIdOrderByName(currentUser.getId())).thenReturn(List.of());
        when(creditCardInstallmentRepository.sumInstallmentsByUserIdAndOpenBills(currentUser.getId()))
                .thenReturn(new BigDecimal("1000.00"));
        when(creditCardRefundRepository.sumRefundsByUserIdAndOpenBills(currentUser.getId()))
                .thenReturn(new BigDecimal("200.00"));

        DashboardDto result = dashboardService.getDashboard(authentication);

        assertEquals(0, new BigDecimal("800.00").compareTo(result.totalOpenBills()));
    }

    @Test
    void getDashboard_WhenNoPocketsOrInvestments_ShouldReturnEmptyLists() {
        when(pocketRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of());
        when(assetRepository.findAllByUserIdOrderByName(currentUser.getId())).thenReturn(List.of());
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
// getIssuerRiskSummary
// -------------------------------------------------------------------------

    @Test
    void getIssuerRiskSummary_ShouldDelegateToRepository() {
        List<IssuerRiskSummaryDto> expected = List.of(
                new IssuerRiskSummaryDto("Banco Teste", new BigDecimal("1500.00"))
        );
        when(assetRepository.sumCurrentValueByLegalEntityAndUserId(currentUser.getId())).thenReturn(expected);

        List<IssuerRiskSummaryDto> result = dashboardService.getIssuerRiskSummary(authentication);

        assertEquals(1, result.size());
        assertEquals("Banco Teste", result.get(0).legalEntityName());
        assertEquals(0, new BigDecimal("1500.00").compareTo(result.get(0).totalCurrentValue()));
    }

    @Test
    void getIssuerRiskSummary_WhenNoAssets_ShouldReturnEmptyList() {
        when(assetRepository.sumCurrentValueByLegalEntityAndUserId(currentUser.getId())).thenReturn(List.of());

        assertTrue(dashboardService.getIssuerRiskSummary(authentication).isEmpty());
    }

    // -------------------------------------------------------------------------
    // getIndexerSummary
    // -------------------------------------------------------------------------

    @Test
    void getIndexerSummary_ShouldDelegateToRepository() {
        List<IndexerSummaryDto> expected = List.of(
                new IndexerSummaryDto(Indexer.CDI, new BigDecimal("1500.00")),
                new IndexerSummaryDto(Indexer.IPCA, new BigDecimal("2000.00"))
        );
        when(assetRepository.sumCurrentValueByIndexerAndUserId(currentUser.getId())).thenReturn(expected);

        List<IndexerSummaryDto> result = dashboardService.getIndexerSummary(authentication);

        assertEquals(2, result.size());
        assertEquals(Indexer.CDI, result.get(0).indexer());
        assertEquals(0, new BigDecimal("1500.00").compareTo(result.get(0).totalCurrentValue()));
    }

    @Test
    void getIndexerSummary_WhenNoFixedIncomeAssets_ShouldReturnEmptyList() {
        when(assetRepository.sumCurrentValueByIndexerAndUserId(currentUser.getId())).thenReturn(List.of());

        assertTrue(dashboardService.getIndexerSummary(authentication).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Cash buildCash(Long id) {
        Cash cash = new Cash();
        cash.setId(id);
        cash.setUser(currentUser);
        return cash;
    }

    private Asset buildAsset(Long id, AssetCategory category, BigDecimal currentValue) {
        LegalEntity le = new LegalEntity();
        le.setCorporateName("Banco");

        InvestmentPosition position = InvestmentPosition.builder()
                .currentValue(currentValue)
                .totalInvested(BigDecimal.ZERO)
                .redeemedValue(BigDecimal.ZERO)
                .build();

        Asset asset = Asset.builder()
                .user(currentUser)
                .legalEntity(le)
                .name("Asset")
                .category(category)
                .status(AssetStatus.ACTIVE)
                .position(position)
                .build();
        asset.setId(id);
        return asset;
    }
}