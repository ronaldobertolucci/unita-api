package io.github.ronaldobertolucci.unita.dto.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreditCardCreateDto(
        @NotNull Long legalEntityId,
        @NotBlank @Size(min = 4, max = 4) String lastFourDigits,
        @NotNull Long cardBrandId,
        @NotNull @Positive BigDecimal creditLimit,
        @NotNull @Min(1) @Max(31) Integer closingDay,
        @NotNull @Min(1) @Max(31) Integer dueDay
) {
}