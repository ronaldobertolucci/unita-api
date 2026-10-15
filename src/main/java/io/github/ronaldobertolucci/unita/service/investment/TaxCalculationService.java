package io.github.ronaldobertolucci.unita.service.investment;

import org.springframework.stereotype.Service;
import io.github.ronaldobertolucci.unita.model.investment.TaxRegime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class TaxCalculationService {

    // Renda Fixa — tabela regressiva sobre rendimento
    public BigDecimal calculateFixedIncomeTaxRate(LocalDate purchaseDate, LocalDate redemptionDate) {
        long days = holdingDays(purchaseDate, redemptionDate);
        if (days <= 180)  return new BigDecimal("0.225");
        if (days <= 360)  return new BigDecimal("0.200");
        if (days <= 720)  return new BigDecimal("0.175");
        return new BigDecimal("0.150");
    }

    // Previdência Regressiva — tabela regressiva especial
    public BigDecimal calculatePensionRegressiveTaxRate(LocalDate purchaseDate, LocalDate redemptionDate) {
        long days = holdingDays(purchaseDate, redemptionDate);
        if (days <= 730)   return new BigDecimal("0.350");
        if (days <= 1460)  return new BigDecimal("0.300");
        if (days <= 2190)  return new BigDecimal("0.250");
        if (days <= 2920)  return new BigDecimal("0.200");
        if (days <= 3650)  return new BigDecimal("0.150");
        return new BigDecimal("0.100");
    }

    // Previdência Progressiva — 15% fixo retido na fonte no resgate
    // Ajuste final ocorre na Declaração Anual do contribuinte
    public BigDecimal getPensionProgressiveTaxRate() {
        return new BigDecimal("0.150");
    }

    public BigDecimal calculateTaxAmount(BigDecimal base, BigDecimal rate) {
        if (base == null || base.signum() < 0) {
            throw new IllegalArgumentException("tax base must be non-negative");
        }
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("tax rate must be between zero and one");
        }
        return base.multiply(rate).setScale(8, RoundingMode.HALF_UP);
    }

    public String describeFixedIncomeTaxBasis(long days) {
        requireNonNegativeDays(days);
        if (days <= 180)  return "Renda fixa: tabela regressiva IR - até 180 dias (22,5%)";
        if (days <= 360)  return "Renda fixa: tabela regressiva IR - 181 a 360 dias (20,0%)";
        if (days <= 720)  return "Renda fixa: tabela regressiva IR - 361 a 720 dias (17,5%)";
        return "Renda fixa: tabela regressiva IR - acima de 720 dias (15,0%)";
    }

    public String describePensionTaxBasis(long days, TaxRegime regime) {
        requireNonNegativeDays(days);
        if (regime == null) {
            throw new IllegalArgumentException("pension tax regime is required");
        }
        if (regime == TaxRegime.PROGRESSIVO) {
            return "Previdência progressiva: 15% retidos na fonte (antecipação) — ajuste final na Declaração Anual";
        }
        if (days <= 730)  return "Previdência regressiva: até 730 dias (35,0%)";
        if (days <= 1460) return "Previdência regressiva: 731 a 1460 dias (30,0%)";
        if (days <= 2190) return "Previdência regressiva: 1461 a 2190 dias (25,0%)";
        if (days <= 2920) return "Previdência regressiva: 2191 a 2920 dias (20,0%)";
        if (days <= 3650) return "Previdência regressiva: 2921 a 3650 dias (15,0%)";
        return "Previdência regressiva: acima de 3650 dias (10,0%)";
    }

    private static long holdingDays(LocalDate purchaseDate, LocalDate redemptionDate) {
        if (purchaseDate == null || redemptionDate == null) {
            throw new IllegalArgumentException("purchaseDate and redemptionDate are required");
        }
        long days = ChronoUnit.DAYS.between(purchaseDate, redemptionDate);
        requireNonNegativeDays(days);
        return days;
    }

    private static void requireNonNegativeDays(long days) {
        if (days < 0) {
            throw new IllegalArgumentException("purchaseDate must not be after redemptionDate");
        }
    }
}
