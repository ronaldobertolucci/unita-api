package io.github.ronaldobertolucci.unita.service.dashboard;

import io.github.ronaldobertolucci.unita.dto.dashboard.*;
import io.github.ronaldobertolucci.unita.dto.investment.AssetSummaryDto;
import io.github.ronaldobertolucci.unita.dto.pocket.PocketSummaryDto;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PocketRepository pocketRepository;
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final CreditCardInstallmentRepository creditCardInstallmentRepository;
    private final CreditCardRefundRepository creditCardRefundRepository;

    // -------------------------------------------------------------------------
    // Public Authentication-based methods (DashboardController)
    // -------------------------------------------------------------------------

    public DashboardDto getDashboard(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return new DashboardDto(
                getPocketSummaryByUserId(currentUser.getId()),
                getInvestmentSummaryByUserId(currentUser.getId()),
                getTotalOpenBillsByUserId(currentUser.getId())
        );
    }

    public FinancialSummaryDto getFinancialSummary(LocalDate startDate, LocalDate endDate,
                                                   Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return new FinancialSummaryDto(
                getIncomesByUserId(currentUser.getId(), startDate, endDate),
                getExpensesByUserId(currentUser.getId(), startDate, endDate)
        );
    }

    public List<MonthlyFinancialSummaryDto> getMonthlyFinancialSummary(LocalDate startDate, LocalDate endDate,
                                                                       Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return buildMonthlyFinancialSummary(
                getMonthlyIncomeByUserId(currentUser.getId(), startDate, endDate),
                getMonthlyExpenseByUserId(currentUser.getId(), startDate, endDate)
        );
    }

    // -------------------------------------------------------------------------
    // Public userId-based methods (GroupDashboardService)
    // -------------------------------------------------------------------------

    public List<CategorySummaryDto> getPocketSummaryByUserId(Long userId) {
        return pocketRepository.findAllByUserId(userId)
                .stream()
                .map(pocket -> {
                    BigDecimal balance = transactionRepository.calculateBalanceByPocketId(pocket.getId());
                    return PocketSummaryDto.of(pocket, balance);
                })
                .collect(Collectors.groupingBy(
                        PocketSummaryDto::type,
                        Collectors.reducing(BigDecimal.ZERO, PocketSummaryDto::balance, BigDecimal::add)
                ))
                .entrySet().stream()
                .map(e -> new CategorySummaryDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CategorySummaryDto::category))
                .toList();
    }

    public List<CategorySummaryDto> getInvestmentSummaryByUserId(Long userId) {
        return assetRepository.findAllByUserId(userId)
                .stream()
                .map(AssetSummaryDto::new)
                .collect(Collectors.groupingBy(
                        AssetSummaryDto::category,
                        Collectors.reducing(BigDecimal.ZERO, AssetSummaryDto::currentValue, BigDecimal::add)
                ))
                .entrySet().stream()
                .map(e -> new CategorySummaryDto(e.getKey().name(), e.getValue()))
                .sorted(Comparator.comparing(CategorySummaryDto::category))
                .toList();
    }

    public BigDecimal getTotalOpenBillsByUserId(Long userId) {
        BigDecimal totalInstallments = creditCardInstallmentRepository.sumInstallmentsByUserIdAndOpenBills(userId);
        BigDecimal totalRefunds = creditCardRefundRepository.sumRefundsByUserIdAndOpenBills(userId);
        return totalInstallments.subtract(totalRefunds);
    }

    public List<CategorySummaryDto> getIncomesByUserId(Long userId, LocalDate startDate, LocalDate endDate) {
        return mergeCategoryResults(
                transactionRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(userId, "INCOME", startDate, endDate),
                List.of()
        );
    }

    public List<CategorySummaryDto> getExpensesByUserId(Long userId, LocalDate startDate, LocalDate endDate) {
        return mergeCategoryResults(
                transactionRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(userId, "EXPENSE", startDate, endDate),
                creditCardInstallmentRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(userId, "EXPENSE", startDate, endDate)
        );
    }

    public Map<String, BigDecimal> getMonthlyIncomeByUserId(Long userId, LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> incomeByMonth = new HashMap<>();
        for (Object[] row : transactionRepository.sumAmountByMonthAndCategoryTypeAndUserIdAndPeriod(userId, startDate, endDate)) {
            String month = (String) row[0];
            String type = (String) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            if (CategoryType.INCOME.name().equals(type)) {
                incomeByMonth.merge(month, amount, BigDecimal::add);
            }
        }
        return incomeByMonth;
    }

    public Map<String, BigDecimal> getMonthlyExpenseByUserId(Long userId, LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> expenseByMonth = new HashMap<>();
        for (Object[] row : transactionRepository.sumAmountByMonthAndCategoryTypeAndUserIdAndPeriod(userId, startDate, endDate)) {
            String month = (String) row[0];
            String type = (String) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            if (CategoryType.EXPENSE.name().equals(type)) {
                expenseByMonth.merge(month, amount, BigDecimal::add);
            }
        }
        for (Object[] row : creditCardInstallmentRepository.sumExpenseAmountByMonthAndUserIdAndPeriod(userId, startDate, endDate)) {
            String month = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            expenseByMonth.merge(month, amount, BigDecimal::add);
        }
        return expenseByMonth;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public List<MonthlyFinancialSummaryDto> buildMonthlyFinancialSummary(
            Map<String, BigDecimal> incomeByMonth, Map<String, BigDecimal> expenseByMonth) {
        return Stream.concat(incomeByMonth.keySet().stream(), expenseByMonth.keySet().stream())
                .distinct()
                .sorted()
                .map(month -> new MonthlyFinancialSummaryDto(
                        month,
                        incomeByMonth.getOrDefault(month, BigDecimal.ZERO),
                        expenseByMonth.getOrDefault(month, BigDecimal.ZERO)
                ))
                .toList();
    }

    private List<CategorySummaryDto> mergeCategoryResults(List<Object[]> source1, List<Object[]> source2) {
        Map<String, BigDecimal> merged = new HashMap<>();

        for (Object[] row : source1) {
            merged.merge((String) row[0], (BigDecimal) row[2], BigDecimal::add);
        }

        for (Object[] row : source2) {
            merged.merge((String) row[0], (BigDecimal) row[1], BigDecimal::add);
        }

        return merged.entrySet().stream()
                .map(e -> new CategorySummaryDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CategorySummaryDto::category))
                .toList();
    }
}