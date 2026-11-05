package io.github.ronaldobertolucci.unita.service.investment;

import io.github.ronaldobertolucci.unita.investment.InvestmentTestDetails;
import io.github.ronaldobertolucci.unita.model.investment.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class InvestmentCategoryRulesTest {
    @ParameterizedTest
    @EnumSource(AssetCategory.class)
    void supportedCategoriesHaveExplicitYieldAndMaturityRules(AssetCategory category) {
        var asset = Asset.builder().category(category).build();
        InvestmentTestDetails.complete(asset);
        assertDoesNotThrow(() -> InvestmentCategoryRules.requireYield(asset));
        assertEquals(category == AssetCategory.RENDA_FIXA, InvestmentCategoryRules.hasMaturity(asset));
    }

    @ParameterizedTest
    @EnumSource(AssetCategory.class)
    void missingOrWrongDetailsAreRejected(AssetCategory category) {
        var asset = Asset.builder().category(category).build();
        assertThrows(IllegalStateException.class, () -> InvestmentCategoryRules.validateDetails(asset));
        InvestmentTestDetails.complete(asset);
        asset.setCategory(category == AssetCategory.RENDA_FIXA ? AssetCategory.PREVIDENCIA : AssetCategory.RENDA_FIXA);
        assertThrows(IllegalStateException.class, () -> InvestmentCategoryRules.validateDetails(asset));
        InvestmentTestDetails.complete(asset);
        assertThrows(IllegalStateException.class, () -> InvestmentCategoryRules.validateDetails(asset));
    }

    @ParameterizedTest
    @ValueSource(strings = {"indexer", "rate", "maturity", "pensionType", "regime"})
    void requiredDetailFieldsAreValidated(String field) {
        var asset = Asset.builder().category(field.equals("pensionType") || field.equals("regime")
                ? AssetCategory.PREVIDENCIA : AssetCategory.RENDA_FIXA).build();
        InvestmentTestDetails.complete(asset);
        switch (field) {
            case "indexer" -> asset.getFixedIncomeDetails().setIndexer(null);
            case "rate" -> asset.getFixedIncomeDetails().setAnnualRate(null);
            case "maturity" -> asset.getFixedIncomeDetails().setMaturityDate(null);
            case "pensionType" -> asset.getPensionDetails().setPensionType(null);
            case "regime" -> asset.getPensionDetails().setTaxRegime(null);
        }
        assertThrows(IllegalStateException.class, () -> InvestmentCategoryRules.validateDetails(asset));
    }

    @Test
    void unknownCategoryDoesNotInheritAnyBehavior() {
        var asset = Asset.builder().build();
        assertThrows(IllegalArgumentException.class, () -> InvestmentCategoryRules.validateDetails(asset));
        assertThrows(IllegalArgumentException.class, () -> InvestmentCategoryRules.requireYield(asset));
        assertThrows(IllegalArgumentException.class, () -> InvestmentCategoryRules.hasMaturity(asset));
        assertArrayEquals(new AssetCategory[]{AssetCategory.RENDA_FIXA, AssetCategory.PREVIDENCIA}, AssetCategory.values());
    }
}
