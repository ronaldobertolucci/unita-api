package io.github.ronaldobertolucci.unita.model.recurring;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="recurring_bill_email_reminders") @Getter @Setter @NoArgsConstructor
public class RecurringBillEmailReminder {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="recurring_bill_id",nullable=false) private RecurringBill bill;
 @Column(nullable=false,length=7) private String occurrenceKey;
 @Column(nullable=false) private LocalDate dueDate;
 @Column(nullable=false) private Instant scheduledAt;
 @Column(nullable=false,length=255) private String description;
 @Column(precision=15,scale=2) private BigDecimal expectedAmount;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private ReminderStatus status;
 @Column(nullable=false) private int attempts;
 @Column(nullable=false) private Instant nextAttemptAt;
 private UUID reservationToken;
 private Instant reservedUntil;
 private Instant sentAt;
 @Column(length=255) private String recipient;
 @Column(length=100) private String lastError;
}
