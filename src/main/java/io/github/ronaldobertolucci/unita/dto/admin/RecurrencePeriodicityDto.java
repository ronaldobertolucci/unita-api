package io.github.ronaldobertolucci.unita.dto.admin;

import io.github.ronaldobertolucci.unita.model.finance.PeriodicityType;
import io.github.ronaldobertolucci.unita.model.finance.RecurrencePeriodicity;

public record RecurrencePeriodicityDto(
        Long id,
        String name,
        PeriodicityType type
) {
    public RecurrencePeriodicityDto(RecurrencePeriodicity periodicity) {
        this(periodicity.getId(), periodicity.getName(), periodicity.getType());
    }
}