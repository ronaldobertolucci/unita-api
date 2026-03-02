package io.github.ronaldobertolucci.unita.dto.investment;

import io.github.ronaldobertolucci.unita.model.investment.FixedIncomeDetails;
import io.github.ronaldobertolucci.unita.model.investment.Indexer;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FixedIncomeDetailsDto(
    Indexer indexer,
    BigDecimal annualRate,
    LocalDate maturityDate,
    boolean taxFree
) {
    public FixedIncomeDetailsDto(FixedIncomeDetails details) {
        this(
            details.getIndexer(),
            details.getAnnualRate(),
            details.getMaturityDate(),
            details.isTaxFree()
        );
    }
}