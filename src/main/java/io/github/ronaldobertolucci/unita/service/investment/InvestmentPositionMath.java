package io.github.ronaldobertolucci.unita.service.investment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Category-independent arithmetic for equivalent units, shared by writes and previews. */
final class InvestmentPositionMath {
    private InvestmentPositionMath() {}

    static BigDecimal proportionalValue(BigDecimal total, BigDecimal sold, BigDecimal available) {
        InvestmentDecimals.nonNegative(total, "total");
        InvestmentDecimals.positive(sold, "quantity");
        InvestmentDecimals.positive(available, "availableQuantity");
        if (sold.compareTo(available) > 0) throw new IllegalStateException("Sale quantity exceeds available units");
        // The final sale removes the exact residual, including earlier rounding differences.
        return sold.compareTo(available) == 0 ? total
                : total.multiply(sold).divide(available, 8, RoundingMode.HALF_UP);
    }

    static BigDecimal averagePrice(BigDecimal cost, BigDecimal quantity) {
        InvestmentDecimals.nonNegative(cost, "remainingCost");
        InvestmentDecimals.nonNegative(quantity, "quantity");
        if (quantity.signum() == 0) {
            if (cost.signum() != 0) throw new IllegalStateException("Cost without units");
            return BigDecimal.ZERO;
        }
        return InvestmentDecimals.calculated(cost.divide(quantity, 8, RoundingMode.HALF_UP), "averagePrice");
    }
}
