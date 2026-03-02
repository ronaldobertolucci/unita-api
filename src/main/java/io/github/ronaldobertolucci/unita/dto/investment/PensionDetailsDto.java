package io.github.ronaldobertolucci.unita.dto.investment;

import io.github.ronaldobertolucci.unita.model.investment.PensionDetails;
import io.github.ronaldobertolucci.unita.model.investment.PensionType;
import io.github.ronaldobertolucci.unita.model.investment.TaxRegime;

public record PensionDetailsDto(
    PensionType pensionType,
    TaxRegime taxRegime
) {
    public PensionDetailsDto(PensionDetails details) {
        this(details.getPensionType(), details.getTaxRegime());
    }
}
