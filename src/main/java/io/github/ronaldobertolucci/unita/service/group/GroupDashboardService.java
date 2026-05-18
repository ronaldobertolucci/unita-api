package io.github.ronaldobertolucci.unita.service.group;

import io.github.ronaldobertolucci.unita.dto.dashboard.*;
import io.github.ronaldobertolucci.unita.model.group.GroupSharePermission;
import io.github.ronaldobertolucci.unita.model.group.ShareType;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.GroupMembershipRepository;
import io.github.ronaldobertolucci.unita.repository.GroupSharePermissionRepository;
import io.github.ronaldobertolucci.unita.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class GroupDashboardService {

    private final GroupMembershipRepository membershipRepository;
    private final GroupSharePermissionRepository permissionRepository;
    private final DashboardService dashboardService;

    // -------------------------------------------------------------------------
    // GET /groups/{groupId}/dashboard
    // -------------------------------------------------------------------------

    public GroupDashboardDto getGroupDashboard(Long groupId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateMembership(currentUser.getId(), groupId);

        List<GroupMemberDashboardDto> members = membershipRepository.findByGroupIdWithUsers(groupId)
                .stream()
                .map(membership -> buildMemberDashboard(membership.getUser(), groupId))
                .toList();

        return new GroupDashboardDto(members);
    }

    private GroupMemberDashboardDto buildMemberDashboard(User member, Long groupId) {
        Set<ShareType> enabled = getEnabledPermissions(member.getId(), groupId);

        return new GroupMemberDashboardDto(
                GroupMemberUserDto.from(member),
                enabled.contains(ShareType.BALANCE)
                        ? dashboardService.getPocketSummaryByUserId(member.getId()) : null,
                enabled.contains(ShareType.INVESTMENTS)
                        ? dashboardService.getInvestmentSummaryByUserId(member.getId()) : null,
                enabled.contains(ShareType.CREDIT_CARD_BILLS)
                        ? dashboardService.getTotalOpenBillsByUserId(member.getId()) : null
        );
    }

    // -------------------------------------------------------------------------
    // GET /groups/{groupId}/dashboard/summary
    // -------------------------------------------------------------------------

    public GroupFinancialSummaryDto getGroupFinancialSummary(Long groupId, LocalDate startDate,
                                                              LocalDate endDate, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateMembership(currentUser.getId(), groupId);

        List<GroupMemberFinancialSummaryDto> members = membershipRepository.findByGroupIdWithUsers(groupId)
                .stream()
                .map(membership -> buildMemberFinancialSummary(membership.getUser(), groupId, startDate, endDate))
                .toList();

        return new GroupFinancialSummaryDto(members);
    }

    private GroupMemberFinancialSummaryDto buildMemberFinancialSummary(User member, Long groupId,
                                                                        LocalDate startDate, LocalDate endDate) {
        Set<ShareType> enabled = getEnabledPermissions(member.getId(), groupId);

        return new GroupMemberFinancialSummaryDto(
                GroupMemberUserDto.from(member),
                enabled.contains(ShareType.INCOME_BY_CATEGORY)
                        ? dashboardService.getIncomesByUserId(member.getId(), startDate, endDate) : null,
                enabled.contains(ShareType.EXPENSES_BY_CATEGORY)
                        ? dashboardService.getExpensesByUserId(member.getId(), startDate, endDate) : null
        );
    }

    // -------------------------------------------------------------------------
    // GET /groups/{groupId}/dashboard/monthly
    // -------------------------------------------------------------------------

    public GroupMonthlyDto getGroupMonthlyFinancialSummary(Long groupId, LocalDate startDate,
                                                            LocalDate endDate, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateMembership(currentUser.getId(), groupId);

        List<GroupMemberMonthlyDto> members = membershipRepository.findByGroupIdWithUsers(groupId)
                .stream()
                .map(membership -> buildMemberMonthly(membership.getUser(), groupId, startDate, endDate))
                .toList();

        return new GroupMonthlyDto(members);
    }

    private GroupMemberMonthlyDto buildMemberMonthly(User member, Long groupId,
                                                      LocalDate startDate, LocalDate endDate) {
        Set<ShareType> enabled = getEnabledPermissions(member.getId(), groupId);
        boolean hasIncome = enabled.contains(ShareType.INCOME_BY_CATEGORY);
        boolean hasExpense = enabled.contains(ShareType.EXPENSES_BY_CATEGORY);

        if (!hasIncome && !hasExpense) {
            return new GroupMemberMonthlyDto(GroupMemberUserDto.from(member), null);
        }

        Map<String, BigDecimal> incomeByMonth = hasIncome
                ? dashboardService.getMonthlyIncomeByUserId(member.getId(), startDate, endDate)
                : Map.of();

        Map<String, BigDecimal> expenseByMonth = hasExpense
                ? dashboardService.getMonthlyExpenseByUserId(member.getId(), startDate, endDate)
                : Map.of();

        List<GroupMonthlyFinancialSummaryDto> monthly = Stream
                .concat(incomeByMonth.keySet().stream(), expenseByMonth.keySet().stream())
                .distinct()
                .sorted()
                .map(month -> new GroupMonthlyFinancialSummaryDto(
                        month,
                        hasIncome ? incomeByMonth.getOrDefault(month, BigDecimal.ZERO) : null,
                        hasExpense ? expenseByMonth.getOrDefault(month, BigDecimal.ZERO) : null
                ))
                .toList();

        return new GroupMemberMonthlyDto(GroupMemberUserDto.from(member), monthly);
    }

    public GroupIssuerRiskDto getGroupIssuerRiskSummary(Long groupId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateMembership(currentUser.getId(), groupId);

        List<GroupMemberIssuerRiskDto> members = membershipRepository.findByGroupIdWithUsers(groupId)
                .stream()
                .map(membership -> buildMemberIssuerRisk(membership.getUser(), groupId))
                .toList();

        return new GroupIssuerRiskDto(members);
    }

    private GroupMemberIssuerRiskDto buildMemberIssuerRisk(User member, Long groupId) {
        Set<ShareType> enabled = getEnabledPermissions(member.getId(), groupId);

        return new GroupMemberIssuerRiskDto(
                GroupMemberUserDto.from(member),
                enabled.contains(ShareType.INVESTMENTS)
                        ? dashboardService.getIssuerRiskSummaryByUserId(member.getId()) : null
        );
    }

    public GroupIndexerSummaryDto getGroupIndexerSummary(Long groupId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateMembership(currentUser.getId(), groupId);

        List<GroupMemberIndexerSummaryDto> members = membershipRepository.findByGroupIdWithUsers(groupId)
                .stream()
                .map(membership -> buildMemberIndexerSummary(membership.getUser(), groupId))
                .toList();

        return new GroupIndexerSummaryDto(members);
    }

    private GroupMemberIndexerSummaryDto buildMemberIndexerSummary(User member, Long groupId) {
        Set<ShareType> enabled = getEnabledPermissions(member.getId(), groupId);

        return new GroupMemberIndexerSummaryDto(
                GroupMemberUserDto.from(member),
                enabled.contains(ShareType.INVESTMENTS)
                        ? dashboardService.getIndexerSummaryByUserId(member.getId()) : null
        );
    }

    public GroupLiquiditySummaryDto getGroupLiquiditySummary(Long groupId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateMembership(currentUser.getId(), groupId);

        List<GroupMemberLiquiditySummaryDto> members = membershipRepository.findByGroupIdWithUsers(groupId)
                .stream()
                .map(membership -> buildMemberLiquiditySummary(membership.getUser(), groupId))
                .toList();

        return new GroupLiquiditySummaryDto(members);
    }

    private GroupMemberLiquiditySummaryDto buildMemberLiquiditySummary(User member, Long groupId) {
        Set<ShareType> enabled = getEnabledPermissions(member.getId(), groupId);

        return new GroupMemberLiquiditySummaryDto(
                GroupMemberUserDto.from(member),
                enabled.contains(ShareType.INVESTMENTS)
                        ? dashboardService.getLiquidityTypeSummaryByUserId(member.getId()) : null
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Set<ShareType> getEnabledPermissions(Long userId, Long groupId) {
        return permissionRepository.findAllByGroupIdAndUserId(groupId, userId)
                .stream()
                .filter(GroupSharePermission::isEnabled)
                .map(GroupSharePermission::getShareType)
                .collect(Collectors.toSet());
    }

    private void validateMembership(Long userId, Long groupId) {
        if (!membershipRepository.existsByUserIdAndGroupId(userId, groupId)) {
            throw new AccessDeniedException("You are not a member of this group");
        }
    }
}