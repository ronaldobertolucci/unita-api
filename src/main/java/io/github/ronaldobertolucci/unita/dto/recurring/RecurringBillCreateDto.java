package io.github.ronaldobertolucci.unita.dto.recurring;
import io.github.ronaldobertolucci.unita.model.recurring.RecurringFrequency;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
public record RecurringBillCreateDto(
 @NotBlank @Size(max=255) String description,
 @DecimalMin(value="0",inclusive=false) @Digits(integer=13,fraction=2) @Schema(description="Optional predicted amount, positive, at most two decimal places") BigDecimal expectedAmount,
 @NotNull RecurringFrequency frequency,
 @NotNull @Schema(description="Calendar anchor; past dates allowed, no overdue reminders",example="2026-01-31") LocalDate firstDueDate,
 @Min(0) @Max(30) @Schema(defaultValue="3") Integer reminderDaysBefore,
 @Schema(defaultValue="true") Boolean emailEnabled,
 @Schema(defaultValue="true") Boolean active,
 @Size(max=2000) String notes) {}
