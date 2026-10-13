package io.github.ronaldobertolucci.unita.service.recurring;
import io.github.ronaldobertolucci.unita.model.recurring.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class RecurringCalendarTest {
 @ParameterizedTest @CsvSource({"29,2027-02,2027-02-28","30,2027-02,2027-02-28","31,2027-02,2027-02-28","31,2028-02,2028-02-29","31,2027-03,2027-03-31","31,2027-04,2027-04-30"})
 void monthlyKeepsAnchor(int day,String month,String expected) {
  var b=new RecurringBill(); b.setFrequency(RecurringFrequency.MONTHLY); b.setFirstDueDate(LocalDate.of(2026,1,day));
  assertEquals(LocalDate.parse(expected),RecurringCalendar.occurrence(b,YearMonth.parse(month)).orElseThrow());
  assertEquals(day,b.getFirstDueDate().getDayOfMonth());
 }
 @Test void leapYearReturnsAndOtherMonthsAreAbsent() {
  var b=new RecurringBill(); b.setFrequency(RecurringFrequency.YEARLY); b.setFirstDueDate(LocalDate.of(2024,2,29));
  assertEquals(LocalDate.of(2027,2,28),RecurringCalendar.occurrence(b,YearMonth.of(2027,2)).orElseThrow());
  assertEquals(LocalDate.of(2028,2,29),RecurringCalendar.occurrence(b,YearMonth.of(2028,2)).orElseThrow());
  assertTrue(RecurringCalendar.occurrence(b,YearMonth.of(2028,3)).isEmpty());
  assertTrue(RecurringCalendar.occurrence(b,YearMonth.of(2024,1)).isEmpty());
 }
 @Test void noOccurrenceBeforeAnchorAndNoticeCrossesYear() {
  var b=new RecurringBill(); b.setFrequency(RecurringFrequency.MONTHLY); b.setFirstDueDate(LocalDate.of(2027,1,1)); b.setReminderDaysBefore(3);
  assertTrue(RecurringCalendar.occurrence(b,YearMonth.of(2026,12)).isEmpty());
  assertEquals(Instant.parse("2026-12-29T11:00:00Z"),RecurringCalendar.scheduled(b,b.getFirstDueDate()));
  assertEquals("2027-01",RecurringCalendar.key(b,b.getFirstDueDate())); b.setFrequency(RecurringFrequency.YEARLY);
  assertEquals("2027",RecurringCalendar.key(b,b.getFirstDueDate()));
 }
}
