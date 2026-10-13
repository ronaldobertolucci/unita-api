package io.github.ronaldobertolucci.unita.dto.recurring;
import io.github.ronaldobertolucci.unita.model.recurring.RecurringFrequency;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
@Schema(description="Complete editable representation; version required. Null expectedAmount/notes clears them.")
public record RecurringBillUpdateDto(
 @NotNull @Min(0) Long version,
 @NotBlank @Size(max=255) String description,
 @DecimalMin(value="0",inclusive=false) @Digits(integer=13,fraction=2) BigDecimal expectedAmount,
 @NotNull RecurringFrequency frequency,
 @NotNull LocalDate firstDueDate,
 @NotNull @Min(0) @Max(30) Integer reminderDaysBefore,
 @NotNull Boolean emailEnabled,
 @NotNull Boolean active,
 @Size(max=2000) String notes) {}
