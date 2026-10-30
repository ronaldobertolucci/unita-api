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
class PurchasePlanIntegrationTest {
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
            owner=users.save(User.builder().firstName("Plan").lastName("Test").email(UUID.randomUUID()+"@example.invalid")
                    .password("encoded").dateOfBirth(LocalDate.of(1990,1,1)).enabled(true).emailVerified(true)
                    .roles(Set.of(roles.findByName("USER").orElseThrow())).build());
            var legal=entities.save(LegalEntity.builder().user(owner).cnpj(String.format("%014d",owner.getId())).corporateName("Plan bank").build());
            category=categories.save(Category.builder().user(owner).name("Plan expense").type(CategoryType.EXPENSE).system(false).build());
            card=CreditCard.builder().user(owner).legalEntity(legal).cardBrand(brands.findAll().getFirst())
                    .lastFourDigits("1234").creditLimit(new BigDecimal("1000")).closingDay(10).dueDay(20).build();em.persist(card);
            var cash=new Cash();cash.setUser(owner);em.persist(cash);pocket=cash;
        });
    }
    @AfterEach void cleanup() throws Exception {
        pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));
        tx.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM credit_card_installments WHERE purchase_id IN (SELECT id FROM credit_card_purchases WHERE credit_card_id=?)",card.getId());
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

    @Test void automaticCreatesCompleteOfficialResponseAndListDetail() throws Exception {
        mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json").content(json("100",3,"")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.installments.length()").value(3))
                .andExpect(jsonPath("$.installments[0].amount").value(33.33))
                .andExpect(jsonPath("$.installments[2].amount").value(33.34))
                .andExpect(jsonPath("$.installments[1].installmentDate").value("2027-02-28"))
                .andExpect(jsonPath("$.installments[2].installmentDate").value("2027-03-31"))
                .andExpect(jsonPath("$.installments[0].creditCardBillId").isNumber());
        var list=cards.findPurchases(card.getId(),null,null,auth());assertEquals(1,list.size());assertEquals(3,list.getFirst().installments().size());
        mvc.perform(get(path()+"/"+list.getFirst().id()).contextPath("/api").with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.installments.length()").value(3));
        amount(list.getFirst().installments().stream().map(CreditCardInstallmentDto::amount).reduce(BigDecimal.ZERO,BigDecimal::add),"100");
    }
    @ParameterizedTest @ValueSource(ints={28,29,30,31})
    void monthlyDatesStayAnchored(int day){
        var p=cards.createPurchase(card.getId(),automatic("100",4,LocalDate.of(2027,1,day)),auth());
        assertEquals(LocalDate.of(2027,2,28),p.installments().get(1).installmentDate());
        assertEquals(LocalDate.of(2027,3,day),p.installments().get(2).installmentDate());
    }
    @Test void leapYearAndCashAndMaximumPlan(){
        var p=cards.createPurchase(card.getId(),automatic("1",1,LocalDate.of(2028,2,29)),auth());
        assertEquals(1,p.installments().size());amount(p.installments().getFirst().amount(),"1");
        var max=cards.createPurchase(card.getId(),automatic("1.20",120,anchor),auth());
        assertEquals(120,max.installments().size());max.installments().forEach(i -> amount(i.amount(),"0.01"));
    }
    @Test void customUnorderedPlanIsSortedAndUsesCategoryOverrides() throws Exception {
        var neutral=tx.execute(s -> categories.save(Category.builder().user(owner).name("Neutral").type(CategoryType.NEUTRAL).system(false).build()));
        mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json")
                .content(json("100",3,",\"installmentMode\":\"CUSTOM\",\"installments\":[{\"installmentNumber\":3,\"amount\":50},{\"installmentNumber\":1,\"amount\":20,\"categoryId\":"+neutral.getId()+"},{\"installmentNumber\":2,\"amount\":30}]")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.installments[0].amount").value(20))
                .andExpect(jsonPath("$.installments[0].category.id").value(neutral.getId()))
                .andExpect(jsonPath("$.installments[1].category.id").value(category.getId()));
    }
    @ParameterizedTest @CsvSource({"0,1","-1,1","1.001,1","10000000000000,1","1,0","1,121","0.01,2"})
    void invalidMoneyAndCountLeaveNoRecords(String total,int count) throws Exception {
        mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json").content(json(total,count,"")))
                .andExpect(status().isBadRequest());empty();
    }
    @ParameterizedTest @ValueSource(strings={
        "[]",
        "[{\"installmentNumber\":1,\"amount\":100}]",
        "[{\"installmentNumber\":1,\"amount\":50},{\"installmentNumber\":1,\"amount\":50}]",
        "[{\"installmentNumber\":1,\"amount\":50},{\"installmentNumber\":3,\"amount\":50}]",
        "[{\"installmentNumber\":1,\"amount\":50},{\"installmentNumber\":2,\"amount\":49.99}]",
        "[{\"installmentNumber\":1,\"amount\":100},{\"installmentNumber\":2,\"amount\":0}]",
        "[{\"installmentNumber\":1,\"amount\":50.001},{\"installmentNumber\":2,\"amount\":49.999}]",
        "[null,null]"
    })
    void invalidCustomLeavesNoPartialRecords(String entries) throws Exception {
        mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json")
                .content(json("100",2,",\"installmentMode\":\"CUSTOM\",\"installments\":"+entries)))
                .andExpect(status().isBadRequest());empty();
    }
    @Test void incompatibleModesAndOldContractAndMissingCustomPlan() throws Exception {
        for(String extra:List.of(",\"installments\":[]",",\"installmentMode\":\"CUSTOM\"")){
            mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json").content(json("100",1,extra)))
                    .andExpect(status().isBadRequest());empty();
        }
        mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json")
                .content("{\"description\":\"Old\",\"totalValue\":100,\"purchaseDate\":\"2027-01-31\",\"installmentsCount\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("categoryId"));empty();
        assertThrows(IllegalArgumentException.class,()->cards.createPurchase(card.getId(),automatic("100.001",1,anchor),auth()));empty();
    }
    @Test void malformedJsonAndUnknownModeReturn400AndAnonymousCannotWrite() throws Exception {
        for (String body : List.of(json("100",1,",\"installmentMode\":\"OTHER\""), "{", json("100",1,"").replace("2027-01-31","invalid"))) {
            mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());empty();
        }
        mvc.perform(post(path()).contextPath("/api").contentType("application/json").content(json("100",1,"")))
                .andExpect(status().isUnauthorized());empty();
    }
    @Test void maximumMoneyFitsBankWithoutRoundingAndGlobalCategoryIsAccepted() {
        var global=categories.findSystemByName("Transferência Enviada").orElseThrow();
        var p=cards.createPurchase(card.getId(),new CreditCardPurchaseCreateDto("Max",new BigDecimal("9999999999999.99"),anchor,1,global.getId(),null,null),auth());
        amount(p.totalValue(),"9999999999999.99");amount(p.installments().getFirst().amount(),"9999999999999.99");
        assertEquals(p,cards.findPurchase(card.getId(),p.id(),auth()));
    }
    @Test void exactClosingDateGoesToNextPeriod(){
        var p=cards.createPurchase(card.getId(),automatic("100",2,LocalDate.of(2027,1,9)),auth());
        var next=cards.createPurchase(card.getId(),automatic("100",1,LocalDate.of(2027,1,10)),auth());
        assertNotEquals(p.installments().getFirst().creditCardBillId(),next.installments().getFirst().creditCardBillId());
        assertEquals(p.installments().get(1).creditCardBillId(),next.installments().getFirst().creditCardBillId());
    }
    @Test void fullCorrectionReplacesSetAndInvalidCorrectionPreservesOldIds() throws Exception {
        var old=create();
        mvc.perform(put(path()+"/"+old.id()).contextPath("/api").with(user(owner)).contentType("application/json").content(json("120",2,"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.installments.length()").value(2)).andExpect(jsonPath("$.totalValue").value(120));
        var corrected=cards.findPurchase(card.getId(),old.id(),auth());amount(corrected.installments().getFirst().amount(),"60");
        assertNotEquals(old.installments().getFirst().id(),corrected.installments().getFirst().id());
        mvc.perform(put(path()+"/"+old.id()).contextPath("/api").with(user(owner)).contentType("application/json").content(json("120.001",2,"")))
                .andExpect(status().isBadRequest());assertEquals(corrected,cards.findPurchase(card.getId(),old.id(),auth()));
    }
    @ParameterizedTest @EnumSource(value=CreditCardBillStatus.class,names={"CLOSED","PAID"})
    void protectedAnyBillRejectsCreationAndCorrection(CreditCardBillStatus state) throws Exception {
        var p=create();var id=p.installments().get(1).creditCardBillId();
        tx.executeWithoutResult(s -> bills.findById(id).orElseThrow().setStatus(state));
        int count=billCount();
        mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json").content(json("100",3,"")))
                .andExpect(status().isConflict());assertEquals(1,purchases());assertEquals(count,billCount());
        mvc.perform(put(path()+"/"+p.id()).contextPath("/api").with(user(owner)).contentType("application/json").content(json("200",1,"")))
                .andExpect(status().isConflict());assertEquals(p,cards.findPurchase(card.getId(),p.id(),auth()));
    }
    @Test void protectedDestinationPreservesOpenOriginalPlan(){
        var original=cards.createPurchase(card.getId(),automatic("100",1,anchor),auth());
        var other=cards.createPurchase(card.getId(),automatic("100",1,anchor.plusMonths(2)),auth());
        cards.closeBill(card.getId(),other.installments().getFirst().creditCardBillId(),auth());
        assertThrows(IllegalStateException.class,()->cards.updatePurchase(card.getId(),original.id(),automatic("100",3,anchor),auth()));
        assertEquals(original,cards.findPurchase(card.getId(),original.id(),auth()));
    }
    @Test void thirdInstallmentDatabaseFailureRollsBackNewPurchaseAndNewBills() throws Exception {
        jdbc.execute("ALTER TABLE credit_card_installments ADD CONSTRAINT us04_test_failure CHECK (installment_number <> 3)");
        try {
            mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json").content(json("100",3,"")))
                    .andExpect(status().isInternalServerError());empty();
        } finally {jdbc.execute("ALTER TABLE credit_card_installments DROP CONSTRAINT us04_test_failure");}
    }
    @Test void failedReplacementRestoresDeletedRowsAndHeaderAndNewBills() throws Exception {
        var original=cards.createPurchase(card.getId(),automatic("100",1,anchor),auth());int count=billCount();
        jdbc.execute("ALTER TABLE credit_card_installments ADD CONSTRAINT us04_test_failure CHECK (installment_number <> 3)");
        try {
            mvc.perform(put(path()+"/"+original.id()).contextPath("/api").with(user(owner)).contentType("application/json").content(json("300",3,"")))
                    .andExpect(status().isInternalServerError());
            assertEquals(original,cards.findPurchase(card.getId(),original.id(),auth()));assertEquals(count,billCount());
        } finally {jdbc.execute("ALTER TABLE credit_card_installments DROP CONSTRAINT us04_test_failure");}
    }
    @Test void retiredRoutesCannotBreakPlansAndStillHideForeignResources() throws Exception {
        var p=create();String installments=path()+"/"+p.id()+"/installments";
        mvc.perform(post(installments).contextPath("/api").with(user(owner)).contentType("application/json")
                .content("{\"installmentNumber\":4,\"amount\":1,\"categoryId\":"+category.getId()+"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete(installments+"/"+p.installments().getFirst().id()).contextPath("/api").with(user(owner))).andExpect(status().isConflict());
        assertEquals(p,cards.findPurchase(card.getId(),p.id(),auth()));
        var stranger=User.builder().id(-1L).firstName("Other").lastName("User").email("other@example.invalid").password("encoded").roles(owner.getRoles()).enabled(true).build();
        mvc.perform(post(path()).contextPath("/api").with(user(stranger)).contentType("application/json").content(json("100",1,""))).andExpect(status().isNotFound());
        mvc.perform(put(path()+"/"+p.id()).contextPath("/api").with(user(stranger)).contentType("application/json").content(json("100",1,""))).andExpect(status().isNotFound());
        mvc.perform(get(path()+"/"+p.id()).contextPath("/api").with(user(stranger))).andExpect(status().isNotFound());
        mvc.perform(post(installments).contextPath("/api").with(user(stranger)).contentType("application/json")
                .content("{\"installmentNumber\":1,\"amount\":100,\"categoryId\":"+category.getId()+"}")).andExpect(status().isNotFound());
    }
    @Test void categoriesAreResolvedBeforeCompletingPlanAndRollBackIntermediateBills() throws Exception {
        var income=tx.execute(s -> categories.save(Category.builder().user(owner).name("Plan income").type(CategoryType.INCOME).system(false).build()));
        mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json")
                .content(json("100",3,",\"installmentMode\":\"CUSTOM\",\"installments\":[{\"installmentNumber\":1,\"amount\":30},{\"installmentNumber\":2,\"amount\":30},{\"installmentNumber\":3,\"amount\":40,\"categoryId\":-1}]")))
                .andExpect(status().isBadRequest());empty();
        var foreign=tx.execute(s -> categories.save(Category.builder().user(null).name("Available global").type(CategoryType.EXPENSE).system(false).build()));
        var second=tx.execute(s -> users.save(User.builder().firstName("Other").lastName("Owner").email(UUID.randomUUID()+"@example.invalid")
                .password("encoded").dateOfBirth(LocalDate.of(1990,1,1)).enabled(true).emailVerified(true).roles(owner.getRoles()).build()));
        tx.executeWithoutResult(s -> categories.findById(foreign.getId()).orElseThrow().setUser(second));
        try {
            for(Long categoryId:List.of(foreign.getId(),999999999L)) {
                var dto=new CreditCardPurchaseCreateDto("Purchase",new BigDecimal("100"),anchor,3,category.getId(),InstallmentPlanMode.CUSTOM,
                        List.of(new PurchaseInstallmentPlanDto(1,new BigDecimal("30"),null),new PurchaseInstallmentPlanDto(2,new BigDecimal("30"),null),new PurchaseInstallmentPlanDto(3,new BigDecimal("40"),categoryId)));
                assertThrows(jakarta.persistence.EntityNotFoundException.class,()->cards.createPurchase(card.getId(),dto,auth()));empty();
            }
            assertThrows(IllegalArgumentException.class,()->cards.createPurchase(card.getId(),new CreditCardPurchaseCreateDto("P",BigDecimal.TEN,anchor,1,income.getId(),null,null),auth()));empty();
        } finally {tx.executeWithoutResult(s -> {jdbc.update("DELETE FROM categories WHERE id=?",foreign.getId());jdbc.update("DELETE FROM users WHERE id=?",second.getId());});}
    }
    @Test void legacyForeignBillCannotBeChangedThroughOwnedPurchase() throws Exception {
        var p=create();
        Long otherBill=tx.execute(s -> {
            var other=CreditCard.builder().user(owner).legalEntity(em.getReference(LegalEntity.class,card.getLegalEntity().getId()))
                    .cardBrand(brands.findAll().getFirst()).lastFourDigits("5678").creditLimit(BigDecimal.TEN).closingDay(10).dueDay(20).build();em.persist(other);
            return bills.save(CreditCardBill.builder().creditCard(other).status(CreditCardBillStatus.OPEN).closingDay(10).dueDay(20)
                    .periodStart(LocalDate.of(2027,1,10)).closingDate(LocalDate.of(2027,2,10)).dueDate(LocalDate.of(2027,2,20)).build()).getId();
        });
        jdbc.update("UPDATE credit_card_installments SET credit_card_bill_id=? WHERE id=?",otherBill,p.installments().getFirst().id());
        mvc.perform(put(path()+"/"+p.id()).contextPath("/api").with(user(owner)).contentType("application/json").content(json("100",1,"")))
                .andExpect(status().isNotFound());
        assertEquals(3,cards.findInstallments(card.getId(),p.id(),auth()).size());
        assertEquals(otherBill,jdbc.queryForObject("SELECT credit_card_bill_id FROM credit_card_installments WHERE id=?",Long.class,p.installments().getFirst().id()));
        String sql=new org.springframework.core.io.ClassPathResource("sql/purchase-installment-plans.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(jdbc.queryForList(sql).stream().anyMatch(row -> ((Number)row.get("purchase_id")).longValue()==p.id() && "FOREIGN_BILL".equals(row.get("reason"))));
    }
    @Test void repeatPostCreatesTwoCompletePurchasesAsDocumented(){
        var first=create();var second=create();assertNotEquals(first.id(),second.id());assertEquals(2,purchases());
        assertEquals(first.installments().size(),second.installments().size());
    }
    @Test void inventoryIdentifiesLegacyWithoutMutationAndExplicitCorrectionCanRepair() throws Exception {
        var p=create();String sql=new org.springframework.core.io.ClassPathResource("sql/purchase-installment-plans.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(jdbc.queryForList(sql).stream().noneMatch(row -> ((Number)row.get("purchase_id")).longValue()==p.id()));
        jdbc.update("DELETE FROM credit_card_installments WHERE id=?",p.installments().getLast().id());
        assertTrue(jdbc.queryForList(sql).stream().anyMatch(row -> ((Number)row.get("purchase_id")).longValue()==p.id() && "COUNT_MISMATCH".equals(row.get("reason"))));
        assertEquals(2,cards.findInstallments(card.getId(),p.id(),auth()).size());
        cards.updatePurchase(card.getId(),p.id(),automatic("100",3,anchor),auth());
        jdbc.update("UPDATE credit_card_installments SET amount=30 WHERE purchase_id=? AND installment_number=1",p.id());
        assertTrue(jdbc.queryForList(sql).stream().anyMatch(row -> ((Number)row.get("purchase_id")).longValue()==p.id() && "TOTAL_MISMATCH".equals(row.get("reason"))));
        jdbc.update("UPDATE credit_card_installments SET installment_number=4 WHERE purchase_id=? AND installment_number=3",p.id());
        assertTrue(jdbc.queryForList(sql).stream().anyMatch(row -> ((Number)row.get("purchase_id")).longValue()==p.id() && "NUMBER_MISMATCH".equals(row.get("reason"))));
    }
    @Test void openBillWithPaymentReferenceAlsoProtectsPlan(){
        var p=create();Long billId=p.installments().getFirst().creditCardBillId();
        cards.closeBill(card.getId(),billId,auth());cards.payBill(card.getId(),billId,new CreditCardBillPayDto(pocket.getId()),auth());
        tx.executeWithoutResult(s -> bills.findById(billId).orElseThrow().setStatus(CreditCardBillStatus.OPEN));
        assertThrows(IllegalStateException.class,()->cards.updatePurchase(card.getId(),p.id(),automatic("100",3,anchor),auth()));
        assertThrows(IllegalStateException.class,this::create);assertEquals(p,cards.findPurchase(card.getId(),p.id(),auth()));
    }
    void postgres(){Assumptions.assumeTrue("PostgreSQL".equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName())));}
    void awaitWaiter(){
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);int waiting=0;
        while(System.nanoTime()<deadline){
            waiting=jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",Integer.class);
            if(waiting>0)break;
            try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
        }
        assertTrue(waiting>0,"Follower must actually wait on PostgreSQL card lock");
    }
    @Test void closureWinsCreationWhichWaitsThenRejects() throws Exception {
        postgres();var p=create();var billId=p.installments().getFirst().creditCardBillId();List<Future<?>> futures=new ArrayList<>();
        tx.executeWithoutResult(s -> {
            locks.lockOwnedCard(card.getId(),owner.getId());cards.closeBill(card.getId(),billId,auth());
            futures.add(pool.submit(() -> assertThrows(IllegalStateException.class,this::create)));awaitWaiter();
        });futures.getFirst().get(10,TimeUnit.SECONDS);assertEquals(1,purchases());
    }
    @Test void paymentWinsCorrectionWhichPreservesPaidComposition() throws Exception {
        postgres();var p=create();Long billId=p.installments().getFirst().creditCardBillId();cards.closeBill(card.getId(),billId,auth());
        List<Future<?>> futures=new ArrayList<>();
        tx.executeWithoutResult(s -> {
            locks.lockOwnedCard(card.getId(),owner.getId());cards.payBill(card.getId(),billId,new CreditCardBillPayDto(pocket.getId()),auth());
            futures.add(pool.submit(() -> assertThrows(IllegalStateException.class,()->cards.updatePurchase(card.getId(),p.id(),automatic("200",1,anchor),auth()))));awaitWaiter();
        });futures.getFirst().get(10,TimeUnit.SECONDS);assertEquals(p,cards.findPurchase(card.getId(),p.id(),auth()));
        amount(jdbc.queryForObject("SELECT amount FROM transactions WHERE id=(SELECT payment_transaction_id FROM credit_card_bills WHERE id=?)",BigDecimal.class,billId),"33.33");
    }
    @Test void correctionWinsClosureAndPaymentUsesFinalAmount() throws Exception {
        postgres();var p=create();Long billId=p.installments().getFirst().creditCardBillId();List<Future<?>> futures=new ArrayList<>();
        tx.executeWithoutResult(s -> {
            locks.lockOwnedCard(card.getId(),owner.getId());cards.updatePurchase(card.getId(),p.id(),automatic("150",1,anchor),auth());
            futures.add(pool.submit(() -> {cards.closeBill(card.getId(),billId,auth());cards.payBill(card.getId(),billId,new CreditCardBillPayDto(pocket.getId()),auth());}));awaitWaiter();
        });futures.getFirst().get(10,TimeUnit.SECONDS);
        amount(jdbc.queryForObject("SELECT amount FROM transactions WHERE id=(SELECT payment_transaction_id FROM credit_card_bills WHERE id=?)",BigDecimal.class,billId),"150");
        assertEquals(1,cards.findPurchase(card.getId(),p.id(),auth()).installments().size());
    }
    @Test void openApiDocumentsRealCompletePlanAndRetiredRoute() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/credit-cards/{id}/purchases'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/credit-cards/{id}/purchases/{purchaseId}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/credit-cards/{id}/purchases/{purchaseId}/installments'].post.deprecated").value(true))
                .andExpect(jsonPath("$.components.schemas.CreditCardPurchaseCreateDto.properties.installments").exists());
    }
}
