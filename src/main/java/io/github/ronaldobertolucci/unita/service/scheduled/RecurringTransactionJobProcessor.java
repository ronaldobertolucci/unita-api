package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.pocket.RecurringTransaction;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import io.github.ronaldobertolucci.unita.repository.RecurringTransactionRepository;
import io.github.ronaldobertolucci.unita.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringTransactionJobProcessor {

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final TransactionRepository transactionRepository;

    // REQUIRES_NEW garante que cada registro é commitado de forma independente.
    // A entidade é re-buscada por ID dentro desta transação, permitindo que
    // associações lazy (pocket, category) sejam carregadas corretamente.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long recurringTransactionId, LocalDate today) {
        RecurringTransaction rt = recurringTransactionRepository.findById(recurringTransactionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "RecurringTransaction not found: " + recurringTransactionId));

        Transaction transaction = Transaction.builder()
                .pocket(rt.getPocket())
                .amount(rt.getAmount())
                .direction(rt.getDirection())
                .transactionDate(today)
                .description(rt.getDescription())
                .category(rt.getCategory())
                .build();

        transactionRepository.save(transaction);

        rt.setLastGeneratedDate(today);
        recurringTransactionRepository.save(rt);
    }
}