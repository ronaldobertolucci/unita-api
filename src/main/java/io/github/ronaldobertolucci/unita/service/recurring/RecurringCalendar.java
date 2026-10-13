package io.github.ronaldobertolucci.unita.service.recurring;
import io.github.ronaldobertolucci.unita.model.recurring.*;
import java.time.*;
import java.util.Optional;
public final class RecurringCalendar {
 public static final ZoneId ZONE=ZoneId.of("America/Sao_Paulo");
 private RecurringCalendar() {}
 public static Optional<LocalDate> occurrence(RecurringBill b,YearMonth month) {
  var anchor=b.getFirstDueDate();
  if(b.getFrequency()==RecurringFrequency.YEARLY && anchor.getMonthValue()!=month.getMonthValue()) return Optional.empty();
  var due=month.atDay(Math.min(anchor.getDayOfMonth(),month.lengthOfMonth()));
  return due.isBefore(anchor)?Optional.empty():Optional.of(due);
 }
 public static String key(RecurringBill b,LocalDate due) {
  return b.getFrequency()==RecurringFrequency.MONTHLY?YearMonth.from(due).toString():Integer.toString(due.getYear());
 }
 public static Instant scheduled(RecurringBill b,LocalDate due) {
  return due.minusDays(b.getReminderDaysBefore()).atTime(8,0).atZone(ZONE).toInstant();
 }
}
