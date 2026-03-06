package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.card.RecurringPurchase;
import io.github.ronaldobertolucci.unita.model.finance.PeriodicityType;
import io.github.ronaldobertolucci.unita.repository.RecurringPurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringPurchaseJob {

    private final RecurringPurchaseRepository recurringPurchaseRepository;
    private final RecurringPurchaseJobProcessor processor;

    // ATENÇÃO: findAllActive deve JOIN FETCH periodicity para evitar LazyInitializationException
    // durante a filtragem abaixo, que ocorre fora de qualquer transação.
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    public void execute() {
        LocalDate today = LocalDate.now();
        log.info("RecurringPurchaseJob starting for date {}", today);

        List<RecurringPurchase> actives = recurringPurchaseRepository.findAllActive(today);

        int generated = 0;
        int failed = 0;

        for (RecurringPurchase rp : actives) {
            if (alreadyGeneratedThisPeriod(rp, today) || !shouldGenerateToday(rp, today)) {
                continue;
            }

            try {
                processor.process(rp.getId(), today);
                generated++;
            } catch (Exception e) {
                log.error("RecurringPurchaseJob — failed for recurringPurchaseId={}: {}",
                        rp.getId(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("RecurringPurchaseJob finished — {} purchase(s) generated, {} failed",
                generated, failed);
    }

    private boolean shouldGenerateToday(RecurringPurchase rp, LocalDate today) {
        LocalDate start = rp.getStartDate();
        PeriodicityType type = rp.getPeriodicity().getType();

        return switch (type) {
            case DAILY -> true;
            case WEEKLY -> today.getDayOfWeek() == start.getDayOfWeek();
            case MONTHLY -> isMatchingDayOfMonth(today, start.getDayOfMonth());
            case YEARLY -> today.getMonthValue() == start.getMonthValue()
                    && isMatchingDayOfMonth(today, start.getDayOfMonth());
        };
    }

    private boolean alreadyGeneratedThisPeriod(RecurringPurchase rp, LocalDate today) {
        LocalDate last = rp.getLastGeneratedDate();
        if (last == null) return false;

        PeriodicityType type = rp.getPeriodicity().getType();

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