package io.github.ronaldobertolucci.unita.dto.card;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Complete atomic purchase plan, also used by PUT. First installment uses purchaseDate; subsequent dates use original date plus months. Only OPEN bills without payment may be affected.")
public record CreditCardPurchaseCreateDto(
        @NotBlank @Size(max = 255) String description,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal totalValue,
        @NotNull LocalDate purchaseDate,
        @NotNull @Min(1) @Max(120) Integer installmentsCount,
        @NotNull @Positive @Schema(description = "Default category for every installment: available EXPENSE or NEUTRAL") Long categoryId,
        @Schema(description = "Defaults to AUTOMATIC when omitted/null. CUSTOM requires the entire installments list.", defaultValue = "AUTOMATIC") InstallmentPlanMode installmentMode,
        @Size(max = 120) @Valid
        @Schema(description = "CUSTOM only: exactly N positive installments numbered 1..N, sum equal to totalValue; dates computed by backend")
        List<@NotNull PurchaseInstallmentPlanDto> installments
) {}
