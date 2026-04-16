package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record DashboardDto(
        List<CategorySummaryDto> pockets,
        List<CategorySummaryDto> investments,
        BigDecimal totalOpenBills
) {}