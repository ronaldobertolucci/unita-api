package io.github.ronaldobertolucci.unita.integration;

import io.github.ronaldobertolucci.unita.dto.card.*;
import io.github.ronaldobertolucci.unita.dto.pocket.*;
import io.github.ronaldobertolucci.unita.model.card.*;
import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import io.github.ronaldobertolucci.unita.model.finance.*;
import io.github.ronaldobertolucci.unita.model.group.*;
import io.github.ronaldobertolucci.unita.model.pocket.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.card.CreditCardService;
import io.github.ronaldobertolucci.unita.service.finance.FinancialConcurrencyService;
import io.github.ronaldobertolucci.unita.service.pocket.*;
import io.github.ronaldobertolucci.unita.service.scheduled.CreditCardBillClosingJob;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Lifecycle API matrix on H2/PostgreSQL; row-lock races require PostgreSQL. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class BillLifecycleIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PocketRepository pockets;
    @Autowired TransactionRepository transactions;
    @Autowired CategoryRepository categories;
    @Autowired LegalEntityRepository entities;
    @Autowired BankAccountTypeRepository bankTypes;
    @Autowired CardBrandRepository brands;
    @Autowired CreditCardBillRepository bills;
    @Autowired CreditCardService cards;
    @Autowired FinancialConcurrencyService locks;
    @Autowired MockMvc mvc;
    @Autowired io.github.ronaldobertolucci.unita.service.user.UserService userService;
    TransactionTemplate tx;
    ExecutorService pool;
    User owner;
    Pocket source, target;
    Category sent, received;
    CreditCardBill bill;
    Long purchaseId, installmentId;

    @BeforeEach void setup() {
        tx=new TransactionTemplate(manager);
        pool=Executors.newFixedThreadPool(2);
        tx.executeWithoutResult(status->{
            owner=users.save(User.builder().firstName("Concurrent").lastName("Test").email(UUID.randomUUID()+"@example.invalid")
                    .password("encoded").dateOfBirth(LocalDate.of(1990,1,1)).enabled(true).emailVerified(true)
                    .roles(Set.of(roles.findByName("USER").orElseThrow())).build());
            var entity=entities.save(LegalEntity.builder().user(owner).cnpj(String.format("%014d",owner.getId())).corporateName("Test bank").build());
            source=pockets.save(BankAccount.builder().user(owner).legalEntity(entity).number("1").agency("1")
                    .bankAccountType(bankTypes.findAll().getFirst()).status(BankAccountStatus.ACTIVE).build());
            var cash=new Cash();cash.setUser(owner);target=pockets.save(cash);
            var group=Group.builder().name("Concurrent group").responsibleUser(owner).build();em.persist(group);
            em.persist(GroupMembership.builder().user(owner).group(group).build());
            sent=categories.findSystemByName("Transferência Enviada").orElseThrow();
            received=categories.findSystemByName("Transferência Recebida").orElseThrow();
            opening(source);opening(target);
            var card=CreditCard.builder().user(owner).legalEntity(entity).cardBrand(brands.findAll().getFirst())
                    .lastFourDigits("1234").creditLimit(new BigDecimal("500")).closingDay(10).dueDay(20).build();em.persist(card);
            bill=bills.save(CreditCardBill.builder().creditCard(card).status(CreditCardBillStatus.CLOSED)
                    .closingDay(10).dueDay(20).closingDate(LocalDate.now().minusDays(1)).dueDate(LocalDate.now().plusDays(10))
                    .periodStart(LocalDate.now().minusMonths(1)).build());
            var purchase=CreditCardPurchase.builder().creditCard(card).description("Purchase").totalValue(new BigDecimal("100"))
                    .purchaseDate(LocalDate.now()).installmentsCount(1).build();em.persist(purchase);purchaseId=purchase.getId();
            var installment=CreditCardInstallment.builder().purchase(purchase).installmentNumber(1).installmentDate(LocalDate.now())
                    .amount(new BigDecimal("100")).creditCardBill(bill).category(sent).build();em.persist(installment);installmentId=installment.getId();
        });
    }
    @AfterEach void stop() throws Exception {
        if(pool!=null){pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
        if(owner!=null) tx.executeWithoutResult(s -> {
            // Remove only this test's committed fixtures, including protected states.
            // Production services intentionally refuse these deletions.
            jdbc.update("DELETE FROM credit_card_installments WHERE purchase_id IN (SELECT id FROM credit_card_purchases WHERE credit_card_id IN (SELECT id FROM credit_cards WHERE user_id=?))",owner.getId());
            jdbc.update("DELETE FROM credit_cards WHERE user_id=?",owner.getId());
            jdbc.update("DELETE FROM users WHERE id=?",owner.getId());
        });
    }
    void opening(Pocket pocket) {
        transactions.save(Transaction.builder().pocket(pocket).direction(Direction.INCOME).category(received)
                .amount(new BigDecimal("100")).description("Opening").transactionDate(LocalDate.now()).build());
    }
    UsernamePasswordAuthenticationToken auth(){return new UsernamePasswordAuthenticationToken(owner,null,owner.getAuthorities());}
    Long cardId(){return bill.getCreditCard().getId();}
    String billPath(){return "/api/credit-cards/"+cardId()+"/bills/"+bill.getId();}
    int pay(){try{return mvc.perform(put(billPath()+"/pay").contextPath("/api").with(user(owner))
            .contentType("application/json").content("{\"pocketId\":"+source.getId()+"}")).andReturn().getResponse().getStatus();}catch(Exception e){throw new RuntimeException(e);}}
    void balance(Pocket pocket,String amount){assertEquals(0,new BigDecimal(amount).compareTo(transactions.calculateBalanceByPocketId(pocket.getId())));}
    int waiting(){return jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",Integer.class);}
    void awaitWaiters(int count){
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(waiting()<count && System.nanoTime()<deadline){try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}
        assertTrue(waiting()>=count,"Both writers must overlap while waiting on PostgreSQL locks");
    }
    void leader(Runnable blocker,Runnable action,Runnable follower) throws Exception {
        List<Future<?>> future=new ArrayList<>();
        tx.executeWithoutResult(status->{blocker.run();action.run();future.add(pool.submit(follower));awaitWaiters(1);});
        future.getFirst().get(10,TimeUnit.SECONDS);
    }
    void state(CreditCardBillStatus state) {
        tx.executeWithoutResult(s -> bills.findById(bill.getId()).orElseThrow().setStatus(state));
    }
    CreditCardBill destination(CreditCardBillStatus state) {
        return tx.execute(s -> bills.save(CreditCardBill.builder().creditCard(em.getReference(CreditCard.class,cardId()))
                .status(state).closingDay(10).dueDay(20).periodStart(LocalDate.of(2027,1,10))
                .closingDate(LocalDate.of(2027,2,10)).dueDate(LocalDate.of(2027,2,20)).build()));
    }
    String installmentPath() { return "/api/credit-cards/"+cardId()+"/purchases/"+purchaseId+"/installments/"+installmentId; }
    int move(Long destinationId, String amount) throws Exception {
        return mvc.perform(put(installmentPath()).contextPath("/api").with(user(owner)).contentType("application/json")
                .content("{\"amount\":"+amount+",\"creditCardBillId\":"+destinationId+"}"))
                .andReturn().getResponse().getStatus();
    }
    void unchanged() {
        assertEquals(bill.getId(),jdbc.queryForObject("SELECT credit_card_bill_id FROM credit_card_installments WHERE id=?",Long.class,installmentId));
        assertEquals(0,new BigDecimal("100").compareTo(jdbc.queryForObject("SELECT amount FROM credit_card_installments WHERE id=?",BigDecimal.class,installmentId)));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM credit_card_purchases WHERE id=?",Integer.class,purchaseId));
        balance(source,"100");
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> matrix() {
        return Arrays.stream(CreditCardBillStatus.values()).flatMap(a -> Arrays.stream(CreditCardBillStatus.values())
                .map(b -> org.junit.jupiter.params.provider.Arguments.of(a,b)));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("matrix")
    void movementMatrix(CreditCardBillStatus origin, CreditCardBillStatus targetState) throws Exception {
        state(origin);var targetBill=destination(targetState);
        int result=move(targetBill.getId(),"100.00");
        if(origin==CreditCardBillStatus.OPEN && targetState==CreditCardBillStatus.OPEN) {
            assertEquals(200,result);
            assertEquals(targetBill.getId(),jdbc.queryForObject("SELECT credit_card_bill_id FROM credit_card_installments WHERE id=?",Long.class,installmentId));
            assertEquals(targetBill.getPeriodStart(),jdbc.queryForObject("SELECT installment_date FROM credit_card_installments WHERE id=?",LocalDate.class,installmentId));
            assertEquals(0,new BigDecimal("100").compareTo(jdbc.queryForObject("SELECT total_value FROM credit_card_purchases WHERE id=?",BigDecimal.class,purchaseId)));
        } else {assertEquals(409,result);unchanged();}
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(CreditCardBillStatus.class)
    void sameBillIsStillValidated(CreditCardBillStatus status) throws Exception {
        state(status);assertEquals(status==CreditCardBillStatus.OPEN?200:409,move(bill.getId(),"100"));unchanged();
        assertEquals(LocalDate.now(),jdbc.queryForObject("SELECT installment_date FROM credit_card_installments WHERE id=?",LocalDate.class,installmentId));
    }
    @Test void amountChangeAndIndividualDeletionCannotBreakThePurchase() throws Exception {
        state(CreditCardBillStatus.OPEN);
        assertEquals(409,move(bill.getId(),"80"));
        mvc.perform(delete(installmentPath()).contextPath("/api").with(user(owner))).andExpect(status().isConflict());
        unchanged();
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(CreditCardBillStatus.class)
    void cascadeAndDirectDeletion(CreditCardBillStatus state) throws Exception {
        state(state);
        mvc.perform(delete(installmentPath()).contextPath("/api").with(user(owner))).andExpect(status().isConflict());
        if(state==CreditCardBillStatus.OPEN) {
            mvc.perform(delete("/api/credit-cards/"+cardId()+"/purchases/"+purchaseId).contextPath("/api").with(user(owner))).andExpect(status().isNoContent());
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM credit_card_installments WHERE id=?",Integer.class,installmentId));
            mvc.perform(delete("/api/credit-cards/"+cardId()).contextPath("/api").with(user(owner))).andExpect(status().isNoContent());
        } else {
            mvc.perform(delete("/api/credit-cards/"+cardId()+"/purchases/"+purchaseId).contextPath("/api").with(user(owner))).andExpect(status().isConflict());
            mvc.perform(delete("/api/credit-cards/"+cardId()).contextPath("/api").with(user(owner))).andExpect(status().isConflict());
            assertThrows(IllegalStateException.class,()->userService.delete(owner.getId()));unchanged();
        }
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(CreditCardBillStatus.class)
    void refundAndCreationStateGuards(CreditCardBillStatus state) throws Exception {
        cards.reopenBill(cardId(),bill.getId(),auth());
        var refund=cards.createRefund(cardId(),bill.getId(),new CreditCardRefundCreateDto("Refund",BigDecimal.TEN,LocalDate.now(),received.getId()),auth());
        state(state);
        tx.executeWithoutResult(s -> em.find(CreditCardInstallment.class,installmentId).setInstallmentNumber(3));
        var result=mvc.perform(post(billPath()+"/refunds").contextPath("/api").with(user(owner)).contentType("application/json")
                .content("{\"description\":\"Refund\",\"amount\":10,\"refundDate\":\"2026-10-13\",\"categoryId\":"+received.getId()+"}"));
        result.andExpect(status().is(state==CreditCardBillStatus.OPEN?201:409));
        mvc.perform(delete(billPath()+"/refunds/"+refund.id()).contextPath("/api").with(user(owner)))
                .andExpect(status().is(state==CreditCardBillStatus.OPEN?204:409));
        tx.executeWithoutResult(s -> em.find(CreditCardPurchase.class,purchaseId).setPurchaseDate(bill.getPeriodStart().plusDays(1)));
        mvc.perform(post("/api/credit-cards/"+cardId()+"/purchases/"+purchaseId+"/installments").contextPath("/api").with(user(owner))
                .contentType("application/json").content("{\"installmentNumber\":2,\"amount\":100,\"categoryId\":"+sent.getId()+"}"))
                .andExpect(status().isCreated()); // next period remains eligible; resolver never skips a protected period
        // First period targets the protected bill, not the new future bill.
        mvc.perform(post("/api/credit-cards/"+cardId()+"/purchases/"+purchaseId+"/installments").contextPath("/api").with(user(owner))
                .contentType("application/json").content("{\"installmentNumber\":1,\"amount\":100,\"categoryId\":"+sent.getId()+"}"))
                .andExpect(status().is(state==CreditCardBillStatus.OPEN?201:409));
    }
    @Test void mixedPurchaseBlocksWholeDeletion() throws Exception {
        state(CreditCardBillStatus.OPEN);var protectedBill=destination(CreditCardBillStatus.CLOSED);
        tx.executeWithoutResult(s -> em.persist(CreditCardInstallment.builder().purchase(em.getReference(CreditCardPurchase.class,purchaseId))
                .installmentNumber(2).installmentDate(protectedBill.getPeriodStart()).amount(BigDecimal.TEN)
                .creditCardBill(em.getReference(CreditCardBill.class,protectedBill.getId())).category(sent).build()));
        assertThrows(IllegalStateException.class,()->cards.deletePurchase(cardId(),purchaseId,auth()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM credit_card_installments WHERE purchase_id=?",Integer.class,purchaseId));unchanged();
    }
    @Test void reopenThenMoveThenPayProtectsConfirmedComposition() throws Exception {
        var targetBill=destination(CreditCardBillStatus.OPEN);
        assertEquals(409,move(targetBill.getId(),"100"));cards.reopenBill(cardId(),bill.getId(),auth());
        assertEquals(200,move(targetBill.getId(),"100"));cards.closeBill(cardId(),targetBill.getId(),auth());
        cards.payBill(cardId(),targetBill.getId(),new CreditCardBillPayDto(source.getId()),auth());
        assertThrows(IllegalStateException.class,()->cards.reopenBill(cardId(),targetBill.getId(),auth()));
        assertEquals(409,move(bill.getId(),"100"));balance(source,"0");
    }
    @Test void foreignCardDestinationIsUnavailable() throws Exception {
        state(CreditCardBillStatus.OPEN);
        Long other=tx.execute(s->{var second=CreditCard.builder().user(owner).legalEntity(((BankAccount)source).getLegalEntity())
                .cardBrand(brands.findAll().getFirst()).lastFourDigits("9999").creditLimit(BigDecimal.TEN).closingDay(10).dueDay(20).build();em.persist(second);
            return bills.save(CreditCardBill.builder().creditCard(second).status(CreditCardBillStatus.OPEN).closingDay(10).dueDay(20)
                    .periodStart(LocalDate.of(2027,1,10)).closingDate(LocalDate.of(2027,2,10)).dueDate(LocalDate.of(2027,2,20)).build()).getId();});
        assertEquals(404,move(other,"100"));unchanged();
        var stranger=User.builder().id(-1L).firstName("Other").lastName("User").email("other@example.invalid").password("encoded").roles(owner.getRoles()).enabled(true).build();
        mvc.perform(delete(installmentPath()).contextPath("/api").with(user(stranger))).andExpect(status().isNotFound());unchanged();
    }
    @Test void inventoryReportsMissingAndDivergentPaymentsWithoutMutation() throws Exception {
        String sql=java.nio.file.Files.readString(java.nio.file.Path.of("docs/sql/US-05-inventario-faturas.sql"));
        state(CreditCardBillStatus.PAID);
        assertTrue(jdbc.queryForList(sql).stream().anyMatch(row -> ((Number)row.get("bill_id")).longValue()==bill.getId()
                && "PAID_WITHOUT_PAYMENT".equals(row.get("reason"))));
        state(CreditCardBillStatus.CLOSED);cards.payBill(cardId(),bill.getId(),new CreditCardBillPayDto(source.getId()),auth());
        assertTrue(jdbc.queryForList(sql).stream().noneMatch(row -> ((Number)row.get("bill_id")).longValue()==bill.getId()));
        jdbc.update("UPDATE credit_card_installments SET amount=90 WHERE id=?",installmentId); // deliberate legacy fixture
        assertTrue(jdbc.queryForList(sql).stream().anyMatch(row -> ((Number)row.get("bill_id")).longValue()==bill.getId()
                && "PAYMENT_COMPOSITION_MISMATCH".equals(row.get("reason"))));
        balance(source,"0");
        assertEquals(CreditCardBillStatus.PAID,bills.findById(bill.getId()).orElseThrow().getStatus());
    }
    @Test void openLegacyBillWithPaymentCannotBeModifiedOrDeleted() throws Exception {
        cards.payBill(cardId(),bill.getId(),new CreditCardBillPayDto(source.getId()),auth());state(CreditCardBillStatus.OPEN);
        assertEquals(409,move(bill.getId(),"100"));
        assertThrows(IllegalStateException.class,()->cards.closeBill(cardId(),bill.getId(),auth()));
        state(CreditCardBillStatus.CLOSED);
        assertEquals(409,pay());
        assertThrows(IllegalStateException.class,()->cards.reopenBill(cardId(),bill.getId(),auth()));
        assertThrows(IllegalStateException.class,()->cards.deleteCreditCard(cardId(),auth()));
        assertThrows(IllegalStateException.class,()->userService.delete(owner.getId()));balance(source,"0");
    }
    @Test void openCompositionAllowsWholeCardCascade() {
        state(CreditCardBillStatus.OPEN);cards.deleteCreditCard(cardId(),auth());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM credit_card_installments WHERE id=?",Integer.class,installmentId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM credit_card_purchases WHERE id=?",Integer.class,purchaseId));
    }
    @Test void openCompositionAllowsUserCascade() {
        state(CreditCardBillStatus.OPEN);userService.delete(owner.getId());
        assertFalse(users.existsById(owner.getId()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM credit_card_installments WHERE id=?",Integer.class,installmentId));
    }
    @Test void openApiDescribesLifecycle() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api").with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/credit-cards/{id}/purchases/{purchaseId}/installments/{installmentId}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/credit-cards/{id}/purchases/{purchaseId}'].delete.responses['409']").exists());
    }
    @Test void failedCascadeRollsBackPreviouslyRemovedInstallments() {
        state(CreditCardBillStatus.OPEN);
        String table="us05_block_"+cardId();
        jdbc.execute("CREATE TABLE "+table+" (card_id BIGINT REFERENCES credit_cards(id))");
        try {
            jdbc.update("INSERT INTO "+table+" VALUES (?)",cardId());
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->cards.deleteCreditCard(cardId(),auth()));
            unchanged();assertTrue(bills.existsById(bill.getId()));
        } finally {jdbc.execute("DROP TABLE "+table);}
    }
    void postgresOnly() {Assumptions.assumeTrue(Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)c->"PostgreSQL".equals(c.getMetaData().getDatabaseProductName()))));}
    @Test void closingWinsAndWaitingMoveRejectsWithoutPartialWrites() throws Exception {
        postgresOnly();state(CreditCardBillStatus.OPEN);var targetBill=destination(CreditCardBillStatus.OPEN);
        leader(()->locks.lockCard(cardId()),()->cards.closeBill(cardId(),bill.getId(),auth()),()->{try{assertEquals(409,move(targetBill.getId(),"100"));}catch(Exception e){throw new RuntimeException(e);}});unchanged();
    }
    @Test void paymentWinsAndWaitingDeletionPreservesPurchaseAndDebit() throws Exception {
        postgresOnly();leader(()->locks.lockCard(cardId()),()->cards.payBill(cardId(),bill.getId(),new CreditCardBillPayDto(source.getId()),auth()),
                ()->assertThrows(IllegalStateException.class,()->cards.deletePurchase(cardId(),purchaseId,auth())));
        balance(source,"0");assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM credit_card_installments WHERE id=?",Integer.class,installmentId));
    }
    @Test void closingWinsAndWaitingUserCascadeRejects() throws Exception {
        postgresOnly();state(CreditCardBillStatus.OPEN);
        leader(()->locks.lockCard(cardId()),()->cards.closeBill(cardId(),bill.getId(),auth()),
                ()->assertThrows(IllegalStateException.class,()->userService.delete(owner.getId())));unchanged();
    }
    @Test void movementWinsAndClosingReadsTheNewComposition() throws Exception {
        postgresOnly();state(CreditCardBillStatus.OPEN);var targetBill=destination(CreditCardBillStatus.OPEN);
        leader(()->locks.lockCard(cardId()),()->cards.updateInstallment(cardId(),purchaseId,installmentId,new CreditCardInstallmentUpdateDto(new BigDecimal("100"),targetBill.getId()),auth()),
                ()->cards.closeBill(cardId(),targetBill.getId(),auth()));
        cards.payBill(cardId(),targetBill.getId(),new CreditCardBillPayDto(source.getId()),auth());balance(source,"0");
    }
}
