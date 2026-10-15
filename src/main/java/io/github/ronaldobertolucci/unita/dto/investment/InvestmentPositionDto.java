package io.github.ronaldobertolucci.unita.dto.investment;

import io.github.ronaldobertolucci.unita.model.investment.InvestmentPosition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentPositionDto(
    BigDecimal quantity,
    BigDecimal averagePrice,
    BigDecimal totalInvested,
    BigDecimal currentValue,
    @Schema(description = "Legacy cumulative received amount, including yield withdrawals and net redemption proceeds")
    BigDecimal redeemedValue,
    LocalDate lastValuationDate
) {
    public InvestmentPositionDto(InvestmentPosition position) {
        this(
            position.getQuantity(),
            position.getAveragePrice(),
            position.getTotalInvested(),
            position.getCurrentValue(),
            position.getRedeemedValue(),
            position.getLastValuationDate()
        );
    }
}
