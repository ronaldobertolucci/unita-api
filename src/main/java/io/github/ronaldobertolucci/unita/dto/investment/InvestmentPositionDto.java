package io.github.ronaldobertolucci.unita.dto.investment;

import io.github.ronaldobertolucci.unita.model.investment.InvestmentPosition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentPositionDto(
    @Schema(description = "Remaining equivalent units; zero after final sale")
    BigDecimal quantity,
    @Schema(description = "Managerial average cost: remainingCost / quantity; zero without units; not fiscal lot accounting")
    BigDecimal averagePrice,
    @Schema(description = "Cumulative historical contributions; never reduced by sales")
    BigDecimal totalInvested,
    @Schema(description = "Manual remaining valuation, reduced proportionally by sales independently of negotiated proceeds; not a live quote")
    BigDecimal currentValue,
    @Schema(description = "Legacy cumulative received amount, including yield withdrawals and net redemption proceeds")
    BigDecimal redeemedValue,
    @Schema(description = "Last manual valuation date; unchanged by a sale")
    LocalDate lastValuationDate,
    @Schema(description = "Acquisition cost of the remaining units; distinct from cumulative totalInvested")
    BigDecimal remainingCost
) {
    public InvestmentPositionDto(InvestmentPosition position) {
        this(
            position.getQuantity(),
            position.getAveragePrice(),
            position.getTotalInvested(),
            position.getCurrentValue(),
            position.getRedeemedValue(),
            position.getLastValuationDate(),
            position.getRemainingCost()
        );
    }
}
