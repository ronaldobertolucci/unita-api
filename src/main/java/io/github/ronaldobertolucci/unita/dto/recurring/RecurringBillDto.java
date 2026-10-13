package io.github.ronaldobertolucci.unita.dto.recurring;
import io.github.ronaldobertolucci.unita.model.recurring.*;
import java.math.BigDecimal;
import java.time.*;
public record RecurringBillDto(Long id,String description,BigDecimal expectedAmount,RecurringFrequency frequency,
 LocalDate firstDueDate,int reminderDaysBefore,boolean emailEnabled,boolean active,String notes,long version,Instant createdAt,Instant updatedAt) {
 public RecurringBillDto(RecurringBill b) { this(b.getId(),b.getDescription(),b.getExpectedAmount(),b.getFrequency(),b.getFirstDueDate(),b.getReminderDaysBefore(),b.isEmailEnabled(),b.isActive(),b.getNotes(),b.getVersion(),b.getCreatedAt(),b.getUpdatedAt()); }
}
