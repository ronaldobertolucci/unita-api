package io.github.ronaldobertolucci.unita.dto.pocket;

import io.github.ronaldobertolucci.unita.model.finance.Direction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionCreateDto(
        @NotNull @Positive BigDecimal amount,
        @NotNull Direction direction,
        @NotNull LocalDate transactionDate,
        @NotBlank @Size(max = 255) String description,
        @NotNull Long categoryId
) {}