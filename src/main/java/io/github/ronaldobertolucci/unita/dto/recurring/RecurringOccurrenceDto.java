package io.github.ronaldobertolucci.unita.dto.recurring;
import java.math.BigDecimal;
import java.time.LocalDate;
public record RecurringOccurrenceDto(Long recurringBillId,String description,BigDecimal expectedAmount,LocalDate dueDate) {}
