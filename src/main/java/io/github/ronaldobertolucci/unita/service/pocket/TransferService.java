package io.github.ronaldobertolucci.unita.service.pocket;

import io.github.ronaldobertolucci.unita.dto.pocket.TransactionDto;
import io.github.ronaldobertolucci.unita.dto.pocket.TransferCreateDto;
import io.github.ronaldobertolucci.unita.dto.pocket.TransferDto;
import io.github.ronaldobertolucci.unita.model.finance.Category;
import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.pocket.BankAccount;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.pocket.Pocket;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.GroupMembershipRepository;
import io.github.ronaldobertolucci.unita.repository.PocketRepository;
import io.github.ronaldobertolucci.unita.repository.TransactionRepository;
import io.github.ronaldobertolucci.unita.service.category.CategoryService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final PocketRepository pocketRepository;
    private final TransactionRepository transactionRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final CategoryService categoryService;

    @Transactional
    public TransferDto transfer(TransferCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Pocket source = pocketRepository.findByIdAndUserId(dto.sourcePocketId(), currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Source pocket not found"));

        if (!(source instanceof BankAccount) && !(source instanceof Cash)) {
            throw new IllegalArgumentException("Source pocket must be a BankAccount or Cash");
        }

        Pocket target = pocketRepository.findById(dto.targetPocketId())
                .orElseThrow(() -> new EntityNotFoundException("Target pocket not found"));

        if (!(target instanceof BankAccount) && !(target instanceof Cash)) {
            throw new IllegalArgumentException("Target pocket must be a BankAccount or Cash");
        }

        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Source and target pockets must be different");
        }

        if (!groupMembershipRepository.existsSharedGroup(currentUser.getId(), target.getUser().getId())) {
            throw new IllegalArgumentException("Source and target pocket owners must share a group");
        }

        BigDecimal balance = transactionRepository.calculateBalanceByPocketId(source.getId());
        if (balance.compareTo(dto.amount()) < 0) {
            throw new IllegalArgumentException("Insufficient balance in source pocket");
        }

        LocalDate today = LocalDate.now();

        Category sentCategory = categoryService.findSystemByName("Transferência Enviada");
        Category receivedCategory = categoryService.findSystemByName("Transferência Recebida");

        Transaction sourceTransaction = Transaction.builder()
                .pocket(source)
                .amount(dto.amount())
                .direction(Direction.EXPENSE)
                .transactionDate(today)
                .description(dto.description())
                .category(sentCategory)
                .build();

        Transaction targetTransaction = Transaction.builder()
                .pocket(target)
                .amount(dto.amount())
                .direction(Direction.INCOME)
                .transactionDate(today)
                .description(dto.description())
                .category(receivedCategory)
                .build();

        return new TransferDto(
                new TransactionDto(transactionRepository.save(sourceTransaction)),
                new TransactionDto(transactionRepository.save(targetTransaction))
        );
    }
}