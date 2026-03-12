package io.github.ronaldobertolucci.unita.service.group;

import io.github.ronaldobertolucci.unita.dto.group.*;
import io.github.ronaldobertolucci.unita.dto.investment.AssetDetailDto;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBill;
import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;
import io.github.ronaldobertolucci.unita.model.group.GroupSharePermission;
import io.github.ronaldobertolucci.unita.model.group.ShareType;
import io.github.ronaldobertolucci.unita.model.pocket.Pocket;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupShareService {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final GroupSharePermissionRepository groupSharePermissionRepository;
    private final PocketRepository pocketRepository;
    private final TransactionRepository transactionRepository;
    private final CreditCardBillRepository creditCardBillRepository;
    private final CreditCardInstallmentRepository creditCardInstallmentRepository;
    private final AssetRepository assetRepository;

    @Transactional
    public List<GroupSharePermissionDto> updatePermissions(Long groupId,
                                                           GroupSharePermissionsUpdateDto dto,
                                                           Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateGroupExists(groupId);
        validateMembership(currentUser.getId(), groupId);

        Map<ShareType, GroupSharePermission> existing = groupSharePermissionRepository
                .findAllByGroupIdAndUserId(groupId, currentUser.getId())
                .stream()
                .collect(Collectors.toMap(GroupSharePermission::getShareType, Function.identity()));

        List<GroupSharePermission> toSave = new ArrayList<>();

        for (GroupSharePermissionUpdateItemDto item : dto.permissions()) {
            GroupSharePermission permission = existing.getOrDefault(
                item.shareType(),
                GroupSharePermission.builder()
                    .group(groupRepository.getReferenceById(groupId))
                    .user(currentUser)
                    .shareType(item.shareType())
                    .build()
            );
            permission.setEnabled(item.enabled());
            toSave.add(permission);
        }

        return groupSharePermissionRepository.saveAll(toSave)
                .stream()
                .map(GroupSharePermissionDto::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupMemberBalanceDto> getBalance(Long groupId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateGroupExists(groupId);
        validateMembership(currentUser.getId(), groupId);

        List<Long> enabledUserIds = groupSharePermissionRepository
                .findEnabledUserIdsByGroupIdAndShareType(groupId, ShareType.BALANCE);

        Map<Long, String> memberNames = resolveMemberNames(groupId, enabledUserIds);

        return enabledUserIds.stream().map(userId -> {
            List<Pocket> pockets = pocketRepository.findAllByUserId(userId);
            List<GroupPocketDto> pocketDtos = pockets.stream().map(pocket -> {
                BigDecimal balance = transactionRepository.calculateBalanceByPocketId(pocket.getId());
                return GroupPocketDto.from(pocket, balance);
            }).toList();
            return new GroupMemberBalanceDto(memberNames.get(userId), pocketDtos);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<GroupMemberCreditCardBillsDto> getCreditCardBills(Long groupId,
                                                                   Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateGroupExists(groupId);
        validateMembership(currentUser.getId(), groupId);

        List<Long> enabledUserIds = groupSharePermissionRepository
                .findEnabledUserIdsByGroupIdAndShareType(groupId, ShareType.CREDIT_CARD_BILLS);

        Map<Long, String> memberNames = resolveMemberNames(groupId, enabledUserIds);

        return enabledUserIds.stream().map(userId -> {
            List<CreditCardBill> bills = creditCardBillRepository.findAllByUserId(userId);
            List<GroupCreditCardBillDto> billDtos = bills.stream().map(bill -> {
                BigDecimal total = creditCardInstallmentRepository.sumAmountByBillId(bill.getId());
                return new GroupCreditCardBillDto(
                    bill.getId(),
                    bill.getCreditCard().getLegalEntity().getCorporateName(),
                    bill.getCreditCard().getLastFourDigits(),
                    bill.getClosingDate(),
                    bill.getDueDate(),
                    bill.getStatus(),
                    total
                );
            }).toList();
            return new GroupMemberCreditCardBillsDto(memberNames.get(userId), billDtos);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<GroupMemberInvestmentsDto> getInvestments(Long groupId,
                                                           Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateGroupExists(groupId);
        validateMembership(currentUser.getId(), groupId);

        List<Long> enabledUserIds = groupSharePermissionRepository
                .findEnabledUserIdsByGroupIdAndShareType(groupId, ShareType.INVESTMENTS);

        Map<Long, String> memberNames = resolveMemberNames(groupId, enabledUserIds);

        return enabledUserIds.stream().map(userId -> {
            List<AssetDetailDto> assetDtos = assetRepository.findAllByUserIdWithDetails(userId)
                    .stream()
                    .map(AssetDetailDto::new)
                    .toList();
            return new GroupMemberInvestmentsDto(memberNames.get(userId), assetDtos);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<GroupMemberCategoryAmountDto> getExpenses(Long groupId,
                                                           LocalDate startDate,
                                                           LocalDate endDate,
                                                           Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateGroupExists(groupId);
        validateMembership(currentUser.getId(), groupId);
        validateDateRange(startDate, endDate);

        List<Long> enabledUserIds = groupSharePermissionRepository
                .findEnabledUserIdsByGroupIdAndShareType(groupId, ShareType.EXPENSES_BY_CATEGORY);

        Map<Long, String> memberNames = resolveMemberNames(groupId, enabledUserIds);

        return buildCategoryAmountList(enabledUserIds, memberNames, "EXPENSE", startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<GroupMemberCategoryAmountDto> getIncome(Long groupId,
                                                         LocalDate startDate,
                                                         LocalDate endDate,
                                                         Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        validateGroupExists(groupId);
        validateMembership(currentUser.getId(), groupId);
        validateDateRange(startDate, endDate);

        List<Long> enabledUserIds = groupSharePermissionRepository
                .findEnabledUserIdsByGroupIdAndShareType(groupId, ShareType.INCOME_BY_CATEGORY);

        Map<Long, String> memberNames = resolveMemberNames(groupId, enabledUserIds);

        return buildCategoryAmountList(enabledUserIds, memberNames, "INCOME", startDate, endDate);
    }

    // --- private helpers ---

    private void validateGroupExists(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new EntityNotFoundException("Group not found with id: " + groupId);
        }
    }

    private void validateMembership(Long userId, Long groupId) {
        if (!groupMembershipRepository.existsByUserIdAndGroupId(userId, groupId)) {
            throw new IllegalArgumentException("User is not a member of this group");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }
    }

    private Map<Long, String> resolveMemberNames(Long groupId, List<Long> userIds) {
        return groupMembershipRepository.findByGroupIdWithUsers(groupId)
                .stream()
                .filter(gm -> userIds.contains(gm.getUser().getId()))
                .collect(Collectors.toMap(
                    gm -> gm.getUser().getId(),
                    gm -> gm.getUser().getFirstName() + " " + gm.getUser().getLastName()
                ));
    }

    private List<GroupMemberCategoryAmountDto> buildCategoryAmountList(List<Long> userIds,
                                                                        Map<Long, String> memberNames,
                                                                        String direction,
                                                                        LocalDate startDate,
                                                                        LocalDate endDate) {
        return userIds.stream().map(userId -> {
            List<Object[]> rows = transactionRepository
                    .sumAmountByCategoryAndUserIdAndDirection(userId, direction, startDate, endDate);
            List<CategoryAmountDto> categories = rows.stream()
                    .map(row -> new CategoryAmountDto(
                        (String) row[0],
                        (BigDecimal) row[1]
                    ))
                    .toList();
            return new GroupMemberCategoryAmountDto(memberNames.get(userId), categories);
        }).toList();
    }
}