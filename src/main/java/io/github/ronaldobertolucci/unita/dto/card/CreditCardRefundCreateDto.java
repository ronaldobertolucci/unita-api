package io.github.ronaldobertolucci.unita.dto.card;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditCardRefundCreateDto(
        @NotBlank @Size(max = 255) String description,
        @Schema(description = "Positive credit, maximum 9999999999999.99, at most two decimal places")
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @NotNull LocalDate refundDate,
        @Schema(description = "Available INCOME or NEUTRAL category")
        @NotNull @Positive Long categoryId,
        @Schema(description = "Optional original purchase of this card and user. Accumulated linked refunds across all bills cannot exceed its original total. Omit for an unlinked credit.")
        @Positive Long purchaseId
) {
    public CreditCardRefundCreateDto(String description, BigDecimal amount, LocalDate refundDate, Long categoryId) {
        this(description, amount, refundDate, categoryId, null);
    }
}
