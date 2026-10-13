package io.github.ronaldobertolucci.unita.model.recurring;
import io.github.ronaldobertolucci.unita.model.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name="recurring_bills") @Getter @Setter @NoArgsConstructor
public class RecurringBill {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
 @Column(nullable=false,length=255) private String description;
 @Column(precision=15,scale=2) private BigDecimal expectedAmount;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private RecurringFrequency frequency;
 @Column(nullable=false) private LocalDate firstDueDate;
 @Column(nullable=false) private int reminderDaysBefore;
 @Column(nullable=false) private boolean emailEnabled;
 @Column(nullable=false) private boolean active;
 @Column(length=2000) private String notes;
 @Version private long version;
 @Column(nullable=false) private Instant createdAt;
 @Column(nullable=false) private Instant updatedAt;
}
