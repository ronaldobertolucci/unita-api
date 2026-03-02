package io.github.ronaldobertolucci.unita.dto.investment;

import io.github.ronaldobertolucci.unita.model.investment.Indexer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FixedIncomeAssetCreateDto(
    @NotBlank @Size(max = 255) String name,
    @NotNull Long legalEntityId,
    @NotNull Indexer indexer,
    @NotNull BigDecimal annualRate,
    @NotNull LocalDate maturityDate,
    @NotNull Boolean taxFree
) {}