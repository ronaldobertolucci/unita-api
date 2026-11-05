package io.github.ronaldobertolucci.unita.service.investment;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvestmentPositionMathTest {
    @Test
    void proportionalCalculationAndAveragePriceDoNotRequireAnAssetOrCategory() {
        var cost = InvestmentPositionMath.proportionalValue(new BigDecimal("3000"), BigDecimal.ONE, new BigDecimal("3"));
        assertEquals(0, new BigDecimal("1000").compareTo(cost));
        assertEquals(new BigDecimal("1066.66666667"), InvestmentPositionMath.averagePrice(new BigDecimal("3200"), new BigDecimal("3")));
    }

    @Test
    void finalSaleConservesExactCostAfterFractionalRounding() {
        BigDecimal total = BigDecimal.ONE, quantity = new BigDecimal("3"), removed = BigDecimal.ZERO;
        for (int i = 0; i < 3; i++) {
            var cost = InvestmentPositionMath.proportionalValue(total, BigDecimal.ONE, quantity);
            removed = removed.add(cost); total = total.subtract(cost); quantity = quantity.subtract(BigDecimal.ONE);
        }
        assertEquals(0, BigDecimal.ONE.compareTo(removed));
        assertEquals(0, total.signum());
        assertEquals(BigDecimal.ZERO, InvestmentPositionMath.averagePrice(total, quantity));
        assertThrows(IllegalStateException.class, () -> InvestmentPositionMath.proportionalValue(BigDecimal.ONE, new BigDecimal("2"), BigDecimal.ONE));
    }
}
