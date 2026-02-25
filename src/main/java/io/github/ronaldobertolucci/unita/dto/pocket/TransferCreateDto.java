package io.github.ronaldobertolucci.unita.dto.pocket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferCreateDto(
    @NotNull Long sourcePocketId,
    @NotNull Long targetPocketId,
    @NotNull @Positive BigDecimal amount,
    @NotBlank @Size(max = 255) String description
) {}