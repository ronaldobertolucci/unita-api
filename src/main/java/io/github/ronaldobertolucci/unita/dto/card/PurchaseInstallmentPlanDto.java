package io.github.ronaldobertolucci.unita.dto.card;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PurchaseInstallmentPlanDto(
        @NotNull @Min(1) @Max(120) Integer installmentNumber,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @Schema(description = "Optional category override; otherwise uses purchase categoryId")
        @Positive Long categoryId
) {}
