package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import io.github.ronaldobertolucci.unita.repository.CreditCardBillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditCardBillClosingJob {

    private final CreditCardBillRepository creditCardBillRepository;

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void execute() {
        LocalDate today = LocalDate.now();
        log.info("CreditCardBillClosingJob starting for date {}", today);

        int closed = creditCardBillRepository.closeAllOverdue(today, CreditCardBillStatus.OPEN, CreditCardBillStatus.CLOSED);

        log.info("CreditCardBillClosingJob finished — {} bill(s) closed", closed);
    }
}