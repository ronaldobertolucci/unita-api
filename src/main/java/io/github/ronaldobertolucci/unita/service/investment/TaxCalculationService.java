package io.github.ronaldobertolucci.unita.service.investment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class TaxCalculationService {

    // Renda Fixa — tabela regressiva sobre rendimento
    public BigDecimal calculateFixedIncomeTaxRate(LocalDate purchaseDate, LocalDate redemptionDate) {
        long days = ChronoUnit.DAYS.between(purchaseDate, redemptionDate);
        if (days <= 180)  return new BigDecimal("0.225");
        if (days <= 360)  return new BigDecimal("0.200");
        if (days <= 720)  return new BigDecimal("0.175");
        return new BigDecimal("0.150");
    }

    // Previdência Regressiva — tabela regressiva especial
    public BigDecimal calculatePensionRegressiveTaxRate(LocalDate purchaseDate, LocalDate redemptionDate) {
        long days = ChronoUnit.DAYS.between(purchaseDate, redemptionDate);
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
        return base.multiply(rate).setScale(8, RoundingMode.HALF_UP);
    }

    public String describeTaxBasis(long days, String regime) {
        if ("PROGRESSIVO".equals(regime)) {
            return "15% retidos na fonte (antecipação) — ajuste final na Declaração Anual";
        }
        if (days <= 180)  return "Tabela regressiva IR - até 180 dias (22,5%)";
        if (days <= 360)  return "Tabela regressiva IR - 181 a 360 dias (20,0%)";
        if (days <= 720)  return "Tabela regressiva IR - 361 a 720 dias (17,5%)";
        return "Tabela regressiva IR - acima de 720 dias (15,0%)";
    }
}