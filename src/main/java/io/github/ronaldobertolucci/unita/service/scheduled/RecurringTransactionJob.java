package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.finance.PeriodicityType;
import io.github.ronaldobertolucci.unita.model.pocket.RecurringTransaction;
import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import io.github.ronaldobertolucci.unita.repository.RecurringTransactionRepository;
import io.github.ronaldobertolucci.unita.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringTransactionJob {

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final TransactionRepository transactionRepository;

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void execute() {
        LocalDate today = LocalDate.now();
        log.info("RecurringTransactionJob starting for date {}", today);

        List<RecurringTransaction> actives = recurringTransactionRepository.findAllActive(today);

        int generated = 0;
        for (RecurringTransaction rt : actives) {
            if (alreadyGeneratedThisPeriod(rt, today)) {
                continue;
            }
            if (!shouldGenerateToday(rt, today)) {
                continue;
            }

            Transaction transaction = Transaction.builder()
                    .pocket(rt.getPocket())
                    .amount(rt.getAmount())
                    .direction(rt.getDirection())
                    .transactionDate(today)
                    .description(rt.getDescription())
                    .build();

            transactionRepository.save(transaction);

            rt.setLastGeneratedDate(today);
            recurringTransactionRepository.save(rt);

            generated++;
        }

        log.info("RecurringTransactionJob finished — {} transaction(s) generated", generated);
    }

    private boolean shouldGenerateToday(RecurringTransaction rt, LocalDate today) {
        LocalDate start = rt.getStartDate();
        PeriodicityType type = rt.getPeriodicity().getType();

        return switch (type) {
            case DAILY -> true;
            case WEEKLY -> today.getDayOfWeek() == start.getDayOfWeek();
            case MONTHLY -> isMatchingDayOfMonth(today, start.getDayOfMonth());
            case YEARLY -> today.getMonthValue() == start.getMonthValue()
                    && isMatchingDayOfMonth(today, start.getDayOfMonth());
        };
    }

    private boolean alreadyGeneratedThisPeriod(RecurringTransaction rt, LocalDate today) {
        LocalDate last = rt.getLastGeneratedDate();
        if (last == null) return false;

        PeriodicityType type = rt.getPeriodicity().getType();

        return switch (type) {
            case DAILY -> !last.isBefore(today);
            case WEEKLY -> !last.isBefore(today.with(java.time.DayOfWeek.MONDAY));
            case MONTHLY -> last.getYear() == today.getYear()
                    && last.getMonthValue() == today.getMonthValue();
            case YEARLY -> last.getYear() == today.getYear();
        };
    }

    /**
     * Handles months shorter than the start day (e.g. start_date on day 31,
     * February runs on the last day of the month instead).
     */
    private boolean isMatchingDayOfMonth(LocalDate today, int startDay) {
        int lastDayOfMonth = today.lengthOfMonth();
        int targetDay = Math.min(startDay, lastDayOfMonth);
        return today.getDayOfMonth() == targetDay;
    }
}