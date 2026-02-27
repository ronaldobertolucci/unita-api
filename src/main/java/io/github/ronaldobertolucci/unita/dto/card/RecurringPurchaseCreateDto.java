package io.github.ronaldobertolucci.unita.dto.card;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringPurchaseCreateDto(
        @NotBlank @Size(max = 255) String description,
        @NotNull @Positive BigDecimal amount,
        @NotNull Long periodicityId,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull Long categoryId
) {}