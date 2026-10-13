package io.github.ronaldobertolucci.unita.repository;
import io.github.ronaldobertolucci.unita.model.recurring.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.*;
public interface RecurringBillEmailReminderRepository extends JpaRepository<RecurringBillEmailReminder,Long> {
 Optional<RecurringBillEmailReminder> findByBillIdAndOccurrenceKey(Long billId,String occurrenceKey);
 List<RecurringBillEmailReminder> findAllByBillId(Long billId);
 boolean existsByBillIdAndAttemptsGreaterThan(Long billId,int attempts);
 @Query("select r.id from RecurringBillEmailReminder r where (r.status=io.github.ronaldobertolucci.unita.model.recurring.ReminderStatus.PENDING and r.nextAttemptAt<=:now) or (r.status=io.github.ronaldobertolucci.unita.model.recurring.ReminderStatus.PROCESSING and r.reservedUntil<=:now) order by r.id")
 List<Long> candidateIds(Instant now,Pageable pageable);
}
