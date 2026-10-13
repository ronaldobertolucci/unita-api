package io.github.ronaldobertolucci.unita.repository;
import io.github.ronaldobertolucci.unita.model.recurring.RecurringBill;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.*;
public interface RecurringBillRepository extends JpaRepository<RecurringBill,Long> {
 Optional<RecurringBill> findByIdAndUserId(Long id,Long userId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from RecurringBill b where b.id=:id")
 Optional<RecurringBill> lockById(Long id);
 @Query("select b from RecurringBill b where b.user.id=:userId and (:active is null or b.active=:active)")
 Page<RecurringBill> list(Long userId,Boolean active,Pageable pageable);
 List<RecurringBill> findAllByUserIdAndActiveTrue(Long userId);
 @Query("select b.id from RecurringBill b where b.active=true and b.emailEnabled=true and b.id>:after order by b.id")
 List<Long> schedulingIds(Long after,Pageable pageable);
}
