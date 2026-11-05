package io.github.ronaldobertolucci.unita.service.investment;

import io.github.ronaldobertolucci.unita.model.investment.*;

/** Explicit supported categories; adding an enum alone must not enable category-specific behavior. */
public final class InvestmentCategoryRules {
    private InvestmentCategoryRules() {}

    public static void validateDetails(Asset asset) {
        if (asset.getCategory() == null) throw new IllegalArgumentException("Unsupported investment category");
        switch (asset.getCategory()) {
            case RENDA_FIXA -> {
                var details = asset.getFixedIncomeDetails();
                if (details == null || asset.getPensionDetails() != null || details.getIndexer() == null
                        || details.getAnnualRate() == null || details.getMaturityDate() == null) {
                    throw new IllegalStateException("Fixed income requires complete fixed-income details and no pension details");
                }
            }
            case PREVIDENCIA -> {
                var details = asset.getPensionDetails();
                if (details == null || asset.getFixedIncomeDetails() != null || details.getPensionType() == null
                        || details.getTaxRegime() == null) {
                    throw new IllegalStateException("Pension requires complete pension details and no fixed-income details");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported investment category");
        }
    }

    public static void requireYield(Asset asset) {
        validateDetails(asset);
        switch (asset.getCategory()) {
            case RENDA_FIXA, PREVIDENCIA -> { /* Receipt already included in valuation; units/cost are unchanged. */ }
            default -> throw new IllegalArgumentException("Yield withdrawal is unavailable for this asset category");
        }
    }

    public static boolean hasMaturity(Asset asset) {
        validateDetails(asset);
        return switch (asset.getCategory()) {
            case RENDA_FIXA -> true;
            case PREVIDENCIA -> false;
            default -> throw new IllegalArgumentException("Maturity rule is unavailable for this asset category");
        };
    }
}
