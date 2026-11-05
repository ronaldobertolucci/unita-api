package io.github.ronaldobertolucci.unita.service.investment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** DECIMAL(18,8): validate inputs without rounding; round only calculated values. */
final class InvestmentDecimals {
    private static final BigDecimal MAX = new BigDecimal("9999999999.99999999");

    private InvestmentDecimals() {}

    static void positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0)
            throw new IllegalArgumentException(field + " must be greater than zero");
        precision(value, field);
    }

    static void nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0)
            throw new IllegalArgumentException(field + " must be non-negative");
        precision(value, field);
    }

    private static void precision(BigDecimal value, String field) {
        if (value.compareTo(MAX) > 0 || value.stripTrailingZeros().scale() > 8)
            throw new IllegalArgumentException(field + " must fit DECIMAL(18,8)");
    }

    static BigDecimal calculated(BigDecimal value, String field) {
        BigDecimal rounded = value.setScale(8, RoundingMode.HALF_UP);
        nonNegative(rounded, field);
        return rounded;
    }
}
