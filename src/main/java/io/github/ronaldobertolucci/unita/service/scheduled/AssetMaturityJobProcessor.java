package io.github.ronaldobertolucci.unita.service.scheduled;

import io.github.ronaldobertolucci.unita.model.investment.Asset;
import io.github.ronaldobertolucci.unita.model.investment.AssetStatus;
import io.github.ronaldobertolucci.unita.repository.AssetRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetMaturityJobProcessor {

    private final AssetRepository assetRepository;

    // REQUIRES_NEW garante que cada asset é commitado de forma independente.
    // O ID recebido corresponde ao asset_id via @MapsId em FixedIncomeDetails.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new EntityNotFoundException("Asset not found: " + assetId));

        asset.setStatus(AssetStatus.MATURED);
        assetRepository.save(asset);
    }
}