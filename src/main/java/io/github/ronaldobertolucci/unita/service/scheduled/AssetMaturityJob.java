package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.investment.FixedIncomeDetails;
import io.github.ronaldobertolucci.unita.repository.FixedIncomeDetailsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetMaturityJob {

    private final FixedIncomeDetailsRepository fixedIncomeDetailsRepository;
    private final AssetMaturityJobProcessor processor;

    @Scheduled(cron = "0 0 1 * * *")
    public void markMaturedAssets() {
        LocalDate today = LocalDate.now();
        log.info("AssetMaturityJob starting for date {}", today);

        List<FixedIncomeDetails> matured = fixedIncomeDetailsRepository.findAllMaturedByDate(today);

        int processed = 0;
        int failed = 0;

        for (FixedIncomeDetails details : matured) {
            try {
                processor.process(details.getId());
                processed++;
            } catch (Exception e) {
                log.error("AssetMaturityJob — failed for assetId={}: {}",
                        details.getId(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("AssetMaturityJob finished — {} asset(s) marked as MATURED, {} failed",
                processed, failed);
    }
}