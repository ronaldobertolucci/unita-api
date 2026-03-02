package io.github.ronaldobertolucci.unita.dto.investment;

import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
import io.github.ronaldobertolucci.unita.model.investment.Asset;
import io.github.ronaldobertolucci.unita.model.investment.AssetCategory;
import io.github.ronaldobertolucci.unita.model.investment.AssetStatus;

public record AssetDetailDto(
    Long id,
    String name,
    AssetCategory category,
    AssetStatus status,
    LegalEntityDto legalEntity,
    InvestmentPositionDto position,
    FixedIncomeDetailsDto fixedIncomeDetails,
    PensionDetailsDto pensionDetails
) {
    public AssetDetailDto(Asset asset) {
        this(
            asset.getId(),
            asset.getName(),
            asset.getCategory(),
            asset.getStatus(),
            new LegalEntityDto(asset.getLegalEntity()),
            new InvestmentPositionDto(asset.getPosition()),
            asset.getFixedIncomeDetails() != null
                ? new FixedIncomeDetailsDto(asset.getFixedIncomeDetails()) : null,
            asset.getPensionDetails() != null
                ? new PensionDetailsDto(asset.getPensionDetails()) : null
        );
    }
}