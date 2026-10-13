package io.github.ronaldobertolucci.unita.service.recurring;

import io.github.ronaldobertolucci.unita.model.recurring.*;
import io.github.ronaldobertolucci.unita.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** All queue mutations take the bill lock first, including edits. SMTP never holds this lock. */
@Service
@RequiredArgsConstructor
public class RecurringReminderQueue {
    private final RecurringBillRepository bills;
    private final RecurringBillEmailReminderRepository reminders;
    private final Clock recurringClock;
    private final EntityManager entityManager;

    @Value("${app.recurring.max-attempts:5}")
    private int maxAttempts;
    @Value("${app.recurring.reservation-seconds:300}")
    private int reservationSeconds;

    @jakarta.annotation.PostConstruct
    public void validateConfiguration() {
        if (maxAttempts < 1 || maxAttempts > 20 || reservationSeconds < 60 || reservationSeconds > 3600) {
            throw new IllegalArgumentException("Invalid recurring retry/reservation configuration");
        }
    }

    public record Reservation(Long reminderId, Long billId, UUID token) {}
    public record EmailPayload(String recipient, String description, BigDecimal expectedAmount, LocalDate dueDate) {}

    private LocalDate today() {
        return LocalDate.ofInstant(recurringClock.instant(), RecurringCalendar.ZONE);
    }

    private boolean eligible(RecurringBill bill) {
        return bill.isActive() && bill.isEmailEnabled() && bill.getUser().isAccessAllowed();
    }

    @Transactional
    public void schedule(Long billId) {
        var bill = bills.lockById(billId).orElse(null);
        if (bill == null || !eligible(bill)) return;
        var now = recurringClock.instant();
        var today = today();
        // 0..30 days of notice means only this month and the next can be eligible.
        for (var month : List.of(YearMonth.from(today), YearMonth.from(today).plusMonths(1))) {
            var occurrence = RecurringCalendar.occurrence(bill, month);
            if (occurrence.isEmpty()) continue;
            var due = occurrence.get();
            var scheduled = RecurringCalendar.scheduled(bill, due);
            if (due.isBefore(today) || scheduled.isAfter(now)) continue;
            String key = RecurringCalendar.key(bill, due);
            var reminder = reminders.findByBillIdAndOccurrenceKey(billId, key).orElse(null);
            // Terminal attempts and historical successes survive edits/restarts.
            if (reminder != null && reminder.getStatus() != ReminderStatus.CANCELLED) continue;
            if (reminder == null) {
                reminder = new RecurringBillEmailReminder();
                reminder.setBill(bill);
                reminder.setOccurrenceKey(key);
            }
            reminder.setDueDate(due);
            reminder.setScheduledAt(scheduled);
            reminder.setDescription(bill.getDescription());
            reminder.setExpectedAmount(bill.getExpectedAmount());
            reminder.setStatus(ReminderStatus.PENDING);
            reminder.setNextAttemptAt(now);
            reminder.setLastError(null);
            reminders.save(reminder);
        }
    }

