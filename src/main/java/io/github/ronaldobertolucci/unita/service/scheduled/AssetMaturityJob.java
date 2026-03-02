package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.investment.Asset;
import io.github.ronaldobertolucci.unita.model.investment.AssetStatus;
import io.github.ronaldobertolucci.unita.repository.AssetRepository;
import io.github.ronaldobertolucci.unita.repository.FixedIncomeDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class AssetMaturityJob {

    private final FixedIncomeDetailsRepository fixedIncomeDetailsRepository;
    private final AssetRepository assetRepository;

    @Scheduled(cron = "0 0 1 * * *") // Diariamente às 01h00
    @Transactional
    public void markMaturedAssets() {
        fixedIncomeDetailsRepository.findAllMaturedByDate(LocalDate.now())
                .forEach(details -> {
                    Asset asset = details.getAsset();
                    asset.setStatus(AssetStatus.MATURED);
                    assetRepository.save(asset);
                });
    }
}