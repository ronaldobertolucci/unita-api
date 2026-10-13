package io.github.ronaldobertolucci.unita.integration;

import io.github.ronaldobertolucci.unita.dto.recurring.*;
import io.github.ronaldobertolucci.unita.model.recurring.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.email.EmailService;
import io.github.ronaldobertolucci.unita.service.recurring.*;
import io.github.ronaldobertolucci.unita.service.scheduled.RecurringReminderJob;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Import(RecurringBillIntegrationTest.TimeConfig.class)
class RecurringBillIntegrationTest {
 static class MutableClock extends Clock {
  AtomicReference<Instant> value=new AtomicReference<>();
  public ZoneId getZone() { return ZoneOffset.UTC; }
  public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
  public Instant instant() { return value.get(); }
  void set(String instant) { value.set(Instant.parse(instant)); }
 }
 @TestConfiguration static class TimeConfig { @Bean @Primary MutableClock testRecurringClock() { return new MutableClock(); } }
 @Autowired MutableClock clock;
 @Autowired MockMvc mvc;
 @Autowired RecurringBillService service;
 @Autowired RecurringReminderQueue queue;
 @Autowired RecurringReminderProcessor processor;
 @Autowired RecurringReminderJob job;
 @Autowired RecurringBillRepository bills;
 @Autowired RecurringBillEmailReminderRepository reminders;
 @Autowired UserRepository users;
 @Autowired RoleRepository roles;
 @Autowired TransactionRepository transactions;
 @Autowired CreditCardPurchaseRepository purchases;
 @MockitoBean EmailService email;
 User owner,other;
 @BeforeEach void setup() {
  reminders.deleteAll(); bills.deleteAll(); reset(email);
  clock.set("2026-10-13T12:00:00Z"); owner=newUser(); other=newUser();
 }
 User newUser() { return users.save(User.builder().firstName("Recurring").lastName("Test").email(UUID.randomUUID()+"@example.invalid")
  .password("encoded").enabled(true).emailVerified(true).dateOfBirth(LocalDate.of(1990,1,1))
  .roles(Set.of(roles.findByName("USER").orElseThrow())).build()); }
 UsernamePasswordAuthenticationToken auth(User u) { return new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()); }
 RecurringBillDto create(RecurringFrequency f,LocalDate due) { return service.create(new RecurringBillCreateDto("Internet <script>",null,f,due,null,null,null,null),auth(owner)); }
 RecurringBillDto create() { return create(RecurringFrequency.MONTHLY,LocalDate.of(2026,10,16)); }
 RecurringBillUpdateDto edit(RecurringBillDto b,boolean active,boolean enabled,LocalDate due) { return new RecurringBillUpdateDto(b.version(),b.description(),b.expectedAmount(),b.frequency(),due,b.reminderDaysBefore(),enabled,active,b.notes()); }
 Long schedule(RecurringBillDto b) { queue.schedule(b.id()); return reminders.findAllByBillId(b.id()).getFirst().getId(); }
 String path(String suffix) { return "/api/recurring-bills"+suffix; }
 String body() { return "{\"description\":\"Internet\",\"frequency\":\"MONTHLY\",\"firstDueDate\":\"2026-01-31\"}"; }
 @Test void apiDefaultsPaginationAndProjectionsArePersonal() throws Exception {
  mvc.perform(post(path("")).contextPath("/api").with(user(owner)).contentType("application/json").content(body()))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.expectedAmount").isEmpty()).andExpect(jsonPath("$.reminderDaysBefore").value(3))
   .andExpect(jsonPath("$.emailEnabled").value(true)).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.version").value(0));
  var yearly=create(RecurringFrequency.YEARLY,LocalDate.of(2024,2,29));
  service.create(new RecurringBillCreateDto("Other owner",null,RecurringFrequency.MONTHLY,LocalDate.of(2026,1,1),null,null,null,null),auth(other));
  mvc.perform(get(path("")).contextPath("/api").with(user(owner))).andExpect(status().isOk())
   .andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(2));
  mvc.perform(get(path("/upcoming")).contextPath("/api").with(user(owner)).param("month","2027-02"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].dueDate").value("2027-02-28"));
  mvc.perform(get(path("/upcoming")).contextPath("/api").with(user(owner)).param("month","2027-03"))
   .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].dueDate").value("2027-03-31"));
  var paused=service.update(yearly.id(),edit(yearly,false,true,yearly.firstDueDate()),auth(owner));
  mvc.perform(get(path("")).contextPath("/api").with(user(owner)).param("active","false"))
   .andExpect(jsonPath("$.content[0].id").value(paused.id())).andExpect(jsonPath("$.totalElements").value(1));
 }
 @Test void unavailableIdsHaveSame404AndNoChanges() throws Exception {
  var b=create(); var dto="{\"version\":0,\"description\":\"Changed\",\"frequency\":\"MONTHLY\",\"firstDueDate\":\"2026-10-16\",\"reminderDaysBefore\":3,\"active\":true,\"emailEnabled\":true}";
  for(long id:List.of(b.id(),Long.MAX_VALUE)) {
   mvc.perform(get(path("/"+id)).contextPath("/api").with(user(other))).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Recurring bill not found"));
   mvc.perform(patch(path("/"+id)).contextPath("/api").with(user(other)).contentType("application/json").content(dto)).andExpect(status().isNotFound());
  }
  assertEquals(b,service.detail(b.id(),auth(owner)));
 }
 @Test void versionConflictIs409AndPauseRemainsVisible() throws Exception {
  var b=create(); var edited=service.update(b.id(),edit(b,false,true,b.firstDueDate()),auth(owner));
  assertEquals(1,edited.version()); assertThrows(IllegalStateException.class,()->service.update(b.id(),edit(b,true,true,b.firstDueDate()),auth(owner)));
  mvc.perform(patch(path("/"+b.id())).contextPath("/api").with(user(owner)).contentType("application/json")
   .content("{\"version\":0,\"description\":\"Internet\",\"frequency\":\"MONTHLY\",\"firstDueDate\":\"2026-10-16\",\"reminderDaysBefore\":3,\"active\":true,\"emailEnabled\":true}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Recurring bill version conflict"));
  assertTrue(service.upcoming(auth(owner),"2026-10").isEmpty()); assertFalse(service.detail(b.id(),auth(owner)).active());
 }
 @ParameterizedTest @ValueSource(strings={"0","-1","1.001","10000000000000"})
 void invalidMoneyRejectedWithoutPersistence(String amount) throws Exception {
  mvc.perform(post(path("")).contextPath("/api").with(user(owner)).contentType("application/json").content(body().replace("}",",\"expectedAmount\":"+amount+"}")))
   .andExpect(status().isBadRequest()); assertEquals(0,bills.count());
 }
 @ParameterizedTest @ValueSource(strings={"{}","{\"description\":\" \"}","{\"frequency\":\"WEEKLY\"}","{\"firstDueDate\":\"invalid\"}"})
 void invalidBodiesReturn400(String value) throws Exception {
  mvc.perform(post(path("")).contextPath("/api").with(user(owner)).contentType("application/json").content(value)).andExpect(status().isBadRequest());
 }
 @Test void invalidQueryAndMissingAuthHaveRealHttpErrors() throws Exception {
  mvc.perform(get(path("")).contextPath("/api")).andExpect(status().isUnauthorized());
  for(String month:List.of("2026-13","2026-1","garbage","0000-01")) mvc.perform(get(path("/upcoming")).contextPath("/api").with(user(owner)).param("month",month)).andExpect(status().isBadRequest());
  mvc.perform(get(path("/upcoming")).contextPath("/api").with(user(owner))).andExpect(status().isBadRequest());
  for(String size:List.of("101","0","bad")) mvc.perform(get(path("")).contextPath("/api").with(user(owner)).param("size",size)).andExpect(status().isBadRequest());
 }
 @Test void individualEmailsPersistSuccessAndNoFinancialWrites() {
  long initialTransactions=transactions.count(),initialPurchases=purchases.count();
  var b=create(); var annual=create(RecurringFrequency.YEARLY,LocalDate.of(2025,10,16));
  job.schedule(); job.schedule(); assertEquals(2,reminders.count()); job.process(); job.process();
  verify(email,times(2)).sendHtmlEmailSynchronously(eq(owner.getEmail()),eq("Vencimento previsto"),contains("Valor previsto: não informado"));
  assertTrue(reminders.findAll().stream().allMatch(r->r.getStatus()==ReminderStatus.SENT && r.getSentAt()!=null && r.getRecipient().equals(owner.getEmail())));
  assertEquals(initialTransactions,transactions.count()); assertEquals(initialPurchases,purchases.count());
  assertEquals(1,reminders.findAllByBillId(annual.id()).size());
  var changed=service.update(b.id(),edit(b,true,true,LocalDate.of(2026,10,15)),auth(owner));
  queue.schedule(changed.id()); job.process(); verify(email,times(2)).sendHtmlEmailSynchronously(anyString(),anyString(),anyString());
  assertThrows(IllegalStateException.class,()->service.update(changed.id(),new RecurringBillUpdateDto(changed.version(),changed.description(),null,RecurringFrequency.YEARLY,changed.firstDueDate(),3,true,true,null),auth(owner)));
 }
 @Test void editUpdatesUnreservedSnapshotWithoutSecondOccurrence() {
  var b=create(); Long id=schedule(b);
  var updated=service.update(b.id(),new RecurringBillUpdateDto(b.version(),"New description",new java.math.BigDecimal("12.34"),b.frequency(),LocalDate.of(2026,10,15),3,true,true,"notes"),auth(owner));
  assertEquals(ReminderStatus.CANCELLED,reminders.findById(id).orElseThrow().getStatus()); queue.schedule(updated.id()); processor.process(id);
  assertEquals(1,reminders.count()); verify(email).sendHtmlEmailSynchronously(anyString(),anyString(),contains("New description"));
  var r=reminders.findById(id).orElseThrow(); assertEquals(LocalDate.of(2026,10,15),r.getDueDate()); assertEquals(ReminderStatus.SENT,r.getStatus());
 }
 @Test void pauseAndDisableCancelUnstartedSendingEvenWhenReserved() {
  for(boolean pause:List.of(true,false)) {
   var b=create(); var id=schedule(b); var reservation=queue.reserve(id).orElseThrow();
   service.update(b.id(),edit(b,!pause,pause,b.firstDueDate()),auth(owner));
   assertTrue(queue.prepare(reservation).isEmpty()); assertEquals(ReminderStatus.CANCELLED,reminders.findById(id).orElseThrow().getStatus());
  }
  verifyNoInteractions(email);
 }
 @Test void userEligibilityAndCurrentEmailAreRevalidated() {
  var b=create(); var id=schedule(b); var reserved=queue.reserve(id).orElseThrow();
  String currentEmail=UUID.randomUUID()+"@example.invalid"; owner.setEmail(currentEmail); users.save(owner);
  assertEquals(currentEmail,queue.prepare(reserved).orElseThrow().recipient());
  owner.setEnabled(false); users.save(owner); assertTrue(queue.prepare(reserved).isEmpty());
  owner.setEnabled(true); owner.setEmailVerified(false); users.save(owner);
  var next=create(); queue.schedule(next.id()); assertTrue(reminders.findAllByBillId(next.id()).isEmpty());
  verifyNoInteractions(email);
 }
 @Test void smtpFailuresRetryPersistentlyAndStopAtFive() {
  doThrow(new RuntimeException("secret SMTP text")).when(email).sendHtmlEmailSynchronously(anyString(),anyString(),anyString());
  var b=create(); var id=schedule(b);
  String[] instants={"2026-10-13T12:00:00Z","2026-10-13T12:05:00Z","2026-10-13T12:15:00Z","2026-10-13T12:35:00Z","2026-10-13T13:15:00Z"};
  for(int i=0;i<instants.length;i++) { clock.set(instants[i]); processor.process(id); var r=reminders.findById(id).orElseThrow(); assertEquals(i+1,r.getAttempts()); assertEquals("SMTP_SEND_FAILED",r.getLastError()); }
  assertEquals(ReminderStatus.FAILED,reminders.findById(id).orElseThrow().getStatus()); queue.schedule(b.id()); processor.process(id);
  verify(email,times(5)).sendHtmlEmailSynchronously(anyString(),anyString(),anyString());
 }
 @Test void retryWaitIsRespectedAndNextProcessCanRecoverAfterRestart() {
  doThrow(new RuntimeException()).doNothing().when(email).sendHtmlEmailSynchronously(anyString(),anyString(),anyString());
  var id=schedule(create()); processor.process(id); processor.process(id); verify(email,times(1)).sendHtmlEmailSynchronously(anyString(),anyString(),anyString());
  clock.set("2026-10-13T12:05:00Z"); job.process(); assertEquals(ReminderStatus.SENT,reminders.findById(id).orElseThrow().getStatus());
 }
 @Test void expiredReservationRecoveredAndOldTokenCannotConfirm() {
  var id=schedule(create()); var first=queue.reserve(id).orElseThrow(); assertTrue(queue.reserve(id).isEmpty());
  clock.set("2026-10-13T12:05:01Z"); var second=queue.reserve(id).orElseThrow();
  assertNotEquals(first.token(),second.token()); queue.complete(first,true); assertEquals(ReminderStatus.PROCESSING,reminders.findById(id).orElseThrow().getStatus());
  assertTrue(queue.prepare(first).isEmpty()); assertTrue(queue.prepare(second).isPresent()); queue.complete(second,true);
  assertEquals(ReminderStatus.SENT,reminders.findById(id).orElseThrow().getStatus()); assertEquals(2,reminders.findById(id).orElseThrow().getAttempts());
 }
 @Test void noReminderBeforeEightOrAfterDueAndLateCreationIsRecovered() {
  clock.set("2026-10-13T10:59:59Z"); var b=create(); queue.schedule(b.id()); assertEquals(0,reminders.count());
  clock.set("2026-10-13T11:00:00Z"); var id=schedule(b);
  clock.set("2026-10-17T12:00:00Z"); processor.process(id); assertEquals(ReminderStatus.CANCELLED,reminders.findById(id).orElseThrow().getStatus()); verifyNoInteractions(email);
  var late=create(RecurringFrequency.MONTHLY,LocalDate.of(2020,10,17)); queue.schedule(late.id()); job.process(); verify(email).sendHtmlEmailSynchronously(anyString(),anyString(),contains("2026-10-17"));
 }
 @Test void retryAtNightWaitsForEightWithoutNewAttempt() {
  var id=schedule(create()); var reservation=queue.reserve(id).orElseThrow();
  clock.set("2026-10-14T04:00:00Z"); assertTrue(queue.reserve(id).isEmpty()); assertEquals(1,reminders.findById(id).orElseThrow().getAttempts());
  clock.set("2026-10-14T11:00:00Z"); processor.process(id); assertEquals(ReminderStatus.SENT,reminders.findById(id).orElseThrow().getStatus());
  queue.complete(reservation,false); assertEquals(ReminderStatus.SENT,reminders.findById(id).orElseThrow().getStatus());
 }
 @Test void htmlEscapesUserTextAndUsesApprovedLink() {
  String html=processor.render(new RecurringReminderQueue.EmailPayload("test@example.invalid","<script>'\"&",null,LocalDate.of(2026,10,16)));
  assertFalse(html.contains("<script>")); assertTrue(html.contains("&lt;script&gt;")); assertTrue(html.contains("http://localhost:4200/recurring-bills"));
  assertTrue(html.contains("Vencimento previsto")); assertTrue(html.contains("Valor previsto")); assertFalse(html.contains("em atraso")); assertFalse(html.contains("não pago"));
 }
 @Test void prepareCrossingMidnightDefersSendingUntilEight() {
  var id=schedule(create()); clock.set("2026-10-13T23:59:59Z"); var reserved=queue.reserve(id).orElseThrow();
  // A separate reserve near local midnight remains valid on the next day.
  clock.set("2026-10-14T02:59:59Z"); var next=queue.reserve(id).orElseThrow();
  clock.set("2026-10-14T03:00:01Z"); assertTrue(queue.prepare(next).isEmpty());
  assertEquals(ReminderStatus.PENDING,reminders.findById(id).orElseThrow().getStatus());
  assertEquals(Instant.parse("2026-10-14T11:00:00Z"),reminders.findById(id).orElseThrow().getNextAttemptAt());
 }
 @Test void failedSendingStillPreventsFrequencyChange() {
  doThrow(new RuntimeException()).when(email).sendHtmlEmailSynchronously(anyString(),anyString(),anyString());
  var b=create(); processor.process(schedule(b));
  assertThrows(IllegalStateException.class,()->service.update(b.id(),new RecurringBillUpdateDto(b.version(),b.description(),null,RecurringFrequency.YEARLY,b.firstDueDate(),3,true,true,null),auth(owner)));
 }
 @Test void databaseUniqueConstraintProtectsOccurrencePeriod() {
  var id=schedule(create()); var existing=reminders.findById(id).orElseThrow();
  var duplicate=new RecurringBillEmailReminder(); duplicate.setBill(existing.getBill()); duplicate.setOccurrenceKey(existing.getOccurrenceKey());
  duplicate.setDueDate(existing.getDueDate()); duplicate.setScheduledAt(existing.getScheduledAt()); duplicate.setDescription(existing.getDescription());
  duplicate.setStatus(ReminderStatus.PENDING); duplicate.setNextAttemptAt(existing.getNextAttemptAt());
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->reminders.saveAndFlush(duplicate));
  assertEquals(1,reminders.count());
 }
 @ParameterizedTest @ValueSource(ints={-1,31})
 void noticeLimitsAreValidated(int days) throws Exception {
  mvc.perform(post(path("")).contextPath("/api").with(user(owner)).contentType("application/json").content(body().replace("}",",\"reminderDaysBefore\":"+days+"}")))
   .andExpect(status().isBadRequest()); assertEquals(0,bills.count());
 }
 @Test void apiDocumentsRealRoutesAndSchemas() throws Exception {
  mvc.perform(get("/api/v3/api-docs").contextPath("/api")).andExpect(status().isOk())
   .andExpect(jsonPath("$.paths['/recurring-bills/{id}'].patch.responses['409']").exists())
   .andExpect(jsonPath("$.components.schemas.RecurringBillUpdateDto.properties.version").exists());
 }
 interface Work<T> { T run() throws Exception; }
 <T> List<T> concurrent(Work<T> work) throws Exception {
  var pool=Executors.newFixedThreadPool(2); var barrier=new CyclicBarrier(2);
  try { List<Future<T>> futures=new ArrayList<>(); for(int i=0;i<2;i++) futures.add(pool.submit(()->{barrier.await(5,TimeUnit.SECONDS); return work.run();}));
   List<T> result=new ArrayList<>(); for(var f:futures) result.add(f.get(15,TimeUnit.SECONDS)); return result;
  } finally { pool.shutdownNow(); }
 }
 @Test void concurrentSchedulersOnlyPersistOnePeriod() throws Exception {
  var b=create(); concurrent(()->{queue.schedule(b.id());return true;}); assertEquals(1,reminders.count());
 }
 @Test void concurrentReservationsHaveExactlyOneOwner() throws Exception {
  var id=schedule(create()); var reservations=concurrent(()->queue.reserve(id)); assertEquals(1,reservations.stream().filter(Optional::isPresent).count());
  assertEquals(1,reminders.findById(id).orElseThrow().getAttempts());
 }
 @Test void concurrentProcessorsOnlySendOneEmail() throws Exception {
  var id=schedule(create()); concurrent(()->{processor.process(id);return true;}); verify(email,times(1)).sendHtmlEmailSynchronously(anyString(),anyString(),anyString());
  assertEquals(ReminderStatus.SENT,reminders.findById(id).orElseThrow().getStatus());
 }
 @Test void concurrentEditsDoNotSilentlyOverwrite() throws Exception {
  var b=create(); var results=concurrent(()->{try { service.update(b.id(),edit(b,false,true,b.firstDueDate()),auth(owner)); return "ok"; } catch(IllegalStateException ex) { return "conflict"; }});
  assertEquals(1,Collections.frequency(results,"ok")); assertEquals(1,Collections.frequency(results,"conflict"));
 }
}
