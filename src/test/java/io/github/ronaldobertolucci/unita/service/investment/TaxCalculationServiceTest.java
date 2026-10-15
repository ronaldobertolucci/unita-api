package io.github.ronaldobertolucci.unita.service.investment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import io.github.ronaldobertolucci.unita.model.investment.TaxRegime;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceTest {

    @InjectMocks
    private TaxCalculationService taxCalculationService;

    // -------------------------------------------------------------------------
    // calculateFixedIncomeTaxRate
    // -------------------------------------------------------------------------

    @Test
    void calculateFixedIncomeTaxRate_WhenUpTo180Days_ShouldReturn22Point5Percent() {
        BigDecimal rate = taxCalculationService.calculateFixedIncomeTaxRate(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30));
        assertEquals(0, new BigDecimal("0.225").compareTo(rate));
    }

    @Test
    void calculateFixedIncomeTaxRate_WhenAt181Days_ShouldReturn20Percent() {
        BigDecimal rate = taxCalculationService.calculateFixedIncomeTaxRate(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 7, 1));
        assertEquals(0, new BigDecimal("0.200").compareTo(rate));
    }

    @Test
    void calculateFixedIncomeTaxRate_WhenAt361Days_ShouldReturn17Point5Percent() {
        BigDecimal rate = taxCalculationService.calculateFixedIncomeTaxRate(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 28));
        assertEquals(0, new BigDecimal("0.175").compareTo(rate));
    }

    @Test
    void calculateFixedIncomeTaxRate_WhenAbove720Days_ShouldReturn15Percent() {
        BigDecimal rate = taxCalculationService.calculateFixedIncomeTaxRate(
                LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 2));
        assertEquals(0, new BigDecimal("0.150").compareTo(rate));
    }

    // -------------------------------------------------------------------------
    // calculatePensionRegressiveTaxRate
    // -------------------------------------------------------------------------

    @Test
    void calculatePensionRegressiveTaxRate_WhenUpTo730Days_ShouldReturn35Percent() {
        BigDecimal rate = taxCalculationService.calculatePensionRegressiveTaxRate(
                LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1));
        assertEquals(0, new BigDecimal("0.350").compareTo(rate));
    }

    @Test
    void calculatePensionRegressiveTaxRate_WhenAbove3650Days_ShouldReturn10Percent() {
        BigDecimal rate = taxCalculationService.calculatePensionRegressiveTaxRate(
                LocalDate.of(2015, 1, 1), LocalDate.of(2025, 1, 2));
        assertEquals(0, new BigDecimal("0.100").compareTo(rate));
    }

    // -------------------------------------------------------------------------
    // calculatePensionProgressiveTaxRate
    // -------------------------------------------------------------------------

    @Test
    void getPensionProgressiveTaxRate_ShouldReturn15Percent() {
        BigDecimal rate = taxCalculationService.getPensionProgressiveTaxRate();
        assertEquals(0, new BigDecimal("0.150").compareTo(rate));
    }

    // -------------------------------------------------------------------------
    // calculateTaxAmount
    // -------------------------------------------------------------------------

    @Test
    void calculateTaxAmount_ShouldReturnCorrectValue() {
        BigDecimal tax = taxCalculationService.calculateTaxAmount(
                new BigDecimal("1000.00"), new BigDecimal("0.150"));
        assertEquals(0, new BigDecimal("150.00000000").compareTo(tax));
    }

    // -------------------------------------------------------------------------
    // Category-specific descriptions
    // -------------------------------------------------------------------------

    @Test
    void describeFixedIncomeTaxBasis_WhenUpTo180Days_ShouldDescribeCorrectly() {
        String description = taxCalculationService.describeFixedIncomeTaxBasis(100);
        assertTrue(description.contains("22,5%"));
    }

    @Test
    void describePensionTaxBasis_WhenProgressivo_ShouldDescribeCorrectly() {
        String description = taxCalculationService.describePensionTaxBasis(500, TaxRegime.PROGRESSIVO);
        assertTrue(description.contains("antecipação"));
    }

    @ParameterizedTest
    @CsvSource({"0,0.35,35", "730,0.35,35", "731,0.30,30", "1460,0.30,30", "1461,0.25,25",
            "2190,0.25,25", "2191,0.20,20", "2920,0.20,20", "2921,0.15,15", "3650,0.15,15", "3651,0.10,10"})
    void pensionRegressiveDescriptionAndRateAgreeAtEveryBoundary(int days, BigDecimal expected, String percent) {
        LocalDate purchase = LocalDate.of(2010, 1, 1);
        assertEquals(0, expected.compareTo(taxCalculationService.calculatePensionRegressiveTaxRate(purchase, purchase.plusDays(days))));
        String description = taxCalculationService.describePensionTaxBasis(days, TaxRegime.REGRESSIVO);
        assertTrue(description.startsWith("Previdência regressiva:"));
        assertTrue(description.contains(percent + ",0%"));
    }

    @ParameterizedTest
    @CsvSource({"0,0.225,22;5", "180,0.225,22;5", "181,0.20,20;0", "360,0.20,20;0", "361,0.175,17;5",
            "720,0.175,17;5", "721,0.15,15;0"})
    void fixedIncomeDescriptionAndRateAgreeAtEveryBoundary(int days, BigDecimal expected, String percent) {
        LocalDate purchase = LocalDate.of(2020, 1, 1);
        assertEquals(0, expected.compareTo(taxCalculationService.calculateFixedIncomeTaxRate(purchase, purchase.plusDays(days))));
        assertTrue(taxCalculationService.describeFixedIncomeTaxBasis(days).contains(percent.replace(';', ',') + "%"));
    }

    @ParameterizedTest
    @CsvSource(value = {"null,0.15", "-1,0.15", "10,null", "10,-0.1", "10,1.1"}, nullValues = "null")
    void invalidTaxBasesAndRatesAreRejected(BigDecimal base, BigDecimal rate) {
        assertThrows(IllegalArgumentException.class, () -> taxCalculationService.calculateTaxAmount(base, rate));
    }

    @Test
    void acquisitionAfterRedemptionIsRejectedByBothRegimes() {
        LocalDate date = LocalDate.of(2026, 10, 14);
        assertThrows(IllegalArgumentException.class, () -> taxCalculationService.calculateFixedIncomeTaxRate(date.plusDays(1), date));
        assertThrows(IllegalArgumentException.class, () -> taxCalculationService.calculatePensionRegressiveTaxRate(date.plusDays(1), date));
        assertThrows(IllegalArgumentException.class, () -> taxCalculationService.describeFixedIncomeTaxBasis(-1));
        assertThrows(IllegalArgumentException.class, () -> taxCalculationService.describePensionTaxBasis(-1, TaxRegime.REGRESSIVO));
    }
}