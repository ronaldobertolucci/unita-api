package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.card.*;
import io.github.ronaldobertolucci.unita.model.finance.PeriodicityType;
import io.github.ronaldobertolucci.unita.repository.CreditCardInstallmentRepository;
import io.github.ronaldobertolucci.unita.repository.CreditCardPurchaseRepository;
import io.github.ronaldobertolucci.unita.repository.RecurringPurchaseRepository;
import io.github.ronaldobertolucci.unita.service.card.CreditCardBillResolverService;
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
public class RecurringPurchaseJob {

    private final RecurringPurchaseRepository recurringPurchaseRepository;
    private final CreditCardPurchaseRepository creditCardPurchaseRepository;
    private final CreditCardInstallmentRepository creditCardInstallmentRepository;
    private final CreditCardBillResolverService billResolverService;

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void execute() {
        LocalDate today = LocalDate.now();
        log.info("RecurringPurchaseJob starting for date {}", today);

        List<RecurringPurchase> actives = recurringPurchaseRepository.findAllActive(today);

        int generated = 0;
        for (RecurringPurchase rp : actives) {
            if (alreadyGeneratedThisPeriod(rp, today)) {
                continue;
            }
            if (!shouldGenerateToday(rp, today)) {
                continue;
            }

            CreditCard creditCard = rp.getCreditCard();

            CreditCardPurchase purchase = CreditCardPurchase.builder()
                    .creditCard(creditCard)
                    .description(rp.getDescription())
                    .totalValue(rp.getAmount())
                    .purchaseDate(today)
                    .installmentsCount(1)
                    .build();

            creditCardPurchaseRepository.save(purchase);

            CreditCardBill bill = billResolverService.findOrCreateForDate(creditCard, today);

            CreditCardInstallment installment = CreditCardInstallment.builder()
                    .purchase(purchase)
                    .installmentNumber(1)
                    .amount(rp.getAmount())
                    .creditCardBill(bill)
                    .build();

            creditCardInstallmentRepository.save(installment);

            rp.setLastGeneratedDate(today);
            recurringPurchaseRepository.save(rp);

            generated++;
        }

        log.info("RecurringPurchaseJob finished — {} purchase(s) generated", generated);
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