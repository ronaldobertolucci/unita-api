package io.github.ronaldobertolucci.unita.integration;

import io.github.ronaldobertolucci.unita.dto.card.*;
import io.github.ronaldobertolucci.unita.model.card.*;
import io.github.ronaldobertolucci.unita.model.finance.*;
import io.github.ronaldobertolucci.unita.model.pocket.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.card.CreditCardService;
import io.github.ronaldobertolucci.unita.service.finance.FinancialConcurrencyService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class RefundIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CategoryRepository categories;
    @Autowired LegalEntityRepository entities;
    @Autowired CardBrandRepository brands;
    @Autowired CreditCardBillRepository bills;
    @Autowired CreditCardService cards;
    @Autowired FinancialConcurrencyService locks;
    @Autowired MockMvc mvc;
    @Autowired CreditCardRefundRepository refunds;
    @Autowired TransactionRepository transactions;
    @Autowired io.github.ronaldobertolucci.unita.service.user.UserService userService;
    Category income;
    CreditCardPurchaseDto purchase;
    Long billId;
    TransactionTemplate tx;
    User owner;
    CreditCard card;
    Category category;
    Pocket pocket;
    ExecutorService pool;
    final LocalDate anchor=LocalDate.of(2027,1,31);

    @BeforeEach void setup() {
        tx=new TransactionTemplate(manager); pool=Executors.newSingleThreadExecutor();
        tx.executeWithoutResult(s -> {
            owner=users.save(User.builder().firstName("Refund").lastName("Test").email(UUID.randomUUID()+"@example.invalid")
                    .password("encoded").dateOfBirth(LocalDate.of(1990,1,1)).enabled(true).emailVerified(true)
                    .roles(Set.of(roles.findByName("USER").orElseThrow())).build());
            var legal=entities.save(LegalEntity.builder().user(owner).cnpj(String.format("%014d",owner.getId())).corporateName("Refund bank").build());
            category=categories.save(Category.builder().user(owner).name("Plan expense").type(CategoryType.EXPENSE).system(false).build());
            card=CreditCard.builder().user(owner).legalEntity(legal).cardBrand(brands.findAll().getFirst())
                    .lastFourDigits("1234").creditLimit(new BigDecimal("1000")).closingDay(10).dueDay(20).build();em.persist(card);
            var cash=new Cash();cash.setUser(owner);em.persist(cash);pocket=cash;
            income=categories.save(Category.builder().user(owner).name("Refund income").type(CategoryType.INCOME).system(false).build());
        });
        purchase=cards.createPurchase(card.getId(),automatic("100",1,anchor),auth());
        billId=purchase.installments().getFirst().creditCardBillId();
    }
    @AfterEach void cleanup() throws Exception {
        pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));
        tx.executeWithoutResult(s -> {
            refunds.deleteAllByUserId(owner.getId());
            jdbc.update("DELETE FROM credit_card_installments WHERE purchase_id IN (SELECT p.id FROM credit_card_purchases p JOIN credit_cards c ON c.id=p.credit_card_id WHERE c.user_id=?)",owner.getId());
            jdbc.update("DELETE FROM credit_cards WHERE id=?",card.getId());
            jdbc.update("DELETE FROM users WHERE id=?",owner.getId());
        });
    }
    UsernamePasswordAuthenticationToken auth(){return new UsernamePasswordAuthenticationToken(owner,null,owner.getAuthorities());}
    String path(){return "/api/credit-cards/"+card.getId()+"/purchases";}
    CreditCardPurchaseCreateDto automatic(String total,int count,LocalDate date) {
        return new CreditCardPurchaseCreateDto("Purchase",new BigDecimal(total),date,count,category.getId(),null,null);
    }
    CreditCardPurchaseDto create(){return cards.createPurchase(card.getId(),automatic("100",3,anchor),auth());}
    String json(String total,int count,String extra) {
        return "{\"description\":\"Purchase\",\"totalValue\":"+total+",\"purchaseDate\":\"2027-01-31\",\"installmentsCount\":"+count+",\"categoryId\":"+category.getId()+extra+"}";
    }
    int purchases(){return jdbc.queryForObject("SELECT count(*) FROM credit_card_purchases WHERE credit_card_id=?",Integer.class,card.getId());}
    int billCount(){return jdbc.queryForObject("SELECT count(*) FROM credit_card_bills WHERE credit_card_id=?",Integer.class,card.getId());}
    void empty(){assertEquals(0,purchases());assertEquals(0,billCount());}
    void amount(BigDecimal actual,String expected){assertEquals(0,actual.compareTo(new BigDecimal(expected)));}

    String refundPath(Long id) {return "/api/credit-cards/"+card.getId()+"/bills/"+id+"/refunds";}
    String refundJson(String value, Long reference) {
        return "{\"description\":\"Refund\",\"amount\":"+value+",\"refundDate\":\"2027-03-01\",\"categoryId\":"+income.getId()
                +(reference==null?"":",\"purchaseId\":"+reference)+"}";
    }
    CreditCardRefundDto refund(Long target,String value,Long reference) {
        return cards.createRefund(card.getId(),target,new CreditCardRefundCreateDto("Refund",new BigDecimal(value),anchor,income.getId(),reference),auth());
    }
    void state(Long id,CreditCardBillStatus status) {tx.executeWithoutResult(s->bills.findById(id).orElseThrow().setStatus(status));}
    Long destination() {return cards.createPurchase(card.getId(),automatic("10",1,anchor.plusMonths(1)),auth()).installments().getFirst().creditCardBillId();}
    int count(){return jdbc.queryForObject("SELECT count(*) FROM credit_card_refunds WHERE credit_card_bill_id IN (SELECT id FROM credit_card_bills WHERE credit_card_id=?)",Integer.class,card.getId());}
    BigDecimal balance(){return transactions.calculateBalanceByPocketId(pocket.getId());}
    void original(){amount(cards.findPurchase(card.getId(),purchase.id(),auth()).totalValue(),"100");amount(cards.findPurchase(card.getId(),purchase.id(),auth()).installments().getFirst().amount(),"100");}

    @ParameterizedTest @EnumSource(CreditCardBillStatus.class)
    void createAndDeleteRespectState(CreditCardBillStatus status) throws Exception {
        var r=refund(billId,"20",purchase.id());state(billId,status);
        mvc.perform(post(refundPath(billId)).contextPath("/api").with(user(owner)).contentType("application/json").content(refundJson("10",purchase.id())))
                .andExpect(status().is(status==CreditCardBillStatus.OPEN?201:409));
        mvc.perform(delete(refundPath(billId)+"/"+r.id()).contextPath("/api").with(user(owner)))
                .andExpect(status().is(status==CreditCardBillStatus.OPEN?204:409));
        assertEquals(1,count());amount(cards.findBillById(card.getId(),billId,auth()).totalAmount(),status==CreditCardBillStatus.OPEN?"90":"80");
        amount(balance(),"0");original();
    }
    @Test void refundOfPaidPurchaseCreditsExplicitOtherBillAndPreservesDebit() throws Exception {
        cards.closeBill(card.getId(),billId,auth());cards.payBill(card.getId(),billId,new CreditCardBillPayDto(pocket.getId()),auth());
        Long target=destination();var r=refund(target,"50",purchase.id());
        assertEquals(purchase.id(),r.purchaseId());amount(cards.findBillById(card.getId(),target,auth()).totalAmount(),"-40");
        amount(cards.findBillById(card.getId(),billId,auth()).totalAmount(),"100");amount(balance(),"-100");original();
        mvc.perform(get(refundPath(target)).contextPath("/api").with(user(owner))).andExpect(status().isOk()).andExpect(jsonPath("$[0].purchaseId").value(purchase.id()));
        mvc.perform(get("/api/credit-cards/"+card.getId()+"/bills/"+target+"/installments").contextPath("/api").with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refunds[0].purchaseId").value(purchase.id()));
        mvc.perform(post(refundPath(billId)).contextPath("/api").with(user(owner)).contentType("application/json").content(refundJson("1",purchase.id())))
                .andExpect(status().isConflict());assertEquals(1,count());amount(balance(),"-100");
    }
    @Test void limitSpansAllBillsAndDeletionReleasesIt() throws Exception {
        refund(billId,"60",purchase.id());cards.closeBill(card.getId(),billId,auth());Long target=destination();
        var second=refund(target,"40",purchase.id());
        mvc.perform(post(refundPath(target)).contextPath("/api").with(user(owner)).contentType("application/json").content(refundJson("0.01",purchase.id())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("amount: accumulated refunds must not exceed purchase totalValue"));
        assertEquals(2,count());cards.deleteRefund(card.getId(),target,second.id(),auth());refund(target,"40",purchase.id());original();amount(balance(),"0");
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","0.001","1.001","10000000000000"})
    void invalidMoneyIsRejectedInApiAndDirectService(String value) throws Exception {
        mvc.perform(post(refundPath(billId)).contextPath("/api").with(user(owner)).contentType("application/json").content(refundJson(value,null)))
                .andExpect(status().isBadRequest());
        assertThrows(IllegalArgumentException.class,()->refund(billId,value,null));assertEquals(0,count());original();
    }
    @Test void maximumUnlinkedCreditAndNeutralCategory() {
        var r=refund(billId,"9999999999999.99",null);assertNull(r.purchaseId());amount(r.amount(),"9999999999999.99");
        var neutral=tx.execute(s->categories.save(Category.builder().user(owner).name("Neutral").type(CategoryType.NEUTRAL).system(false).build()));
        cards.createRefund(card.getId(),billId,new CreditCardRefundCreateDto("Neutral",BigDecimal.ONE,anchor,neutral.getId()),auth());assertEquals(2,count());amount(balance(),"0");
    }
    @Test void validatesDescriptionDateIdsAndCategoryWithoutWriting() throws Exception {
        for(var dto:List.of(new CreditCardRefundCreateDto(" ",BigDecimal.ONE,anchor,income.getId()),
                new CreditCardRefundCreateDto("x".repeat(256),BigDecimal.ONE,anchor,income.getId()),
                new CreditCardRefundCreateDto("Refund",null,anchor,income.getId()),
                new CreditCardRefundCreateDto("Refund",BigDecimal.ONE,null,income.getId()),
                new CreditCardRefundCreateDto("Refund",BigDecimal.ONE,anchor,null),
                new CreditCardRefundCreateDto("Refund",BigDecimal.ONE,anchor,0L),
                new CreditCardRefundCreateDto("Refund",BigDecimal.ONE,anchor,income.getId(),0L)))
            assertThrows(IllegalArgumentException.class,()->cards.createRefund(card.getId(),billId,dto,auth()));
        assertThrows(IllegalArgumentException.class,()->cards.createRefund(card.getId(),billId,null,auth()));
        mvc.perform(post(refundPath(billId)).contextPath("/api").with(user(owner)).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        mvc.perform(post(refundPath(billId)).contextPath("/api").with(user(owner)).contentType("application/json")
                .content(refundJson("1",null).replace("\"categoryId\":"+income.getId(),"\"categoryId\":"+category.getId())))
                .andExpect(status().isBadRequest());assertEquals(0,count());
    }
    @Test void unavailablePurchaseBillCategoryCardAndRefundRevealNothing() throws Exception {
        var other=tx.execute(s->{var c=CreditCard.builder().user(owner).legalEntity(card.getLegalEntity()).cardBrand(card.getCardBrand())
                .lastFourDigits("9999").creditLimit(BigDecimal.TEN).closingDay(10).dueDay(20).build();em.persist(c);return c;});
        var otherPurchase=cards.createPurchase(other.getId(),automatic("50",1,anchor),auth());
        for(Long reference:List.of(otherPurchase.id(),Long.MAX_VALUE))
            mvc.perform(post(refundPath(billId)).contextPath("/api").with(user(owner)).contentType("application/json").content(refundJson("1",reference)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Purchase not found"));
        mvc.perform(post(refundPath(otherPurchase.installments().getFirst().creditCardBillId())).contextPath("/api").with(user(owner)).contentType("application/json").content(refundJson("1",null)))
                .andExpect(status().isNotFound());
        var stranger=User.builder().id(-1L).roles(owner.getRoles()).enabled(true).emailVerified(true).build();
        mvc.perform(post(refundPath(billId)).contextPath("/api").with(user(stranger)).contentType("application/json").content(refundJson("1",purchase.id())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Credit card not found"));
        mvc.perform(post(refundPath(billId)).contextPath("/api").with(user(owner)).contentType("application/json")
                .content(refundJson("1",null).replace("\"categoryId\":"+income.getId(),"\"categoryId\":9223372036854775807")))
                .andExpect(status().isNotFound());
        mvc.perform(delete(refundPath(billId)+"/9223372036854775807").contextPath("/api").with(user(owner))).andExpect(status().isNotFound());
        assertEquals(0,count());original();
        tx.executeWithoutResult(s->{jdbc.update("DELETE FROM credit_card_installments WHERE purchase_id=?",otherPurchase.id());jdbc.update("DELETE FROM credit_cards WHERE id=?",other.getId());});
    }
    @ParameterizedTest @CsvSource({"20,80,200","100,0,409","120,-20,409"})
    void positiveZeroAndCreditorTotalsHaveDefinedPaymentBehavior(String credit,String total,int result) throws Exception {
        refund(billId,credit,null);cards.closeBill(card.getId(),billId,auth());
        mvc.perform(put("/api/credit-cards/"+card.getId()+"/bills/"+billId+"/pay").contextPath("/api").with(user(owner)).contentType("application/json").content("{\"pocketId\":"+pocket.getId()+"}"))
                .andExpect(status().is(result));amount(cards.findBillById(card.getId(),billId,auth()).totalAmount(),total);
        if(result==200){amount(balance(),"-80");assertEquals(CreditCardBillStatus.PAID,bills.findById(billId).orElseThrow().getStatus());}
        else {amount(balance(),"0");assertNull(bills.findById(billId).orElseThrow().getPaymentTransaction());assertEquals(CreditCardBillStatus.CLOSED,bills.findById(billId).orElseThrow().getStatus());
            cards.reopenBill(card.getId(),billId,auth());assertEquals(CreditCardBillStatus.OPEN,bills.findById(billId).orElseThrow().getStatus());}
        original();
    }
    @Test void referencedPurchaseCannotBeEditedOrDeletedUntilRefundIsRemoved() throws Exception {
        var r=refund(billId,"20",purchase.id());
        mvc.perform(put(path()+"/"+purchase.id()).contextPath("/api").with(user(owner)).contentType("application/json").content(json("90",1,"")))
                .andExpect(status().isConflict());
        mvc.perform(delete(path()+"/"+purchase.id()).contextPath("/api").with(user(owner))).andExpect(status().isConflict());original();
        cards.deleteRefund(card.getId(),billId,r.id(),auth());cards.updatePurchase(card.getId(),purchase.id(),automatic("90",1,anchor),auth());
        cards.deletePurchase(card.getId(),purchase.id(),auth());assertEquals(0,purchases());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void eligibleCardAndUserCascadeCanRemoveLinkedRefunds(boolean wholeUser) {
        refund(billId,"20",purchase.id());if(wholeUser)userService.delete(owner.getId());else cards.deleteCreditCard(card.getId(),auth());assertEquals(0,count());
    }
    @Test void failedRefundInsertRollsBackWithoutCreditOrDebit() {
        String constraint="us06_failure_"+card.getId();jdbc.execute("ALTER TABLE credit_card_refunds ADD CONSTRAINT "+constraint+" CHECK (description <> 'Failure')");
        try {assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->cards.createRefund(card.getId(),billId,
                new CreditCardRefundCreateDto("Failure",BigDecimal.ONE,anchor,income.getId(),purchase.id()),auth()));
            assertEquals(0,count());amount(cards.findBillById(card.getId(),billId,auth()).totalAmount(),"100");amount(balance(),"0");original();}
        finally {jdbc.execute("ALTER TABLE credit_card_refunds DROP CONSTRAINT "+constraint);}
    }
    @Test void openWithPaymentIsProtectedEvenInLegacyState() {
        cards.closeBill(card.getId(),billId,auth());cards.payBill(card.getId(),billId,new CreditCardBillPayDto(pocket.getId()),auth());state(billId,CreditCardBillStatus.OPEN);
        assertThrows(IllegalStateException.class,()->refund(billId,"10",purchase.id()));amount(balance(),"-100");assertEquals(0,count());
    }
    void postgresOnly(){Assumptions.assumeTrue(Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)c->"PostgreSQL".equals(c.getMetaData().getDatabaseProductName()))));}
    void leader(Runnable first,Runnable waiting) throws Exception {
        List<Future<?>> futures=new ArrayList<>();
        tx.executeWithoutResult(s->{locks.lockCard(card.getId());first.run();futures.add(pool.submit(waiting));
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",Integer.class)<1 && System.nanoTime()<until){try{Thread.sleep(10);}catch(InterruptedException e){throw new RuntimeException(e);}}
            assertTrue(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",Integer.class)>=1,"Writer must actually wait on PostgreSQL lock");});
        futures.getFirst().get(10,TimeUnit.SECONDS);
    }
    @Test void paymentWinsAgainstRefundInsertion() throws Exception {
        postgresOnly();cards.closeBill(card.getId(),billId,auth());
        leader(()->cards.payBill(card.getId(),billId,new CreditCardBillPayDto(pocket.getId()),auth()),()->assertThrows(IllegalStateException.class,()->refund(billId,"20",purchase.id())));
        assertEquals(0,count());amount(balance(),"-100");original();
    }
    @Test void paymentWinsAgainstRefundDeletion() throws Exception {
        postgresOnly();var r=refund(billId,"20",purchase.id());cards.closeBill(card.getId(),billId,auth());
        leader(()->cards.payBill(card.getId(),billId,new CreditCardBillPayDto(pocket.getId()),auth()),()->assertThrows(IllegalStateException.class,()->cards.deleteRefund(card.getId(),billId,r.id(),auth())));
        assertEquals(1,count());amount(balance(),"-80");amount(cards.findBillById(card.getId(),billId,auth()).totalAmount(),"80");
    }
    @Test void refundWinsBeforeClosingAndPaymentUsesNetTotal() throws Exception {
        postgresOnly();leader(()->refund(billId,"20",purchase.id()),()->cards.closeBill(card.getId(),billId,auth()));
        cards.payBill(card.getId(),billId,new CreditCardBillPayDto(pocket.getId()),auth());amount(balance(),"-80");assertEquals(1,count());
    }
    @Test void deletionWinsBeforeClosingAndPaymentUsesUpdatedTotal() throws Exception {
        postgresOnly();var r=refund(billId,"20",purchase.id());leader(()->cards.deleteRefund(card.getId(),billId,r.id(),auth()),()->cards.closeBill(card.getId(),billId,auth()));
        cards.payBill(card.getId(),billId,new CreditCardBillPayDto(pocket.getId()),auth());amount(balance(),"-100");assertEquals(0,count());
    }
    @Test void simultaneousRefundsCannotExceedOriginalPurchaseTotal() throws Exception {
        postgresOnly();Long target=destination();leader(()->refund(billId,"60",purchase.id()),()->assertThrows(IllegalArgumentException.class,()->refund(target,"60",purchase.id())));
        assertEquals(1,count());amount(refunds.sumAmountByPurchaseId(purchase.id()),"60");amount(balance(),"0");
    }
    @Test void repeatedPostIsNotDeduplicated() throws Exception {
        for(int n=0;n<2;n++) mvc.perform(post(refundPath(billId)).contextPath("/api").with(user(owner))
                .contentType("application/json").content(refundJson("20",purchase.id()))).andExpect(status().isCreated());
        assertEquals(2,count());amount(refunds.sumAmountByPurchaseId(purchase.id()),"40");
    }
    @Test void failedCardCascadeRestoresRemovedRefundsAndInstallments() {
        var r=refund(billId,"20",purchase.id());String table="us06_block_"+card.getId();
        jdbc.execute("CREATE TABLE "+table+" (card_id BIGINT REFERENCES credit_cards(id))");
        try {jdbc.update("INSERT INTO "+table+" VALUES (?)",card.getId());
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->cards.deleteCreditCard(card.getId(),auth()));
            assertTrue(refunds.existsById(r.id()));assertEquals(1,count());original();amount(balance(),"0");}
        finally {jdbc.execute("DROP TABLE "+table);}
    }
    @Test void paidBillRejectsApiRefundDeletionAndKeepsOriginalPayment() throws Exception {
        var r=refund(billId,"20",purchase.id());cards.closeBill(card.getId(),billId,auth());cards.payBill(card.getId(),billId,new CreditCardBillPayDto(pocket.getId()),auth());
        Long payment=jdbc.queryForObject("SELECT payment_transaction_id FROM credit_card_bills WHERE id=?",Long.class,billId);
        mvc.perform(delete(refundPath(billId)+"/"+r.id()).contextPath("/api").with(user(owner))).andExpect(status().isConflict());
        amount(cards.findBillById(card.getId(),billId,auth()).totalAmount(),"80");amount(balance(),"-80");assertEquals(1,count());
        assertEquals(payment,jdbc.queryForObject("SELECT payment_transaction_id FROM credit_card_bills WHERE id=?",Long.class,billId));original();
    }
    @Test void anotherOwnersPurchaseAndCategoryRemainUnavailable() throws Exception {
        User other=tx.execute(s->users.save(User.builder().firstName("Other").lastName("Owner").email(UUID.randomUUID()+"@example.invalid")
                .password("encoded").dateOfBirth(LocalDate.of(1990,1,1)).enabled(true).emailVerified(true).roles(owner.getRoles()).build()));
        try {
            var otherCategory=tx.execute(s->categories.save(Category.builder().user(other).name("Private").type(CategoryType.INCOME).system(false).build()));
            var otherCard=tx.execute(s->{var legal=entities.save(LegalEntity.builder().user(other).cnpj(String.format("%014d",other.getId())).corporateName("Private bank").build());
                var c=CreditCard.builder().user(other).legalEntity(legal).cardBrand(card.getCardBrand()).lastFourDigits("5678").creditLimit(BigDecimal.TEN).closingDay(10).dueDay(20).build();em.persist(c);return c;});
            Long otherPurchase=tx.execute(s->{var p=CreditCardPurchase.builder().creditCard(otherCard).description("Private purchase").totalValue(BigDecimal.TEN).purchaseDate(anchor).installmentsCount(1).build();em.persist(p);return p.getId();});
            mvc.perform(post(refundPath(billId)).contextPath("/api").with(user(owner)).contentType("application/json").content(refundJson("1",otherPurchase)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Purchase not found"));
            mvc.perform(post(refundPath(billId)).contextPath("/api").with(user(owner)).contentType("application/json")
                    .content(refundJson("1",null).replace("\"categoryId\":"+income.getId(),"\"categoryId\":"+otherCategory.getId())))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Category not found"));
            assertEquals(0,count());original();
        } finally {tx.executeWithoutResult(s->jdbc.update("DELETE FROM users WHERE id=?",other.getId()));}
    }
    @Test void openApiContainsRealRefundContract() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api").with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CreditCardRefundCreateDto.properties.purchaseId").exists())
                .andExpect(jsonPath("$.components.schemas.CreditCardRefundDto.properties.purchaseId").exists())
                .andExpect(jsonPath("$.paths['/credit-cards/{id}/bills/{billId}/refunds'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/credit-cards/{id}/bills/{billId}/refunds'].post.responses['400']").exists());
    }
}
