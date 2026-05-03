package io.github.ronaldobertolucci.unita.service.pocket;

import io.github.ronaldobertolucci.unita.dto.pocket.*;
import io.github.ronaldobertolucci.unita.model.employer.Employer;
import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;
import io.github.ronaldobertolucci.unita.model.finance.*;
import io.github.ronaldobertolucci.unita.model.pocket.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.category.CategoryService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PocketService {

    private final PocketRepository pocketRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BenefitAccountRepository benefitAccountRepository;
    private final FgtsEmployerAccountRepository fgtsEmployerAccountRepository;
    private final CashRepository cashRepository;
    private final TransactionRepository transactionRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final BankAccountTypeRepository bankAccountTypeRepository;
    private final BenefitTypeRepository benefitTypeRepository;
    private final EmployerRepository employerRepository;
    private final CategoryService categoryService;

    // -------------------------------------------------------------------------
    // Pocket (geral)
    // -------------------------------------------------------------------------

    public List<PocketSummaryDto> findMyPockets(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return pocketRepository.findAllByUserId(currentUser.getId())
                .stream()
                .sorted(Comparator.comparing(Pocket::getLabel))
                .map(pocket -> {
                    BigDecimal balance = transactionRepository.calculateBalanceByPocketId(pocket.getId());
                    return PocketSummaryDto.of(pocket, balance);
                })
                .toList();
    }

    // -------------------------------------------------------------------------
    // BankAccount
    // -------------------------------------------------------------------------

    @Transactional
    public BankAccountDto createBankAccount(BankAccountCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        LegalEntity legalEntity = legalEntityRepository.findById(dto.legalEntityId())
                .orElseThrow(() -> new EntityNotFoundException("Legal entity not found"));

        BankAccountType bankAccountType = bankAccountTypeRepository.findById(dto.bankAccountTypeId())
                .orElseThrow(() -> new EntityNotFoundException("Bank account type not found"));

        BankAccount bankAccount = BankAccount.builder()
                .user(currentUser)
                .legalEntity(legalEntity)
                .number(dto.number())
                .agency(dto.agency())
                .bankAccountType(bankAccountType)
                .status(BankAccountStatus.ACTIVE)
                .build();

        return new BankAccountDto(bankAccountRepository.save(bankAccount));
    }

    public BankAccountDto findBankAccountById(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        BankAccount bankAccount = bankAccountRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Bank account not found"));
        return new BankAccountDto(bankAccount);
    }

    @Transactional
    public BankAccountDto updateBankAccount(Long id, BankAccountUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        BankAccount bankAccount = bankAccountRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Bank account not found"));

        BigDecimal balance = findBalance(id, authentication);
        if (dto.status() != BankAccountStatus.ACTIVE && balance.compareTo(BigDecimal.ZERO) != 0)
            throw new IllegalStateException("Pocket must be empty to be deactivated.");

        bankAccount.setStatus(dto.status());
        return new BankAccountDto(bankAccountRepository.save(bankAccount));
    }

    @Transactional
    public void deleteBankAccount(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        if (!bankAccountRepository.existsByIdAndUserId(id, currentUser.getId())) {
            throw new EntityNotFoundException("Bank account not found");
        }
        bankAccountRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // BenefitAccount
    // -------------------------------------------------------------------------

    @Transactional
    public BenefitAccountDto createBenefitAccount(BenefitAccountCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        LegalEntity legalEntity = legalEntityRepository.findById(dto.legalEntityId())
                .orElseThrow(() -> new EntityNotFoundException("Legal entity not found"));

        BenefitType benefitType = benefitTypeRepository.findById(dto.benefitTypeId())
                .orElseThrow(() -> new EntityNotFoundException("Benefit type not found"));

        BenefitAccount benefitAccount = BenefitAccount.builder()
                .user(currentUser)
                .legalEntity(legalEntity)
                .benefitType(benefitType)
                .status(BenefitAccountStatus.ACTIVE)
                .build();

        return new BenefitAccountDto(benefitAccountRepository.save(benefitAccount));
    }

    public BenefitAccountDto findBenefitAccountById(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        BenefitAccount benefitAccount = benefitAccountRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Benefit account not found"));
        return new BenefitAccountDto(benefitAccount);
    }

    @Transactional
    public BenefitAccountDto updateBenefitAccount(Long id, BenefitAccountUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        BenefitAccount benefitAccount = benefitAccountRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Benefit account not found"));

        BigDecimal balance = findBalance(id, authentication);
        if (dto.status() != BenefitAccountStatus.ACTIVE && balance.compareTo(BigDecimal.ZERO) != 0)
            throw new IllegalStateException("Pocket must be empty to be deactivated.");

        benefitAccount.setStatus(dto.status());
        return new BenefitAccountDto(benefitAccountRepository.save(benefitAccount));
    }

    @Transactional
    public void deleteBenefitAccount(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        if (!benefitAccountRepository.existsByIdAndUserId(id, currentUser.getId())) {
            throw new EntityNotFoundException("Benefit account not found");
        }
        benefitAccountRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // FgtsEmployerAccount
    // -------------------------------------------------------------------------

    @Transactional
    public FgtsEmployerAccountDto createFgtsEmployerAccount(FgtsEmployerAccountCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        Employer employer = employerRepository.findById(dto.employerId())
                .orElseThrow(() -> new EntityNotFoundException("Employer not found"));

        FgtsEmployerAccount fgtsAccount = FgtsEmployerAccount.builder()
                .user(currentUser)
                .employer(employer)
                .admissionDate(dto.admissionDate())
                .dismissalDate(dto.dismissalDate())
                .status(FgtsEmployerAccountStatus.ACTIVE)
                .build();

        return new FgtsEmployerAccountDto(fgtsEmployerAccountRepository.save(fgtsAccount));
    }

    public FgtsEmployerAccountDto findFgtsEmployerAccountById(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        FgtsEmployerAccount fgtsAccount = fgtsEmployerAccountRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("FGTS employer account not found"));
        return new FgtsEmployerAccountDto(fgtsAccount);
    }

    @Transactional
    public FgtsEmployerAccountDto updateFgtsEmployerAccount(Long id, FgtsEmployerAccountUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        FgtsEmployerAccount fgtsAccount = fgtsEmployerAccountRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("FGTS employer account not found"));

        BigDecimal balance = findBalance(id, authentication);
        if (dto.status() != FgtsEmployerAccountStatus.ACTIVE && balance.compareTo(BigDecimal.ZERO) != 0)
            throw new IllegalStateException("Pocket must be empty to be deactivated.");

        fgtsAccount.setStatus(dto.status());
        fgtsAccount.setDismissalDate(dto.dismissalDate());
        return new FgtsEmployerAccountDto(fgtsEmployerAccountRepository.save(fgtsAccount));
    }

    @Transactional
    public void deleteFgtsEmployerAccount(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        if (!fgtsEmployerAccountRepository.existsByIdAndUserId(id, currentUser.getId())) {
            throw new EntityNotFoundException("FGTS employer account not found");
        }
        fgtsEmployerAccountRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Cash
    // -------------------------------------------------------------------------

    @Transactional
    public CashDto createCash(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        if (cashRepository.existsByUserId(currentUser.getId())) {
            throw new IllegalStateException("Cash wallet already exists for this user");
        }

        Cash cash = new Cash();
        cash.setUser(currentUser);
        Cash saved = cashRepository.save(cash);

        return new CashDto(saved, BigDecimal.ZERO);
    }

    public CashDto findCash(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Cash cash = cashRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Cash wallet not found"));
        BigDecimal balance = transactionRepository.calculateBalanceByPocketId(cash.getId());
        return new CashDto(cash, balance);
    }

    // -------------------------------------------------------------------------
    // Transaction
    // -------------------------------------------------------------------------

    @Transactional
    public TransactionDto createTransaction(Long pocketId, TransactionCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Pocket pocket = pocketRepository.findByIdAndUserId(pocketId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pocket not found"));

        Set<CategoryType> allowed = dto.direction() == Direction.INCOME
                ? EnumSet.of(CategoryType.INCOME, CategoryType.NEUTRAL)
                : EnumSet.of(CategoryType.EXPENSE, CategoryType.NEUTRAL);
        Category category = categoryService.resolveCategory(dto.categoryId(), currentUser, allowed);

        Transaction transaction = Transaction.builder()
                .pocket(pocket)
                .amount(dto.amount())
                .direction(dto.direction())
                .transactionDate(dto.transactionDate())
                .description(dto.description())
                .category(category)
                .build();

        return new TransactionDto(transactionRepository.save(transaction));
    }

    public List<TransactionDto> findTransactions(Long pocketId, LocalDate startDate, LocalDate endDate,
                                                 Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        if (!pocketRepository.existsByIdAndUserId(pocketId, currentUser.getId())) {
            throw new EntityNotFoundException("Pocket not found");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }
        return transactionRepository.findAllByPocketIdAndPeriod(pocketId, startDate, endDate)
                .stream()
                .map(TransactionDto::new)
                .toList();
    }

    public BigDecimal findBalance(Long pocketId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        if (!pocketRepository.existsByIdAndUserId(pocketId, currentUser.getId())) {
            throw new EntityNotFoundException("Pocket not found");
        }
        return transactionRepository.calculateBalanceByPocketId(pocketId);
    }

    @Transactional
    public void deleteTransaction(Long pocketId, Long transactionId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        if (!pocketRepository.existsByIdAndUserId(pocketId, currentUser.getId())) {
            throw new EntityNotFoundException("Pocket not found");
        }
        Transaction transaction = transactionRepository.findByIdAndPocketId(transactionId, pocketId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
        transactionRepository.delete(transaction);
    }
}