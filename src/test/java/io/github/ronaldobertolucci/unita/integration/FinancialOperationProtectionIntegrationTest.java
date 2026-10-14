package io.github.ronaldobertolucci.unita.integration;

import io.github.ronaldobertolucci.unita.dto.card.CreditCardBillPayDto;
import io.github.ronaldobertolucci.unita.dto.pocket.*;
import io.github.ronaldobertolucci.unita.model.card.*;
import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import io.github.ronaldobertolucci.unita.model.finance.*;
import io.github.ronaldobertolucci.unita.model.group.*;
import io.github.ronaldobertolucci.unita.model.pocket.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.pocket.*;
import io.github.ronaldobertolucci.unita.service.card.CreditCardService;
import io.github.ronaldobertolucci.unita.service.user.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Each write commits independently; assertions read the actual post-request database state. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class FinancialOperationProtectionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PocketRepository pockets;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean TransactionRepository transactions;
    @Autowired TransferOperationRepository operations;
    @Autowired CategoryRepository categories;
    @Autowired LegalEntityRepository entities;
    @Autowired BankAccountTypeRepository bankTypes;
    @Autowired CardBrandRepository brands;
    @Autowired CreditCardBillRepository bills;
    @Autowired TransferService transfers;
    @Autowired PocketService pocketService;
    @Autowired CreditCardService cardService;
    @Autowired UserService userService;
    User owner, other;
    BankAccount source, target;
    Category sent, received;
    TransactionTemplate tx;
    private User newUser() {
        return users.save(User.builder().firstName("Protection").lastName("Test")
                .email(UUID.randomUUID()+"@example.invalid").password("encoded")
                .dateOfBirth(LocalDate.of(1990,1,1)).enabled(true).emailVerified(true)
                .roles(Set.of(roles.findByName("USER").orElseThrow())).build());
    }
    private BankAccount bank(User u) {
        var entity = entities.save(LegalEntity.builder().user(u).cnpj(String.format("%014d",u.getId()))
                .corporateName("Private bank "+u.getId()).build());
        return (BankAccount)pockets.save(BankAccount.builder().user(u).legalEntity(entity)
                .number("123").agency("1").bankAccountType(bankTypes.findAll().getFirst())
                .status(BankAccountStatus.ACTIVE).build());
    }
    private Transaction entry(Pocket pocket, Direction direction, Category category, String description) {
        return transactions.save(Transaction.builder().pocket(pocket).direction(direction).category(category)
                .amount(new BigDecimal("100.00")).description(description).transactionDate(LocalDate.now()).build());
    }
    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            owner = newUser(); other = newUser(); source=bank(owner); target=bank(other);
            var group=Group.builder().name("Protection group").responsibleUser(owner).build(); em.persist(group);
            em.persist(GroupMembership.builder().user(owner).group(group).build());
            em.persist(GroupMembership.builder().user(other).group(group).build());
            sent=categories.findSystemByName("Transferência Enviada").orElseThrow();
            received=categories.findSystemByName("Transferência Recebida").orElseThrow();
            entry(source,Direction.INCOME,received,"Opening balance");
        });
    }
    private UsernamePasswordAuthenticationToken auth(User u) {
        return new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities());
    }
    private TransferDto transfer() {
        return transfers.transfer(new TransferCreateDto(source.getId(),target.getId(),new BigDecimal("80.00"),"Transfer"),auth(owner));
    }
    private void balance(Pocket p, String expected) {
        assertEquals(0,new BigDecimal(expected).compareTo(transactions.calculateBalanceByPocketId(p.getId())));
    }
    private void rejectDelete(User u, Pocket p, Long id) throws Exception {
        mvc.perform(delete("/api/pockets/"+p.getId()+"/transactions/"+id).contextPath("/api").with(user(u)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Conflict"));
    }
    @Test void bothSidesAndBothOwnersAreProtectedAndPrivate() throws Exception {
        var result=transfer();
        assertFalse(result.sourceTransaction().canDelete());
        assertEquals(result.sourceTransaction().operationId(),result.targetTransaction().operationId());
        rejectDelete(owner,source,result.sourceTransaction().id());
        rejectDelete(other,target,result.targetTransaction().id());
        for(var pair:List.of(Map.entry(owner,source),Map.entry(other,target))) {
            mvc.perform(delete("/api/pockets/bank-accounts/"+pair.getValue().getId()).contextPath("/api").with(user(pair.getKey())))
                    .andExpect(status().isConflict());
        }
        balance(source,"20"); balance(target,"80");
        mvc.perform(get("/api/pockets/"+source.getId()+"/transactions").contextPath("/api").with(user(owner)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("\"canDelete\":false")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(other.getEmail()))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("targetPocket"))));
        mvc.perform(get("/api/pockets/"+target.getId()+"/transactions").contextPath("/api").with(user(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/pockets/"+target.getId()+"/transactions/"+result.targetTransaction().id()).contextPath("/api").with(user(owner)))
                .andExpect(status().isNotFound());
        assertThrows(IllegalStateException.class,()->userService.delete(owner.getId()));
        assertThrows(IllegalStateException.class,()->userService.delete(other.getId()));
    }
    @Test void fgtsWithdrawalAlsoHasPairAndBlocksAccountDeletion() throws Exception {
        var fgts=tx.execute(status->{
            var employer=IndividualEmployer.builder().user(owner).cpf(String.format("%011d",owner.getId())).name("Employer").build();em.persist(employer);
            var p=pockets.save(FgtsEmployerAccount.builder().user(owner).employer(employer).admissionDate(LocalDate.now())
                    .status(FgtsEmployerAccountStatus.ACTIVE).build());
            entry(p,Direction.INCOME,received,"FGTS balance");return p;
        });
        var result=transfers.fgtsWithdrawal(new TransferCreateDto(fgts.getId(),source.getId(),new BigDecimal("80"),"Withdrawal"),auth(owner));
        assertEquals("FGTS_WITHDRAWAL",result.sourceTransaction().operationType());
        rejectDelete(owner,fgts,result.sourceTransaction().id());rejectDelete(owner,source,result.targetTransaction().id());
        mvc.perform(delete("/api/pockets/fgts/"+fgts.getId()).contextPath("/api").with(user(owner))).andExpect(status().isConflict());
        balance(fgts,"20");balance(source,"180");
    }
    private CreditCardBill closedBill() {
        return tx.execute(status->{
            var card=CreditCard.builder().user(owner).legalEntity(source.getLegalEntity()).cardBrand(brands.findAll().getFirst())
                    .lastFourDigits("1234").creditLimit(new BigDecimal("500")).closingDay(10).dueDay(20).build();em.persist(card);
            var bill=bills.save(CreditCardBill.builder().creditCard(card).status(CreditCardBillStatus.CLOSED)
                    .closingDay(10).dueDay(20).closingDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(10))
                    .periodStart(LocalDate.now().minusMonths(1)).build());
            var purchase=CreditCardPurchase.builder().creditCard(card).description("Purchase").totalValue(new BigDecimal("80"))
                    .purchaseDate(LocalDate.now()).installmentsCount(1).build();em.persist(purchase);
            em.persist(CreditCardInstallment.builder().purchase(purchase).installmentNumber(1).installmentDate(LocalDate.now())
                    .amount(new BigDecimal("80")).creditCardBill(bill).category(sent).build());return bill;
        });
    }
    @Test void actualBillPaymentAndItsAccountAndCardCannotBeDeleted() throws Exception {
        var bill=closedBill(); long cardId=bill.getCreditCard().getId();
        cardService.payBill(cardId,bill.getId(),new CreditCardBillPayDto(source.getId()),auth(owner));
        long payment=tx.execute(status->bills.findById(bill.getId()).orElseThrow().getPaymentTransaction().getId());
        rejectDelete(owner,source,payment);
        mvc.perform(delete("/api/pockets/bank-accounts/"+source.getId()).contextPath("/api").with(user(owner))).andExpect(status().isConflict());
        mvc.perform(delete("/api/credit-cards/"+cardId).contextPath("/api").with(user(owner))).andExpect(status().isConflict());
        tx.executeWithoutResult(status->{var saved=bills.findById(bill.getId()).orElseThrow();assertEquals(CreditCardBillStatus.PAID,saved.getStatus());assertEquals(payment,saved.getPaymentTransaction().getId());});
        var listing=pocketService.findTransactions(source.getId(),null,null,auth(owner));
        assertEquals("BILL_PAYMENT",listing.stream().filter(t->t.id()==payment).findFirst().orElseThrow().operationType());
        balance(source,"20");
    }
    @Test void unlinkedEntryStillDeletesAndUnprotectedAccountStillDeletes() throws Exception {
        var entry=tx.execute(status->entry(target,Direction.INCOME,received,"Manual"));
        mvc.perform(delete("/api/pockets/"+target.getId()+"/transactions/"+entry.getId()).contextPath("/api").with(user(other)))
                .andExpect(status().isNoContent());
        assertFalse(transactions.existsById(entry.getId()));
        pocketService.deleteBankAccount(target.getId(),auth(other));assertFalse(pockets.existsById(target.getId()));
    }
    @Test void secondEntryDatabaseFailureRollsBackFirstAndPair() {
        long tc=transactions.count(), oc=operations.count();
        jdbc.execute("ALTER TABLE transactions ADD CONSTRAINT us02_fail_second CHECK (pocket_id <> "+target.getId()+" OR direction <> 'INCOME')");
        try {assertThrows(RuntimeException.class,this::transfer);}
        finally {jdbc.execute("ALTER TABLE transactions DROP CONSTRAINT us02_fail_second");}
        assertEquals(tc,transactions.count());assertEquals(oc,operations.count());balance(source,"100");balance(target,"0");
    }
    @Test void operationPersistenceFailureRollsBackBothEntries() {
        long tc=transactions.count(),oc=operations.count();
        jdbc.execute("ALTER TABLE transfer_operations ADD CONSTRAINT us02_fail_pair CHECK (source_transaction_id <= "+jdbc.queryForObject("SELECT max(id) FROM transactions",Long.class)+")");
        try {assertThrows(RuntimeException.class,this::transfer);}
        finally {jdbc.execute("ALTER TABLE transfer_operations DROP CONSTRAINT us02_fail_pair");}
        assertEquals(tc,transactions.count());assertEquals(oc,operations.count());balance(source,"100");balance(target,"0");
    }
    @Test void databaseRestrictsTransferEntriesAndPocketCascades() {
        var result=transfer();
        for(long id:List.of(result.sourceTransaction().id(),result.targetTransaction().id()))
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM transactions WHERE id=?",id));
        for(long id:List.of(source.getId(),target.getId()))
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM pockets WHERE id=?",id));
        balance(source,"20");balance(target,"80");assertTrue(pockets.existsById(source.getId()));assertTrue(pockets.existsById(target.getId()));
    }
    @Test void databaseRestrictsExplicitLegacyPaymentAndPocketCascade() {
        var bill=closedBill();long payment=tx.execute(status->{
            var p=entry(source,Direction.EXPENSE,sent,"Legacy payment");var b=bills.findById(bill.getId()).orElseThrow();b.setStatus(CreditCardBillStatus.PAID);b.setPaymentTransaction(p);return p.getId();
        });
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM transactions WHERE id=?",payment));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM pockets WHERE id=?",source.getId()));
        assertTrue(transactions.existsById(payment));
        Long retainedPayment=tx.execute(status->bills.findById(bill.getId()).orElseThrow().getPaymentTransaction().getId());
        assertEquals(payment,retainedPayment.longValue());
    }
    @Test void apiCreatesAtomicPairAndRejectsForeignSourceWithoutPersisting() throws Exception {
        String body="{\"sourcePocketId\":"+source.getId()+",\"targetPocketId\":"+target.getId()+",\"amount\":80,\"description\":\"API transfer\"}";
        long count=transactions.count();
        mvc.perform(post("/api/transfers").contextPath("/api").with(user(other)).contentType("application/json").content(body))
                .andExpect(status().isNotFound());assertEquals(count,transactions.count());
        mvc.perform(post("/api/transfers").contextPath("/api").with(user(owner)).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sourceTransaction.operationType").value("TRANSFER"))
                .andExpect(jsonPath("$.sourceTransaction.operationId").isNumber()).andExpect(jsonPath("$.targetTransaction.canDelete").value(false));
        assertEquals(count+2,transactions.count());balance(source,"20");balance(target,"80");
    }
    @Test void openApiDocumentsActualMetadataAndDeletionConflict() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api").with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.components.schemas.TransactionDto.properties.operationType").exists())
                .andExpect(jsonPath("$.components.schemas.TransactionDto.properties.operationId").exists())
                .andExpect(jsonPath("$.components.schemas.TransactionDto.properties.canDelete").exists());
    }
    @Test void inventorySeparatesProofFromAmbiguousHistoryWithoutMutation() throws Exception {
        transfer();var bill=closedBill();
        tx.executeWithoutResult(status->{var b=bills.findById(bill.getId()).orElseThrow();b.setStatus(CreditCardBillStatus.PAID);});
        long tc=transactions.count(),oc=operations.count();
        String sql=java.nio.file.Files.readString(java.nio.file.Path.of("docs/sql/US-02-inventario-operacoes.sql"));
        sql=sql.replaceAll("(?m)--.*$", "");
        var statements=sql.split(";");
        var explicit=jdbc.queryForList(statements[0]);assertFalse(explicit.isEmpty());
        var payments=jdbc.queryForList(statements[1]);assertTrue(payments.stream().anyMatch(row->row.get("classification").equals("PAID_WITHOUT_REFERENCE")));
        var ambiguous=jdbc.queryForList(statements[2]);assertTrue(ambiguous.stream().anyMatch(row->row.get("classification").equals("AMBIGUOUS_TRANSFER_CANDIDATE")));
        assertEquals(explicit,jdbc.queryForList(statements[0]));assertEquals(payments,jdbc.queryForList(statements[1]));assertEquals(ambiguous,jdbc.queryForList(statements[2]));
        assertEquals(tc,transactions.count());assertEquals(oc,operations.count());
        var listing=pocketService.findTransactions(source.getId(),null,null,auth(owner));
        assertTrue(listing.stream().anyMatch(t->t.operationType().equals("UNLINKED") && t.canDelete()));
    }

    @Test void databaseConflictAfterStaleServiceCheckReturnsSafe409AndRollsBackCascade() throws Exception {
        transfer();
        // Simulates a stale precheck: the final FK must still protect and produce a domain response.
        org.mockito.Mockito.doReturn(false).when(transactions).existsProtectedByPocketId(source.getId());
        mvc.perform(delete("/api/pockets/bank-accounts/"+source.getId()).contextPath("/api").with(user(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Resource is linked to a protected financial operation and cannot be deleted"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fk_transfer"))));
        assertTrue(pockets.existsById(source.getId()));balance(source,"20");balance(target,"80");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM bank_accounts WHERE pocket_id=?",Integer.class,source.getId()));
    }

}
