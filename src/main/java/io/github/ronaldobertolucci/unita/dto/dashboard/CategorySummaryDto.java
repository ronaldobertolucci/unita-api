package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.math.BigDecimal;

public record CategorySummaryDto(String category, BigDecimal total) {}