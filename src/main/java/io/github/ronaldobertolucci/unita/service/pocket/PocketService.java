package io.github.ronaldobertolucci.unita.service.pocket;

import io.github.ronaldobertolucci.unita.dto.pocket.*;
import io.github.ronaldobertolucci.unita.model.employer.Employer;
import io.github.ronaldobertolucci.unita.model.finance.BankAccountType;
import io.github.ronaldobertolucci.unita.model.finance.BenefitType;
import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.finance.RecurrencePeriodicity;
import io.github.ronaldobertolucci.unita.model.pocket.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PocketService {

    private final PocketRepository pocketRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BenefitAccountRepository benefitAccountRepository;
    private final FgtsEmployerAccountRepository fgtsEmployerAccountRepository;
    private final CashRepository cashRepository;
    private final TransactionRepository transactionRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final BankAccountTypeRepository bankAccountTypeRepository;
    private final BenefitTypeRepository benefitTypeRepository;
    private final EmployerRepository employerRepository;
    private final RecurrencePeriodicityRepository recurrencePeriodicityRepository;

    // -------------------------------------------------------------------------
    // Pocket (geral)
    // -------------------------------------------------------------------------

    public List<PocketSummaryDto> findMyPockets(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return pocketRepository.findAllByUserId(currentUser.getId())
                .stream()
                .map(pocket -> {
                    BigDecimal balance = transactionRepository.calculateBalanceByPocketId(pocket.getId());
                    String label = resolvePocketLabel(pocket);
                    return PocketSummaryDto.of(pocket, label, balance);
                })
                .toList();
    }

    private String resolvePocketLabel(Pocket pocket) {
        return switch (pocket) {
            case BankAccount ba -> ba.getLegalEntity().getCorporateName() + " ..." + ba.getNumber().substring(Math.max(0, ba.getNumber().length() - 4));
            case BenefitAccount ba -> ba.getLegalEntity().getCorporateName() + " - " + ba.getBenefitType().getName();
            case FgtsEmployerAccount f -> "FGTS";
            case Cash c -> "Dinheiro em espécie";
            default -> "Pocket";
        };
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

        Transaction transaction = Transaction.builder()
                .pocket(pocket)
                .amount(dto.amount())
                .direction(dto.direction())
                .transactionDate(dto.transactionDate())
                .description(dto.description())
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

    // -------------------------------------------------------------------------
    // RecurringTransaction
    // -------------------------------------------------------------------------

    @Transactional
    public RecurringTransactionDto createRecurringTransaction(Long pocketId, RecurringTransactionCreateDto dto,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Pocket pocket = pocketRepository.findByIdAndUserId(pocketId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pocket not found"));

        RecurrencePeriodicity periodicity = recurrencePeriodicityRepository.findById(dto.periodicityId())
                .orElseThrow(() -> new EntityNotFoundException("Periodicity not found"));

        RecurringTransaction recurringTransaction = RecurringTransaction.builder()
                .pocket(pocket)
                .amount(dto.amount())
                .direction(dto.direction())
                .periodicity(periodicity)
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .description(dto.description())
                .build();

        recurringTransactionRepository.save(recurringTransaction);

        generateCurrentTransaction(recurringTransaction, pocket);

        return new RecurringTransactionDto(recurringTransaction);
    }

    public List<RecurringTransactionDto> findRecurringTransactions(Long pocketId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        if (!pocketRepository.existsByIdAndUserId(pocketId, currentUser.getId())) {
            throw new EntityNotFoundException("Pocket not found");
        }
        return recurringTransactionRepository.findAllByPocketId(pocketId)
                .stream()
                .map(RecurringTransactionDto::new)
                .toList();
    }

    @Transactional
    public RecurringTransactionDto updateRecurringTransaction(Long pocketId, Long recurringId,
                                                              RecurringTransactionUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        if (!pocketRepository.existsByIdAndUserId(pocketId, currentUser.getId())) {
            throw new EntityNotFoundException("Pocket not found");
        }
        RecurringTransaction recurringTransaction = recurringTransactionRepository
                .findByIdAndPocketId(recurringId, pocketId)
                .orElseThrow(() -> new EntityNotFoundException("Recurring transaction not found"));

        recurringTransaction.setAmount(dto.amount());
        return new RecurringTransactionDto(recurringTransactionRepository.save(recurringTransaction));
    }

    @Transactional
    public void deleteRecurringTransaction(Long pocketId, Long recurringId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        if (!pocketRepository.existsByIdAndUserId(pocketId, currentUser.getId())) {
            throw new EntityNotFoundException("Pocket not found");
        }
        RecurringTransaction recurringTransaction = recurringTransactionRepository.findByIdAndPocketId(recurringId, pocketId)
                .orElseThrow(() -> new EntityNotFoundException("Recurring transaction not found"));
        recurringTransactionRepository.delete(recurringTransaction);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void generateCurrentTransaction(RecurringTransaction recurringTransaction, Pocket pocket) {
        Transaction transaction = Transaction.builder()
                .pocket(pocket)
                .amount(recurringTransaction.getAmount())
                .direction(recurringTransaction.getDirection())
                .transactionDate(LocalDate.now())
                .description(recurringTransaction.getDescription())
                .build();
        transactionRepository.save(transaction);
    }
}