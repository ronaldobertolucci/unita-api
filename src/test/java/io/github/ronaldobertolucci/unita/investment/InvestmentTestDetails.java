package io.github.ronaldobertolucci.unita.investment;

import io.github.ronaldobertolucci.unita.model.investment.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Valid default category details for tests whose subject is position arithmetic or concurrency. */
public final class InvestmentTestDetails {
    private InvestmentTestDetails() {}

    public static void complete(Asset asset) {
        if (asset.getCategory() == AssetCategory.RENDA_FIXA) {
            asset.setFixedIncomeDetails(FixedIncomeDetails.builder().asset(asset).indexer(Indexer.CDI)
                    .annualRate(BigDecimal.ONE).maturityDate(LocalDate.of(2027, 1, 1)).taxFree(false).build());
        } else if (asset.getCategory() == AssetCategory.PREVIDENCIA) {
            asset.setPensionDetails(PensionDetails.builder().asset(asset).pensionType(PensionType.VGBL)
                    .taxRegime(TaxRegime.REGRESSIVO).build());
        }
    }
}
