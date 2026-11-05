package io.github.ronaldobertolucci.unita.dto.investment;

import io.github.ronaldobertolucci.unita.model.investment.InvestmentTransaction;
import io.github.ronaldobertolucci.unita.model.investment.InvestmentTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentTransactionDto(
    Long id,
    InvestmentTransactionType type,
    BigDecimal amount,
    LocalDate transactionDate,
    String notes,
    @Schema(description = "Original cash transaction reference; may be absent or shared by legacy movements")
    Long pocketTransactionId,
    @Schema(description = "Units bought or sold; null when unknown in legacy history or not applicable")
    BigDecimal quantity,
    @Schema(description = "Cost removed by a sale; null when unknown in legacy history or not applicable")
    BigDecimal costBasis,
    @Schema(description = "SELL linked to this TAX; null for unpaired legacy movements or when not applicable")
    Long relatedSellId,
    @Schema(description = "Sale operation UUID shared by SELL and TAX; null for legacy or unrelated movements")
    java.util.UUID operationId
) {
    public InvestmentTransactionDto(InvestmentTransaction tx) {
        this(
            tx.getId(),
            tx.getType(),
            tx.getAmount(),
            tx.getTransactionDate(),
            tx.getNotes(),
            tx.getTransaction() != null ? tx.getTransaction().getId() : null,
            tx.getQuantity(),
            tx.getCostBasis(),
            tx.getRelatedSell() != null ? tx.getRelatedSell().getId() : null,
            tx.getOperationId()
        );
    }
}
