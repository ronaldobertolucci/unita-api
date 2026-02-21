package io.github.ronaldobertolucci.unita.dto.card;

import jakarta.validation.constraints.NotNull;

public record CreditCardBillPayDto(
        @NotNull Long pocketId
) {
}