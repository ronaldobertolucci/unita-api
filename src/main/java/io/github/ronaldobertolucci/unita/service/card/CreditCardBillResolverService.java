package io.github.ronaldobertolucci.unita.service.card;

import io.github.ronaldobertolucci.unita.model.card.CreditCard;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBill;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import io.github.ronaldobertolucci.unita.repository.CreditCardBillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CreditCardBillResolverService {

    private final CreditCardBillRepository creditCardBillRepository;

    @Transactional
    public CreditCardBill findOrCreateForDate(CreditCard creditCard, LocalDate installmentDate) {
        return creditCardBillRepository
                .findByPeriod(creditCard.getId(), installmentDate)
                .orElseGet(() -> createChainForDate(creditCard, installmentDate));
    }

    @Transactional
    public CreditCardBill findOrCreateForDate(CreditCard creditCard, LocalDate installmentDate, LocalDate limitDate) {
        return findOrCreateForDate(creditCard, installmentDate);
    }

    private CreditCardBill createChainForDate(CreditCard creditCard, LocalDate installmentDate) {
        LocalDate today = LocalDate.now();

        Optional<CreditCardBill> latestBefore = creditCardBillRepository
                .findLatestBeforeDate(creditCard.getId(), installmentDate);

        Optional<CreditCardBill> latest = creditCardBillRepository
                .findLatestByCreditCardId(creditCard.getId());

        if (latestBefore.isPresent()) {
            return createChainForward(creditCard, latestBefore.get(), installmentDate, today);
        } else if (latest.isPresent()) {
            return createChainBackward(creditCard, latest.get(), installmentDate);
        } else {
            return createInitialBill(creditCard, installmentDate);
        }
    }

    private CreditCardBill createChainForward(CreditCard creditCard, CreditCardBill reference,
                                              LocalDate installmentDate, LocalDate today) {
        CreditCardBill current = reference;
        while (!installmentDate.isBefore(current.getClosingDate())) {
            boolean isFuture = current.getClosingDate().isAfter(today);
            int closingDay = isFuture ? creditCard.getClosingDay() : current.getClosingDay();
            int dueDay = isFuture ? creditCard.getDueDay() : current.getDueDay();
            current = createNext(creditCard, current, closingDay, dueDay);
        }
        return current;
    }

    private CreditCardBill createChainBackward(CreditCard creditCard, CreditCardBill reference,
                                               LocalDate installmentDate) {
        CreditCardBill current = reference;
        while (installmentDate.isBefore(current.getPeriodStart())) {
            current = createPrevious(creditCard, current);
        }
        return current;
    }

    private CreditCardBill createNext(CreditCard creditCard, CreditCardBill previous,
                                      int closingDay, int dueDay) {
        LocalDate periodStart = previous.getClosingDate();
        LocalDate nextClosing = periodStart.plusMonths(1)
                .withDayOfMonth(Math.min(closingDay, periodStart.plusMonths(1).lengthOfMonth()));

        LocalDate dueDateMonth = dueDay > closingDay ? nextClosing : nextClosing.plusMonths(1);
        LocalDate dueDate = dueDateMonth.withDayOfMonth(Math.min(dueDay, dueDateMonth.lengthOfMonth()));

        return creditCardBillRepository.save(CreditCardBill.builder()
                .creditCard(creditCard)
                .periodStart(periodStart)
                .closingDate(nextClosing)
                .dueDate(dueDate)
                .closingDay(closingDay)
                .dueDay(dueDay)
                .status(CreditCardBillStatus.OPEN)
                .build());
    }

    private CreditCardBill createPrevious(CreditCard creditCard, CreditCardBill reference) {
        int closingDay = reference.getClosingDay();
        int dueDay = reference.getDueDay();

        LocalDate prevClosing = reference.getPeriodStart();
        LocalDate prevPeriodStart = prevClosing.minusMonths(1)
                .withDayOfMonth(Math.min(closingDay, prevClosing.minusMonths(1).lengthOfMonth()));

        LocalDate dueDateMonth = dueDay > closingDay ? prevClosing : prevClosing.plusMonths(1);
        LocalDate dueDate = dueDateMonth.withDayOfMonth(Math.min(dueDay, dueDateMonth.lengthOfMonth()));

        return creditCardBillRepository.save(CreditCardBill.builder()
                .creditCard(creditCard)
                .periodStart(prevPeriodStart)
                .closingDate(prevClosing)
                .dueDate(dueDate)
                .closingDay(closingDay)
                .dueDay(dueDay)
                .status(CreditCardBillStatus.OPEN)
                .build());
    }

    public CreditCardBill createInitialBill(CreditCard creditCard, LocalDate referenceDate) {
        int closingDay = creditCard.getClosingDay();
        int dueDay = creditCard.getDueDay();

        LocalDate closingDate = referenceDate.withDayOfMonth(
                Math.min(closingDay, referenceDate.lengthOfMonth()));
        if (!closingDate.isAfter(referenceDate)) {
            LocalDate next = closingDate.plusMonths(1);
            closingDate = next.withDayOfMonth(Math.min(closingDay, next.lengthOfMonth()));
        }

        LocalDate periodStart = closingDate.minusMonths(1)
                .withDayOfMonth(Math.min(closingDay, closingDate.minusMonths(1).lengthOfMonth()));

        LocalDate dueDateMonth = dueDay > closingDay ? closingDate : closingDate.plusMonths(1);
        LocalDate dueDate = dueDateMonth.withDayOfMonth(Math.min(dueDay, dueDateMonth.lengthOfMonth()));

        return creditCardBillRepository.save(CreditCardBill.builder()
                .creditCard(creditCard)
                .periodStart(periodStart)
                .closingDate(closingDate)
                .dueDate(dueDate)
                .closingDay(closingDay)
                .dueDay(dueDay)
                .status(CreditCardBillStatus.OPEN)
                .build());
    }
}