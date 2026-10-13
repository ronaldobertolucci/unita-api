package io.github.ronaldobertolucci.unita.service.scheduled;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.recurring.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
@Component @RequiredArgsConstructor @Slf4j
public class RecurringReminderJob {
 private final RecurringBillRepository bills;
 private final RecurringBillEmailReminderRepository reminders;
 private final RecurringReminderQueue queue;
 private final RecurringReminderProcessor processor;
 private final Clock recurringClock;
 @Value("${app.recurring.batch-size:100}") private int batchSize;
 @jakarta.annotation.PostConstruct
 public void validateConfiguration() {
  if(batchSize<1 || batchSize>100) throw new IllegalArgumentException("Recurring batch size must be between 1 and 100");
 }
 @Scheduled(cron="${app.recurring.schedule-cron:0 */5 * * * *}",zone="America/Sao_Paulo")
 public void schedule() {
  long after=0;
  while(true) {
   var ids=bills.schedulingIds(after,PageRequest.of(0,batchSize)); if(ids.isEmpty()) return;
   for(var id:ids) { try { queue.schedule(id); } catch(RuntimeException ex) { log.warn("Recurring scheduling failure billId={}",id); } }
   after=ids.getLast();
  }
 }
 @Scheduled(cron="${app.recurring.process-cron:30 * * * * *}",zone="America/Sao_Paulo")
 public void process() {
  for(var id:reminders.candidateIds(recurringClock.instant(),PageRequest.of(0,batchSize))) {
   try { processor.process(id); } catch(RuntimeException ex) { log.warn("Recurring processing failure reminderId={}",id); }
  }
 }
}
