package io.github.ronaldobertolucci.unita.service.investment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class InvestmentDecimalsTest {
    @ParameterizedTest
    @ValueSource(strings = {"0.000000001", "1.123456789", "10000000000", "1E+100"})
    void rejectsInputsThatCannotBeRepresentedWithoutLoss(String value) {
        assertThrows(IllegalArgumentException.class, () -> InvestmentDecimals.positive(new BigDecimal(value), "quantity"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00000001", "9999999999.99999999", "1.000000000", "1E+9"})
    void acceptsRepresentableValuesIncludingInsignificantTrailingZeroes(String value) {
        assertDoesNotThrow(() -> InvestmentDecimals.positive(new BigDecimal(value), "quantity"));
    }

    @Test
    void roundsOnlyDerivedValuesAndChecksOverflowAfterRounding() {
        assertEquals(new BigDecimal("1.12345679"), InvestmentDecimals.calculated(new BigDecimal("1.123456785"), "contribution"));
        assertEquals(new BigDecimal("1.12345678"), InvestmentDecimals.calculated(new BigDecimal("1.123456784"), "contribution"));
        assertThrows(IllegalArgumentException.class, () -> InvestmentDecimals.calculated(new BigDecimal("9999999999.999999995"), "currentValue"));
        assertThrows(IllegalArgumentException.class, () -> InvestmentDecimals.nonNegative(new BigDecimal("-1"), "currentValue"));
        assertDoesNotThrow(() -> InvestmentDecimals.nonNegative(BigDecimal.ZERO, "currentValue"));
    }
}
