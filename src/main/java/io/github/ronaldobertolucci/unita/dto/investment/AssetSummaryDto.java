package io.github.ronaldobertolucci.unita.dto.investment;

import io.github.ronaldobertolucci.unita.model.investment.Asset;
import io.github.ronaldobertolucci.unita.model.investment.AssetCategory;
import io.github.ronaldobertolucci.unita.model.investment.AssetStatus;

import java.math.BigDecimal;

public record AssetSummaryDto(
    Long id,
    String name,
    AssetCategory category,
    AssetStatus status,
    String legalEntityName,
    BigDecimal currentValue,
    BigDecimal totalInvested,
    BigDecimal redeemedValue
) {
    public AssetSummaryDto(Asset asset) {
        this(
            asset.getId(),
            asset.getName(),
            asset.getCategory(),
            asset.getStatus(),
            asset.getLegalEntity().getCorporateName(),
            asset.getPosition().getCurrentValue(),
            asset.getPosition().getTotalInvested(),
            asset.getPosition().getRedeemedValue()
        );
    }
}