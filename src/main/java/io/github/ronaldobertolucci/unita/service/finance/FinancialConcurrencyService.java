package io.github.ronaldobertolucci.unita.service.finance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

/**
 * Transaction-scoped database protocol: cards first (ascending ID), then pockets
 * (ascending ID). Lock before reading bills/composition or calculating balances.
 * Scalar native queries avoid JOINED pocket outer joins and stale entity snapshots.
 * Investment writers intentionally retain their previous behavior.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class FinancialConcurrencyService {
    private final EntityManager entityManager;

    private void configureWait() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            if ("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET LOCAL lock_timeout = '5s'");
                }
            }
        });
    }

    public void lockOwnedCard(Long id, Long userId) {
        configureWait();
        if (entityManager.createNativeQuery("SELECT id FROM credit_cards WHERE id = :id AND user_id = :userId FOR UPDATE")
                .setParameter("id", id).setParameter("userId", userId).getResultList().isEmpty()) {
            throw new EntityNotFoundException("Credit card not found");
        }
    }

    public void lockCard(Long id) {
        configureWait();
        if (entityManager.createNativeQuery("SELECT id FROM credit_cards WHERE id = :id FOR UPDATE")
                .setParameter("id", id).getResultList().isEmpty()) {
            throw new EntityNotFoundException("Credit card not found");
        }
    }

    public void lockOwnedPocket(Long id, Long userId) {
        configureWait();
        if (entityManager.createNativeQuery("SELECT id FROM pockets WHERE id = :id AND user_id = :userId FOR UPDATE")
                .setParameter("id", id).setParameter("userId", userId).getResultList().isEmpty()) {
            throw new EntityNotFoundException("Pocket not found");
        }
    }

    public void lockPockets(Long... ids) {
        configureWait();
        Arrays.stream(ids).distinct().sorted().forEach(id -> {
            if (entityManager.createNativeQuery("SELECT id FROM pockets WHERE id = :id FOR UPDATE")
                    .setParameter("id", id).getResultList().isEmpty()) {
                throw new EntityNotFoundException("Pocket not found");
            }
        });
    }

    // The bulk closing job participates in the same order as interactive card writes.
    @SuppressWarnings("unchecked")
    public java.util.List<Long> lockAllCards() {
        configureWait();
        return (java.util.List<Long>) entityManager.createNativeQuery("SELECT id FROM credit_cards ORDER BY id FOR UPDATE").getResultList();
    }
}
