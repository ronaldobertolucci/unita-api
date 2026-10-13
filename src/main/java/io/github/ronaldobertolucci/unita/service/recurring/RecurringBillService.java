package io.github.ronaldobertolucci.unita.service.recurring;

import io.github.ronaldobertolucci.unita.dto.recurring.*;
import io.github.ronaldobertolucci.unita.model.recurring.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecurringBillService {
    private final RecurringBillRepository bills;
    private final RecurringBillEmailReminderRepository reminders;
    private final Clock recurringClock;
    private final Validator validator;

    private Long owner(Authentication auth) {
        return ((User) auth.getPrincipal()).getId();
    }

    private void validate(Object dto) {
        if (!validator.validate(dto).isEmpty()) {
            throw new IllegalArgumentException("Invalid recurring bill input");
        }
    }

    private void validateDate(LocalDate date) {
        if (date.getYear() < 1900 || date.getYear() > 9998) {
            throw new IllegalArgumentException("Date year must be between 1900 and 9998");
        }
    }

    @Transactional
    public RecurringBillDto create(RecurringBillCreateDto dto, Authentication auth) {
        validate(dto);
        validateDate(dto.firstDueDate());
        var bill = new RecurringBill();
        bill.setUser((User) auth.getPrincipal());
        bill.setDescription(dto.description());
        bill.setExpectedAmount(dto.expectedAmount());
        bill.setFrequency(dto.frequency());
        bill.setFirstDueDate(dto.firstDueDate());
        bill.setReminderDaysBefore(dto.reminderDaysBefore() == null ? 3 : dto.reminderDaysBefore());
        bill.setEmailEnabled(dto.emailEnabled() == null || dto.emailEnabled());
        bill.setActive(dto.active() == null || dto.active());
        bill.setNotes(dto.notes());
        bill.setCreatedAt(recurringClock.instant());
        bill.setUpdatedAt(bill.getCreatedAt());
        return new RecurringBillDto(bills.saveAndFlush(bill));
    }

    public RecurringBillDto detail(Long id, Authentication auth) {
        return new RecurringBillDto(bills.findByIdAndUserId(id, owner(auth))
                .orElseThrow(() -> new EntityNotFoundException("Recurring bill not found")));
    }

    public RecurringBillPageDto list(Authentication auth, Boolean active, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size between 1 and 100");
        }
        var result = bills.list(owner(auth), active,
                PageRequest.of(page, size, Sort.by("description", "id")));
        return new RecurringBillPageDto(result.map(RecurringBillDto::new).getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public List<RecurringOccurrenceDto> upcoming(Authentication auth, String month) {
        YearMonth requestedMonth;
        try {
            if (month == null || !month.matches("\\d{4}-\\d{2}")) {
                throw new IllegalArgumentException();
            }
            requestedMonth = YearMonth.parse(month);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("month must be YYYY-MM");
        }
        if (requestedMonth.getYear() < 1900 || requestedMonth.getYear() > 9998) {
            throw new IllegalArgumentException("Month year must be between 1900 and 9998");
        }
        return bills.findAllByUserIdAndActiveTrue(owner(auth)).stream()
                .flatMap(bill -> RecurringCalendar.occurrence(bill, requestedMonth).stream()
                        .map(due -> new RecurringOccurrenceDto(bill.getId(), bill.getDescription(),
                                bill.getExpectedAmount(), due)))
                .sorted(Comparator.comparing(RecurringOccurrenceDto::dueDate)
                        .thenComparing(RecurringOccurrenceDto::description)
                        .thenComparing(RecurringOccurrenceDto::recurringBillId))
                .toList();
    }

    @Transactional
    public RecurringBillDto update(Long id, RecurringBillUpdateDto dto, Authentication auth) {
        validate(dto);
        validateDate(dto.firstDueDate());
        var bill = bills.lockById(id).filter(b -> b.getUser().getId().equals(owner(auth)))
                .orElseThrow(() -> new EntityNotFoundException("Recurring bill not found"));
        if (bill.getVersion() != dto.version()) {
            throw new IllegalStateException("Recurring bill version conflict");
        }
        if (bill.getFrequency() != dto.frequency() && reminders.existsByBillIdAndAttemptsGreaterThan(id, 0)) {
            throw new IllegalStateException("Frequency cannot change after sending has started");
        }
        bill.setDescription(dto.description());
        bill.setExpectedAmount(dto.expectedAmount());
        bill.setFrequency(dto.frequency());
        bill.setFirstDueDate(dto.firstDueDate());
        bill.setReminderDaysBefore(dto.reminderDaysBefore());
        bill.setEmailEnabled(dto.emailEnabled());
        bill.setActive(dto.active());
        bill.setNotes(dto.notes());
        bill.setUpdatedAt(recurringClock.instant());
        for (var reminder : reminders.findAllByBillId(id)) {
            if (reminder.getStatus() == ReminderStatus.PENDING) {
                reminder.setStatus(ReminderStatus.CANCELLED);
            }
        }
        return new RecurringBillDto(bills.saveAndFlush(bill));
    }
}
