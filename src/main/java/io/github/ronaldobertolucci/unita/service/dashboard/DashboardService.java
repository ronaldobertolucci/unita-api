package io.github.ronaldobertolucci.unita.service.dashboard;

import io.github.ronaldobertolucci.unita.dto.dashboard.CategorySummaryDto;
import io.github.ronaldobertolucci.unita.dto.dashboard.DashboardDto;
import io.github.ronaldobertolucci.unita.dto.dashboard.FinancialSummaryDto;
import io.github.ronaldobertolucci.unita.dto.dashboard.MonthlyFinancialSummaryDto;
import io.github.ronaldobertolucci.unita.dto.investment.AssetSummaryDto;
import io.github.ronaldobertolucci.unita.dto.pocket.PocketSummaryDto;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.CreditCardInstallmentRepository;
import io.github.ronaldobertolucci.unita.repository.CreditCardRefundRepository;
import io.github.ronaldobertolucci.unita.repository.TransactionRepository;
import io.github.ronaldobertolucci.unita.service.investment.AssetService;
import io.github.ronaldobertolucci.unita.service.pocket.PocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PocketService pocketService;
    private final AssetService assetService;
    private final TransactionRepository transactionRepository;
    private final CreditCardInstallmentRepository creditCardInstallmentRepository;
    private final CreditCardRefundRepository creditCardRefundRepository;

    public DashboardDto getDashboard(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        List<CategorySummaryDto> pockets = pocketService.findMyPockets(authentication)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        PocketSummaryDto::type,
                        java.util.stream.Collectors.reducing(BigDecimal.ZERO, PocketSummaryDto::balance, BigDecimal::add)
                ))
                .entrySet().stream()
                .map(e -> new CategorySummaryDto(e.getKey(), e.getValue()))
                .sorted(java.util.Comparator.comparing(CategorySummaryDto::category))
                .toList();

        List<CategorySummaryDto> investments = assetService.findAll(authentication)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        AssetSummaryDto::category,
                        java.util.stream.Collectors.reducing(BigDecimal.ZERO, AssetSummaryDto::currentValue, BigDecimal::add)
                ))
                .entrySet().stream()
                .map(e -> new CategorySummaryDto(e.getKey().name(), e.getValue()))
                .sorted(java.util.Comparator.comparing(CategorySummaryDto::category))
                .toList();

        BigDecimal totalInstallments = creditCardInstallmentRepository
                .sumInstallmentsByUserIdAndOpenBills(currentUser.getId());
        BigDecimal totalRefunds = creditCardRefundRepository
                .sumRefundsByUserIdAndOpenBills(currentUser.getId());
        BigDecimal totalOpenBills = totalInstallments.subtract(totalRefunds);

        return new DashboardDto(pockets, investments, totalOpenBills);
    }

    public FinancialSummaryDto getFinancialSummary(LocalDate startDate, LocalDate endDate,
                                                    Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        List<CategorySummaryDto> incomes = mergeCategoryResults(
                transactionRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(
                        currentUser.getId(), CategoryType.INCOME.name(), startDate, endDate),
                List.of()
        );

        List<CategorySummaryDto> expenses = mergeCategoryResults(
                transactionRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(
                        currentUser.getId(), CategoryType.EXPENSE.name(), startDate, endDate),
                creditCardInstallmentRepository.sumAmountByCategoryTypeAndUserIdAndPeriod(
                        currentUser.getId(), CategoryType.EXPENSE.name(), startDate, endDate)
        );

        return new FinancialSummaryDto(incomes, expenses);
    }

    private List<CategorySummaryDto> mergeCategoryResults(List<Object[]> source1, List<Object[]> source2) {
        Map<String, BigDecimal> merged = new HashMap<>();

        for (Object[] row : source1) {
            String category = (String) row[0];
            BigDecimal amount = (BigDecimal) row[2]; // row[1] é o type
            merged.merge(category, amount, BigDecimal::add);
        }

        for (Object[] row : source2) {
            String category = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1]; // installments só retorna (name, sum)
            merged.merge(category, amount, BigDecimal::add);
        }

        return merged.entrySet().stream()
                .map(e -> new CategorySummaryDto(e.getKey(), e.getValue()))
                .sorted(java.util.Comparator.comparing(CategorySummaryDto::category))
                .toList();
    }

    public List<MonthlyFinancialSummaryDto> getMonthlyFinancialSummary(LocalDate startDate, LocalDate endDate,
                                                                       Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        List<Object[]> transactionRows = transactionRepository
                .sumAmountByMonthAndCategoryTypeAndUserIdAndPeriod(currentUser.getId(), startDate, endDate);

        List<Object[]> installmentRows = creditCardInstallmentRepository
                .sumExpenseAmountByMonthAndUserIdAndPeriod(currentUser.getId(), startDate, endDate);

        Map<String, BigDecimal> incomeByMonth = new HashMap<>();
        Map<String, BigDecimal> expenseByMonth = new HashMap<>();

        for (Object[] row : transactionRows) {
            String month = (String) row[0];
            String type = (String) row[1]; // native query retorna String
            BigDecimal amount = (BigDecimal) row[2];

            if (CategoryType.INCOME.name().equals(type)) {
                incomeByMonth.merge(month, amount, BigDecimal::add);
            } else if (CategoryType.EXPENSE.name().equals(type)) {
                expenseByMonth.merge(month, amount, BigDecimal::add);
            }
        }

        for (Object[] row : installmentRows) {
            String month = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            expenseByMonth.merge(month, amount, BigDecimal::add);
        }

        return java.util.stream.Stream.concat(incomeByMonth.keySet().stream(), expenseByMonth.keySet().stream())
                .distinct()
                .sorted()
                .map(month -> new MonthlyFinancialSummaryDto(
                        month,
                        incomeByMonth.getOrDefault(month, BigDecimal.ZERO),
                        expenseByMonth.getOrDefault(month, BigDecimal.ZERO)
                ))
                .toList();
    }
}