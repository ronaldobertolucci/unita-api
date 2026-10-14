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

/** PostgreSQL only: real row-lock waits, independent transactions and bounded barriers. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class FinancialConcurrencyIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PocketRepository pockets;
    @Autowired TransactionRepository transactions;
    @Autowired TransferOperationRepository operations;
    @Autowired CategoryRepository categories;
    @Autowired LegalEntityRepository entities;
    @Autowired BankAccountTypeRepository bankTypes;
    @Autowired CardBrandRepository brands;
    @Autowired CreditCardBillRepository bills;
    @Autowired TransferService transfers;
    @Autowired PocketService pocketService;
    @Autowired CreditCardService cards;
    @Autowired CreditCardBillClosingJob closingJob;
    @Autowired FinancialConcurrencyService locks;
    @Autowired MockMvc mvc;
    TransactionTemplate tx;
    ExecutorService pool;
    User owner;
    Pocket source, target;
    Category sent, received;
    CreditCardBill bill;
    Long purchaseId, installmentId;

    @BeforeEach void setup() {
        Assumptions.assumeTrue(Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) c -> "PostgreSQL".equals(c.getMetaData().getDatabaseProductName()))));
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
    int transfer(Pocket from,Pocket to,boolean fgts){try{return mvc.perform(post(fgts?"/api/transfers/fgts/withdrawal":"/api/transfers").contextPath("/api").with(user(owner))
            .contentType("application/json").content("{\"sourcePocketId\":"+from.getId()+",\"targetPocketId\":"+to.getId()+",\"amount\":80.00,\"description\":\"Concurrent\"}"))
            .andReturn().getResponse().getStatus();}catch(Exception e){throw new RuntimeException(e);}}
    void balance(Pocket pocket,String amount){assertEquals(0,new BigDecimal(amount).compareTo(transactions.calculateBalanceByPocketId(pocket.getId())));}
    int waiting(){return jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",Integer.class);}
    void awaitWaiters(int count){
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(waiting()<count && System.nanoTime()<deadline){try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}
        assertTrue(waiting()>=count,"Both writers must overlap while waiting on PostgreSQL locks");
    }
    List<Integer> race(Runnable blocker,Supplier<Integer> first,Supplier<Integer> second) throws Exception {
        List<Future<Integer>> futures=new ArrayList<>();
        tx.executeWithoutResult(status->{
            blocker.run();var barrier=new CyclicBarrier(2);
            for(var action:List.of(first,second)) futures.add(pool.submit(()->{barrier.await(3,TimeUnit.SECONDS);return action.get();}));
            awaitWaiters(2);
        });
        return List.of(futures.get(0).get(10,TimeUnit.SECONDS),futures.get(1).get(10,TimeUnit.SECONDS));
    }
    void leader(Runnable blocker,Runnable action,Runnable follower) throws Exception {
        List<Future<?>> future=new ArrayList<>();
        tx.executeWithoutResult(status->{blocker.run();action.run();future.add(pool.submit(follower));awaitWaiters(1);});
        future.getFirst().get(10,TimeUnit.SECONDS);
    }
    @Test void simultaneousPaymentsCreateExactlyOneDebit() throws Exception {
        assertEquals(List.of(200,409),race(()->locks.lockCard(cardId()),this::pay,this::pay).stream().sorted().toList());
        balance(source,"0");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM transactions WHERE pocket_id=? AND direction='EXPENSE'",Integer.class,source.getId()));
        tx.executeWithoutResult(status->{var saved=bills.findById(bill.getId()).orElseThrow();assertEquals(CreditCardBillStatus.PAID,saved.getStatus());assertNotNull(saved.getPaymentTransaction());});
    }
    @Test void sameSourceTransfersCannotSpendTheSameBalance() throws Exception {
        assertEquals(List.of(201,400),race(()->locks.lockPockets(source.getId()),()->transfer(source,target,false),()->transfer(source,target,false))
                .stream().sorted().toList());
        balance(source,"20");balance(target,"180");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM transfer_operations o JOIN transactions t ON t.id=o.source_transaction_id WHERE t.pocket_id=?",Integer.class,source.getId()));
    }
    @Test void fgtsWithdrawalsAlsoSerialize() throws Exception {
        source=tx.execute(status->{var employer=IndividualEmployer.builder().user(owner).cpf(String.format("%011d",owner.getId())).name("Employer").build();em.persist(employer);
            var pocket=pockets.save(FgtsEmployerAccount.builder().user(owner).employer(employer).admissionDate(LocalDate.now()).status(FgtsEmployerAccountStatus.ACTIVE).build());opening(pocket);return pocket;});
        assertEquals(Set.of(201,400),new HashSet<>(race(()->locks.lockPockets(source.getId()),()->transfer(source,target,true),()->transfer(source,target,true))));
        balance(source,"20");balance(target,"180");
    }
    @Test void crossedTransfersUseStableOrderAndCommitWholePairs() throws Exception {
        assertEquals(List.of(201,201),race(()->locks.lockPockets(source.getId(),target.getId()),()->transfer(source,target,false),()->transfer(target,source,false)));
        balance(source,"100");balance(target,"100");
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM transfer_operations o JOIN transactions t ON t.id=o.source_transaction_id WHERE t.pocket_id IN (?,?)",Integer.class,source.getId(),target.getId()));
    }
    @Test void paymentDebitIsVisibleToWaitingTransfer() throws Exception {
        leader(()->locks.lockCard(cardId()),()->cards.payBill(cardId(),bill.getId(),new CreditCardBillPayDto(source.getId()),auth()),
                ()->assertEquals(400,transfer(source,target,false)));
        balance(source,"0");balance(target,"100");
    }
    @Test void manualDeletionIsVisibleBeforeBalanceValidation() throws Exception {
        Long openingId=jdbc.queryForObject("SELECT id FROM transactions WHERE pocket_id=?",Long.class,source.getId());
        leader(()->locks.lockPockets(source.getId()),()->pocketService.deleteTransaction(source.getId(),openingId,auth()),
                ()->assertEquals(400,transfer(source,target,false)));
        balance(source,"0");balance(target,"100");
    }
    @Test void manualDebitIsVisibleBeforeBalanceValidation() throws Exception {
        leader(()->locks.lockPockets(source.getId()),()->pocketService.createTransaction(source.getId(),
                new TransactionCreateDto(new BigDecimal("30"),Direction.EXPENSE,LocalDate.now(),"Manual",sent.getId()),auth()),
                ()->assertEquals(400,transfer(source,target,false)));
        balance(source,"70");
    }
    @Test void reopenWinningMakesWaitingPaymentConflict() throws Exception {
        leader(()->locks.lockCard(cardId()),()->cards.reopenBill(cardId(),bill.getId(),auth()),()->assertEquals(409,pay()));
        balance(source,"100");
    }
    @Test void paymentWinningMakesWaitingReopenConflict() throws Exception {
        leader(()->locks.lockCard(cardId()),()->cards.payBill(cardId(),bill.getId(),new CreditCardBillPayDto(source.getId()),auth()),
                ()->assertThrows(IllegalStateException.class,()->cards.reopenBill(cardId(),bill.getId(),auth())));
        balance(source,"0");
    }
    @Test void editingBeforePaymentUsesCommittedComposition() throws Exception {
        leader(()->locks.lockCard(cardId()),()->cards.updateInstallment(cardId(),purchaseId,installmentId,
                new CreditCardInstallmentUpdateDto(new BigDecimal("80"),bill.getId()),auth()),()->assertEquals(200,pay()));
        balance(source,"20");
    }
    @Test void closingJobSharesTheCardProtocol() throws Exception {
        leader(()->locks.lockCard(cardId()),()->cards.reopenBill(cardId(),bill.getId(),auth()),closingJob::execute);
        assertEquals(CreditCardBillStatus.CLOSED,bills.findById(bill.getId()).orElseThrow().getStatus());
        assertEquals(200,pay());balance(source,"0");
    }
    @Test void failedBillUpdateRollsBackTheDebit() {
        String constraint="us03_fail_paid_"+bill.getId();
        jdbc.execute("ALTER TABLE credit_card_bills ADD CONSTRAINT "+constraint+" CHECK (id <> "+bill.getId()+" OR status <> 'PAID')");
        try{assertEquals(500,pay());balance(source,"100");
            assertEquals(CreditCardBillStatus.CLOSED,bills.findById(bill.getId()).orElseThrow().getStatus());
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM transactions WHERE pocket_id=? AND direction='EXPENSE'",Integer.class,source.getId()));
        }finally{jdbc.execute("ALTER TABLE credit_card_bills DROP CONSTRAINT "+constraint);}
    }
    @Test void lockTimeoutIs503AndCreatesNoDebit() throws Exception {
        tx.executeWithoutResult(status->{locks.lockCard(cardId());var future=pool.submit(this::pay);
            try{awaitWaiters(1);assertEquals(503,future.get(8,TimeUnit.SECONDS));}catch(Exception e){throw new RuntimeException(e);}});
        balance(source,"100");assertEquals(CreditCardBillStatus.CLOSED,bills.findById(bill.getId()).orElseThrow().getStatus());
    }
    @Test void openApiDescribesDuplicateAndTimeoutResponses() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/credit-cards/{id}/bills/{billId}/pay'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/credit-cards/{id}/bills/{billId}/pay'].put.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/transfers'].post.responses['503']").exists());
    }

    @Test void zeroTotalCannotCreateExpense() { rejectNonPositiveTotal("100"); }
    @Test void creditTotalCannotCreateNegativeExpense() { rejectNonPositiveTotal("120"); }
    void rejectNonPositiveTotal(String refundAmount) {
        cards.createRefund(cardId(), bill.getId(), new CreditCardRefundCreateDto("Refund", new BigDecimal(refundAmount), LocalDate.now(), received.getId()), auth());
        assertEquals(409, pay());
        balance(source, "100");
        tx.executeWithoutResult(status -> {
            var saved = bills.findById(bill.getId()).orElseThrow();
            assertEquals(CreditCardBillStatus.CLOSED, saved.getStatus());
            assertNull(saved.getPaymentTransaction());
        });
    }
    @Test void explicitClosingPrecedesWaitingPayment() throws Exception {
        cards.reopenBill(cardId(),bill.getId(),auth());
        leader(()->locks.lockCard(cardId()),()->cards.closeBill(cardId(),bill.getId(),auth()),()->assertEquals(200,pay()));
        balance(source,"0");
    }

    @Test void bulkClosingNeverTouchesCardsOutsideTheLockedSnapshot() throws Exception {
        tx.executeWithoutResult(status -> {
            var locked = locks.lockAllCards();
            var future = pool.submit(() -> tx.execute(inner -> {
                var card = CreditCard.builder().user(owner).legalEntity(((BankAccount)source).getLegalEntity())
                        .cardBrand(brands.findAll().getFirst()).lastFourDigits("5678")
                        .creditLimit(new BigDecimal("500")).closingDay(10).dueDay(20).build();
                em.persist(card);
                return bills.save(CreditCardBill.builder().creditCard(card).status(CreditCardBillStatus.OPEN)
                        .closingDay(10).dueDay(20).closingDate(LocalDate.now().minusDays(1))
                        .dueDate(LocalDate.now()).periodStart(LocalDate.now().minusMonths(1)).build()).getId();
            }));
            try {
                Long newBill = future.get(3, TimeUnit.SECONDS);
                bills.closeOverdueForLockedCards(LocalDate.now(),CreditCardBillStatus.OPEN,CreditCardBillStatus.CLOSED,locked);
                assertEquals(CreditCardBillStatus.OPEN,bills.findById(newBill).orElseThrow().getStatus());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

}