    @Transactional
    public Optional<Reservation> reserve(Long reminderId) {
        // Read only the parent ID before locking; refresh after acquiring the lock.
        var reminder = reminders.findById(reminderId).orElse(null);
        if (reminder == null) return Optional.empty();
        var bill = bills.lockById(reminder.getBill().getId()).orElseThrow();
        // Another process may have updated the reminder while this lock was awaited.
        entityManager.refresh(reminder);
        var now = recurringClock.instant();
        boolean pending = reminder.getStatus() == ReminderStatus.PENDING
                && !reminder.getNextAttemptAt().isAfter(now);
        boolean expired = reminder.getStatus() == ReminderStatus.PROCESSING
                && !reminder.getReservedUntil().isAfter(now);
        if (!pending && !expired) return Optional.empty();
        if (!eligible(bill) || reminder.getDueDate().isBefore(today())) {
            cancel(reminder);
            return Optional.empty();
        }
        var openingTime = today().atTime(8, 0).atZone(RecurringCalendar.ZONE).toInstant();
        if (now.isBefore(openingTime)) {
            reminder.setStatus(ReminderStatus.PENDING);
            reminder.setReservationToken(null);
            reminder.setReservedUntil(null);
            reminder.setNextAttemptAt(openingTime);
            return Optional.empty();
        }
        if (reminder.getAttempts() >= maxAttempts) {
            reminder.setStatus(ReminderStatus.FAILED);
            reminder.setLastError("ATTEMPT_LIMIT");
            reminder.setReservationToken(null);
            reminder.setReservedUntil(null);
            return Optional.empty();
        }
        reminder.setStatus(ReminderStatus.PROCESSING);
        reminder.setAttempts(reminder.getAttempts() + 1);
        reminder.setReservationToken(UUID.randomUUID());
        reminder.setReservedUntil(now.plusSeconds(reservationSeconds));
        return Optional.of(new Reservation(reminder.getId(), bill.getId(), reminder.getReservationToken()));
    }

    private boolean owns(RecurringBillEmailReminder reminder, Reservation reservation) {
        return reminder.getStatus() == ReminderStatus.PROCESSING
                && Objects.equals(reminder.getReservationToken(), reservation.token())
                && reminder.getReservedUntil().isAfter(recurringClock.instant());
    }

    private void cancel(RecurringBillEmailReminder reminder) {
        reminder.setStatus(ReminderStatus.CANCELLED);
        reminder.setReservationToken(null);
        reminder.setReservedUntil(null);
    }

    /** This is the boundary at which external sending starts; edits cannot recall SMTP after it. */
    @Transactional
    public Optional<EmailPayload> prepare(Reservation reservation) {
        var bill = bills.lockById(reservation.billId()).orElse(null);
        if (bill == null) return Optional.empty();
        var reminder = reminders.findById(reservation.reminderId()).orElseThrow();
        if (!owns(reminder, reservation)) return Optional.empty();
        // Fetch current user/email, never a recipient snapshot from scheduling.
        entityManager.refresh(bill.getUser());
        if (!eligible(bill) || reminder.getDueDate().isBefore(today())) {
            cancel(reminder);
            return Optional.empty();
        }
        var openingTime = today().atTime(8, 0).atZone(RecurringCalendar.ZONE).toInstant();
        if (recurringClock.instant().isBefore(openingTime)) {
            reminder.setStatus(ReminderStatus.PENDING);
            reminder.setReservationToken(null);
            reminder.setReservedUntil(null);
            reminder.setNextAttemptAt(openingTime);
            return Optional.empty();
        }
        reminder.setRecipient(bill.getUser().getEmail());
        return Optional.of(new EmailPayload(reminder.getRecipient(), reminder.getDescription(),
                reminder.getExpectedAmount(), reminder.getDueDate()));
    }

    @Transactional
    public void complete(Reservation reservation, boolean success) {
        if (bills.lockById(reservation.billId()).isEmpty()) return;
        var reminder = reminders.findById(reservation.reminderId()).orElseThrow();
        if (!owns(reminder, reservation)) return;
        reminder.setReservationToken(null);
        reminder.setReservedUntil(null);
        if (success) {
            reminder.setStatus(ReminderStatus.SENT);
            reminder.setSentAt(recurringClock.instant());
            reminder.setLastError(null);
        } else {
            reminder.setLastError("SMTP_SEND_FAILED");
            if (reminder.getAttempts() >= maxAttempts || reminder.getDueDate().isBefore(today())) {
                reminder.setStatus(ReminderStatus.FAILED);
            } else {
                reminder.setStatus(ReminderStatus.PENDING);
                long delaySeconds = 300L * (1L << Math.min(reminder.getAttempts() - 1, 10));
                reminder.setNextAttemptAt(recurringClock.instant().plusSeconds(delaySeconds));
            }
        }
    }
}
