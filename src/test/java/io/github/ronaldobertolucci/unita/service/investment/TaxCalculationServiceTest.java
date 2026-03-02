package io.github.ronaldobertolucci.unita.service.investment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    // describeTaxBasis
    // -------------------------------------------------------------------------

    @Test
    void describeTaxBasis_WhenRegressivoUpTo180Days_ShouldDescribeCorrectly() {
        String description = taxCalculationService.describeTaxBasis(100, "REGRESSIVO");
        assertTrue(description.contains("22,5%"));
    }

    @Test
    void describeTaxBasis_WhenProgressivo_ShouldDescribeCorrectly() {
        String description = taxCalculationService.describeTaxBasis(500, "PROGRESSIVO");
        assertTrue(description.contains("antecipação"));
    }
}