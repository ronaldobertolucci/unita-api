package io.github.ronaldobertolucci.unita.dto.dashboard;

import io.github.ronaldobertolucci.unita.model.investment.Indexer;

import java.math.BigDecimal;

public record IndexerSummaryDto(
        Indexer indexer,
        BigDecimal totalCurrentValue
) {}