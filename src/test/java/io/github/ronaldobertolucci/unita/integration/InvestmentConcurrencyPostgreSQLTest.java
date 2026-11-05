package io.github.ronaldobertolucci.unita.integration;

import io.github.ronaldobertolucci.unita.dto.investment.*;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.finance.FinancialConcurrencyService;
import io.github.ronaldobertolucci.unita.service.investment.AssetService;
import io.github.ronaldobertolucci.unita.service.investment.InvestmentTransactionService;
import io.github.ronaldobertolucci.unita.service.scheduled.AssetMaturityJobProcessor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Run against a disposable PostgreSQL database via spring.datasource.*; never against application data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "spring.datasource.url", matches = "jdbc:postgresql:.*")
class InvestmentConcurrencyPostgreSQLTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired LegalEntityRepository entities;
    @Autowired AssetRepository assets;
    @Autowired InvestmentPositionRepository positions;
    @Autowired PocketRepository pockets;
    @Autowired InvestmentTransactionService movements;
    @Autowired AssetService service;
    @Autowired FinancialConcurrencyService locks;
    @Autowired AssetMaturityJobProcessor maturity;
    @Autowired MockMvc mvc;

    private TransactionTemplate tx;
    private ExecutorService pool;
    private User owner;
    private Long assetId;
    private Long pocketId;
    private final LocalDate date = LocalDate.of(2026, 11, 4);

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(manager);
        pool = Executors.newSingleThreadExecutor();
        createPortfolio();
    }

    private void createPortfolio() {
        tx.executeWithoutResult(status -> {
            owner = users.save(User.builder().firstName("Concurrency").lastName("Investment")
                    .email(UUID.randomUUID() + "@example.invalid").password("encoded")
                    .dateOfBirth(LocalDate.of(1990, 1, 1)).enabled(true).emailVerified(true)
                    .roles(Set.of(roles.findByName("USER").orElseThrow())).build());
            var issuer = entities.save(LegalEntity.builder().user(owner).cnpj("12345678000190")
                    .corporateName("Test issuer").build());
            var asset = assets.save(Asset.builder().user(owner).legalEntity(issuer).name("Concurrency asset")
                    .category(AssetCategory.RENDA_FIXA).status(AssetStatus.ACTIVE).build());
            io.github.ronaldobertolucci.unita.investment.InvestmentTestDetails.complete(asset);
            em.persist(asset.getFixedIncomeDetails());
            assetId = asset.getId();
            positions.save(InvestmentPosition.builder().asset(asset).build());
            var cash = new Cash();
            cash.setUser(owner);
            pocketId = pockets.save(cash).getId();
        });
        buy("100", "3");
    }

    @AfterEach
    void cleanup() throws Exception {
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        jdbc.update("DELETE FROM users WHERE id=?", owner.getId());
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities());
    }

    private void buy(String price, String quantity) {
        movements.buy(assetId, new InvestmentBuyDto(new BigDecimal(price), new BigDecimal(quantity), date, pocketId, 39L, null), auth());
    }

    private void sell() { sellQuantity("3"); }

    private void sellQuantity(String quantity) {
        movements.sell(assetId, new InvestmentSellDto(new BigDecimal("330"), new BigDecimal("30"), date, pocketId, 41L, null, new BigDecimal(quantity), java.util.UUID.randomUUID()), auth());
    }

    private InvestmentSellDto command(UUID operation, String quantity) {
        return new InvestmentSellDto(new BigDecimal("330"), new BigDecimal("30"), date, pocketId, 41L,
                "Venda: á, \"teste\"", new BigDecimal(quantity), operation);
    }

    private int operations() {
        return jdbc.queryForObject("SELECT count(*) FROM investment_sale_operations WHERE user_id=?", Integer.class, owner.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "3"})
    void sequentialRetryReturnsOriginalResponseEvenAfterFullRedemption(String quantity) {
        var dto = command(UUID.randomUUID(), quantity);
        var first = movements.sell(assetId, dto, auth());
        assertEquals(first, movements.sell(assetId, dto, auth()));
        assertEquals(1, operations());
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM investment_transactions WHERE asset_id=?", Integer.class, assetId));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM transactions WHERE pocket_id=?", Integer.class, pocketId));
        decimal("300", jdbc.queryForObject("SELECT redeemed_value FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        decimal(new BigDecimal("3").subtract(new BigDecimal(quantity)).toPlainString(), jdbc.queryForObject(
                "SELECT quantity FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        for (var movement : first) assertEquals(dto.operationId(), movement.operationId());
        var history = movements.findAllByAsset(assetId, auth());
        assertEquals(2, history.stream().filter(t -> dto.operationId().equals(t.operationId())).count());
    }

    @Test
    void decimalScaleIsIgnoredButTheOriginalResponseIsPreserved() {
        UUID id = UUID.randomUUID();
        var first = movements.sell(assetId, command(id, "1"), auth());
        var scaled = new InvestmentSellDto(new BigDecimal("330.00000000"), new BigDecimal("30.0"),
                date, pocketId, 41L, command(id, "1").notes(), new BigDecimal("1.00000000"), id);
        assertEquals(first, movements.sell(assetId, scaled, auth()));
        // Historical references may be detached later; an acknowledged result remains the same snapshot.
        jdbc.update("UPDATE investment_transactions SET transaction_id=NULL WHERE asset_id=? AND type='SELL'", assetId);
        assertEquals(first, movements.sell(assetId, scaled, auth()));
        assertEquals(1, operations());
    }

    @ParameterizedTest
    @ValueSource(strings = {"quantity", "gross", "tax", "pocket", "category", "date", "notes"})
    void sameUuidWithAnyDifferentCommandFieldConflicts(String field) {
        UUID id = UUID.randomUUID();
        var dto = command(id, "1");
        var first = movements.sell(assetId, dto, auth());
        var changed = new InvestmentSellDto(field.equals("gross") ? new BigDecimal("331") : dto.grossAmount(),
                field.equals("tax") ? new BigDecimal("31") : dto.taxAmount(),
                field.equals("date") ? date.plusDays(1) : date,
                field.equals("pocket") ? pocketId + 100000 : pocketId,
                field.equals("category") ? 40L : 41L,
                field.equals("notes") ? dto.notes() + " " : dto.notes(),
                field.equals("quantity") ? new BigDecimal("2") : dto.quantity(), id);
        assertThrows(IllegalStateException.class, () -> movements.sell(assetId, changed, auth()));
        assertEquals(first, movements.sell(assetId, dto, auth()));
        assertEquals(1, operations());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM transactions WHERE pocket_id=?", Integer.class, pocketId));
    }

    private Long anotherOwnedAsset() {
        return tx.execute(status -> {
            var issuer = assets.findById(assetId).orElseThrow().getLegalEntity();
            var another = assets.save(Asset.builder().user(owner).legalEntity(issuer).name("Another asset")
                    .category(AssetCategory.RENDA_FIXA).build());
            io.github.ronaldobertolucci.unita.investment.InvestmentTestDetails.complete(another);
            em.persist(another.getFixedIncomeDetails());
            positions.save(InvestmentPosition.builder().asset(another).quantity(BigDecimal.ONE)
                    .totalInvested(new BigDecimal("100")).remainingCost(new BigDecimal("100"))
                    .averagePrice(new BigDecimal("100")).currentValue(new BigDecimal("100")).build());
            return another.getId();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "3"})
    void concurrentDuplicateReturnsSameResultWithoutAnotherCashCredit(String quantity) throws Exception {
        var dto = command(UUID.randomUUID(), quantity);
        var first = new java.util.concurrent.atomic.AtomicReference<java.util.List<InvestmentTransactionDto>>();
        race(() -> first.set(movements.sell(assetId, dto, auth())),
                () -> assertEquals(first.get(), movements.sell(assetId, dto, auth())));
        BigDecimal remaining = new BigDecimal("3").subtract(new BigDecimal(quantity));
        decimal(remaining.toPlainString(), jdbc.queryForObject("SELECT quantity FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        decimal(remaining.multiply(new BigDecimal("100")).toPlainString(), jdbc.queryForObject("SELECT remaining_cost FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        decimal("300", jdbc.queryForObject("SELECT redeemed_value FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        assertEquals(quantity.equals("3") ? "REDEEMED" : "ACTIVE", jdbc.queryForObject("SELECT status FROM assets WHERE id=?", String.class, assetId));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM transactions WHERE pocket_id=?", Integer.class, pocketId));
        assertEquals(1, operations());
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM investment_transactions WHERE asset_id=?", Integer.class, assetId));
    }

    @Test
    void sameUuidOnDifferentAssetsIsSerializedAndRejected() throws Exception {
        Long another = anotherOwnedAsset();
        var dto = command(UUID.randomUUID(), "1");
        race(() -> movements.sell(assetId, dto, auth()),
                () -> assertThrows(IllegalStateException.class, () -> movements.sell(another, dto, auth())));
        assertEquals(1, operations());
        decimal("1", jdbc.queryForObject("SELECT quantity FROM investment_positions WHERE asset_id=?", BigDecimal.class, another));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM investment_transactions WHERE asset_id=?", Integer.class, another));
    }

    @Test
    void identicalCommandsWithDifferentUuidsAreIntentionalSeparateSales() {
        var first = movements.sell(assetId, command(UUID.randomUUID(), "1"), auth());
        var second = movements.sell(assetId, command(UUID.randomUUID(), "1"), auth());
        assertNotEquals(first.getFirst().id(), second.getFirst().id());
        assertEquals(2, operations());
        decimal("1", jdbc.queryForObject("SELECT quantity FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        decimal("600", jdbc.queryForObject("SELECT redeemed_value FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
    }

    @Test
    void operationUuidIsScopedToUserAndNeverRevealsAnotherOwnersResult() {
        var originalOwner = owner;
        Long originalAsset = assetId, originalPocket = pocketId;
        UUID id = UUID.randomUUID();
        var first = movements.sell(assetId, command(id, "1"), auth());
        createPortfolio();
        try {
            var secondCommand = command(id, "1");
            assertThrows(jakarta.persistence.EntityNotFoundException.class,
                    () -> movements.sell(originalAsset, secondCommand, auth()));
            var second = movements.sell(assetId, secondCommand, auth());
            assertNotEquals(first.getFirst().id(), second.getFirst().id());
            assertEquals(second, movements.sell(assetId, secondCommand, auth()));
            assertEquals(1, operations());
        } finally {
            jdbc.update("DELETE FROM users WHERE id=?", owner.getId());
            owner = originalOwner; assetId = originalAsset; pocketId = originalPocket;
        }
        assertEquals(first, movements.sell(assetId, command(id, "1"), auth()));
    }

    private void yieldCash() {
        movements.yield(assetId, new InvestmentYieldDto(new BigDecimal("5"), date, pocketId, 40L, null), auth());
    }

    private void value(String value) {
        service.updatePosition(assetId, new InvestmentPositionUpdateDto(new BigDecimal(value), date), auth());
    }

    /** Prove the second connection actually waits on this leader, rather than hoping threads overlap. */
    private void race(Runnable first, Runnable second) throws Exception {
        var futures = new java.util.ArrayList<Future<?>>();
        tx.executeWithoutResult(status -> {
            locks.lockOwnedAsset(assetId, owner.getId());
            int leaderPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
            first.run();
            futures.add(pool.submit(() -> tx.executeWithoutResult(waiting -> {
                // Simulate OSIV/a surrounding transaction caching the OLD position before acquiring the lock.
                var stale = em.find(Asset.class, assetId);
                assertNotNull(stale.getPosition());
                second.run();
                // Expected business exceptions caught by assertThrows mark the joined transaction rollback-only.
                if (waiting.isRollbackOnly()) waiting.setRollbackOnly();
            })));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            boolean blocked = false;
            while (System.nanoTime() < deadline) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))",
                        Boolean.class, leaderPid));
                if (blocked) break;
                try { Thread.sleep(10); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            assertTrue(blocked, "Second writer must wait on the leader's PostgreSQL asset lock");
        });
        futures.getFirst().get(10, TimeUnit.SECONDS);
    }

    private void assertPosition(String status, String quantity, String invested, String current, String received, String cash) {
        var row = jdbc.queryForMap("SELECT a.status,p.* FROM assets a JOIN investment_positions p ON p.asset_id=a.id WHERE a.id=?", assetId);
        assertEquals(status, row.get("status"));
        decimal(quantity, (BigDecimal) row.get("quantity"));
        decimal(invested, (BigDecimal) row.get("total_invested"));
        decimal(current, (BigDecimal) row.get("current_value"));
        decimal(received, (BigDecimal) row.get("redeemed_value"));
        decimal(status.equals("REDEEMED") ? "0" : invested, (BigDecimal) row.get("remaining_cost"));
        decimal(cash, jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN direction='INCOME' THEN amount ELSE -amount END),0) FROM transactions WHERE pocket_id=?", BigDecimal.class, pocketId));
    }

    private static void decimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " != " + actual);
    }

    @Test
    void concurrentPartialSalesCannotExceedAvailableUnits() throws Exception {
        race(() -> sellQuantity("2"), () -> assertThrows(IllegalStateException.class, () -> sellQuantity("2")));
        decimal("1", jdbc.queryForObject("SELECT quantity FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        decimal("100", jdbc.queryForObject("SELECT remaining_cost FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        decimal("100", jdbc.queryForObject("SELECT current_value FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM assets WHERE id=?", String.class, assetId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM investment_transactions WHERE asset_id=? AND type='SELL'", Integer.class, assetId));
        decimal("300", jdbc.queryForObject("SELECT redeemed_value FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        sellQuantity("1");
        assertPosition("REDEEMED", "0", "300", "0", "600", "300");
    }

    @Test
    void concurrentPartialSalesConsumingExactlyTheBalanceCloseOnlyOnFinalSale() throws Exception {
        race(() -> sellQuantity("2"), () -> sellQuantity("1"));
        assertPosition("REDEEMED", "0", "300", "0", "600", "300");
        decimal("300", jdbc.queryForObject("SELECT sum(cost_basis) FROM investment_transactions WHERE asset_id=? AND type='SELL'", BigDecimal.class, assetId));
    }

    @Test
    void buyWaitingForPartialSaleUsesTheRemainingCost() throws Exception {
        race(() -> sellQuantity("1"), () -> buy("100", "1"));
        decimal("3", jdbc.queryForObject("SELECT quantity FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        decimal("400", jdbc.queryForObject("SELECT total_invested FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        decimal("300", jdbc.queryForObject("SELECT remaining_cost FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        decimal("100", jdbc.queryForObject("SELECT average_price FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM assets WHERE id=?", String.class, assetId));
    }

    @Test
    void concurrentFullSalesCannotBothConsumeThePosition() throws Exception {
        race(this::sell, () -> assertThrows(IllegalStateException.class, this::sell));
        assertPosition("REDEEMED", "0", "300", "0", "300", "0");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM investment_transactions WHERE asset_id=? AND type='SELL'", Integer.class, assetId));
    }

    @Test
    void buyThenSellIncludesTheCommittedContribution() throws Exception {
        race(() -> buy("100", "1"), () -> sellQuantity("4"));
        assertPosition("REDEEMED", "0", "400", "0", "300", "-100");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUY", "YIELD", "VALUATION"})
    void salePreventsWaitingWriterFromUsingStaleActiveStatus(String operation) throws Exception {
        Runnable writer = switch (operation) {
            case "BUY" -> () -> buy("100", "1");
            case "YIELD" -> this::yieldCash;
            default -> () -> value("400");
        };
        race(this::sell, () -> assertThrows(IllegalStateException.class, writer::run));
        assertPosition("REDEEMED", "0", "300", "0", "300", "0");
    }

    @Test
    void yieldThenSalePreservesBothReceipts() throws Exception {
        race(this::yieldCash, this::sell);
        assertPosition("REDEEMED", "0", "300", "0", "305", "5");
    }

    @Test
    void valuationThenSaleDoesNotResurrectThePosition() throws Exception {
        race(() -> value("400"), this::sell);
        assertPosition("REDEEMED", "0", "300", "0", "300", "0");
    }

    @Test
    void valuationThenYieldReadsTheNewValueDespiteCachedPosition() throws Exception {
        race(() -> value("400"), this::yieldCash);
        assertPosition("ACTIVE", "3", "300", "395", "5", "-295");
    }

    @Test
    void concurrentBuysDoNotLoseQuantityOrCost() throws Exception {
        race(() -> buy("100", "1"), () -> buy("100", "2"));
        assertPosition("ACTIVE", "6", "600", "600", "0", "-600");
    }

    @Test
    void maturityJobDoesNotOverwriteRedemptionAfterWaiting() throws Exception {
        race(this::sell, () -> maturity.process(assetId));
        assertPosition("REDEEMED", "0", "300", "0", "300", "0");
    }

    @Test
    void metadataEditDoesNotOverwriteRedemptionWithCachedStatus() throws Exception {
        Long issuerId = jdbc.queryForObject("SELECT legal_entity_id FROM assets WHERE id=?", Long.class, assetId);
        race(this::sell, () -> service.update(assetId,
                new AssetUpdateDto("Renamed", issuerId, null, null), auth()));
        assertPosition("REDEEMED", "0", "300", "0", "300", "0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "3"})
    void persistenceFailureAfterCashAndSellInsertionRollsBackEverything(String quantity) {
        var dto = command(UUID.randomUUID(), quantity);
        jdbc.execute("ALTER TABLE investment_transactions ADD CONSTRAINT be02_test_tax_failure CHECK (NOT (type='TAX' AND asset_id=" + assetId + "))");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> movements.sell(assetId, dto, auth()));
            assertPosition("ACTIVE", "3", "300", "300", "0", "-300");
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM investment_transactions WHERE asset_id=?", Integer.class, assetId));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM transactions WHERE pocket_id=?", Integer.class, pocketId));
            assertEquals(0, operations());
        } finally {
            jdbc.execute("ALTER TABLE investment_transactions DROP CONSTRAINT be02_test_tax_failure");
        }
        var retried = movements.sell(assetId, dto, auth());
        assertEquals(dto.operationId(), retried.getFirst().operationId());
        assertEquals(retried, movements.sell(assetId, dto, auth()));
        assertEquals(1, operations());
    }

    @Test
    void failureSavingIdempotencySnapshotAlsoRollsBackCashHistoryAndPosition() {
        var dto = command(UUID.randomUUID(), "3");
        jdbc.execute("ALTER TABLE investment_sale_operations ADD CONSTRAINT be04_test_snapshot_failure CHECK (user_id<>" + owner.getId() + ")");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> movements.sell(assetId, dto, auth()));
            assertPosition("ACTIVE", "3", "300", "300", "0", "-300");
            assertEquals(0, operations());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM investment_transactions WHERE asset_id=?", Integer.class, assetId));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM transactions WHERE pocket_id=?", Integer.class, pocketId));
        } finally {
            jdbc.execute("ALTER TABLE investment_sale_operations DROP CONSTRAINT be04_test_snapshot_failure");
        }
        var result = movements.sell(assetId, dto, auth());
        assertEquals(result, movements.sell(assetId, dto, auth()));
        assertPosition("REDEEMED", "0", "300", "0", "300", "0");
        assertEquals(1, operations());
    }

    private String path() { return "/api/assets/" + assetId; }

    @ParameterizedTest
    @CsvSource({"-1", "0.000000001", "10000000000"})
    void invalidValuationReturns400WithoutMutation(String value) throws Exception {
        mvc.perform(patch(path() + "/position").contextPath("/api").with(user(owner))
                        .contentType("application/json").content("{\"currentValue\":" + value + ",\"lastValuationDate\":\"2026-11-04\"}"))
                .andExpect(status().isBadRequest());
        assertPosition("ACTIVE", "3", "300", "300", "0", "-300");
    }

    @ParameterizedTest
    @CsvSource({"buy,amount,10000000000", "buy,quantity,0.000000001", "buy,amount,0.000000001",
            "sell,grossAmount,10000000000", "sell,taxAmount,0.000000001", "yield,amount,0.000000001"})
    void invalidMovementPrecisionReturns400WithoutMutation(String kind, String field, String value) throws Exception {
        String body = switch (kind) {
            case "buy" -> "\"amount\":100,\"quantity\":1";
            case "sell" -> "\"operationId\":\"" + UUID.randomUUID() + "\",\"quantity\":3,\"grossAmount\":330,\"taxAmount\":30";
            default -> "\"amount\":5";
        };
        body = body.replaceAll("\"" + field + "\":[0-9]+", "\"" + field + "\":" + value);
        mvc.perform(post(path() + "/transactions/" + kind).contextPath("/api").with(user(owner))
                        .contentType("application/json").content("{" + body + ",\"transactionDate\":\"2026-11-04\",\"pocketId\":" + pocketId + ",\"categoryId\":41}"))
                .andExpect(status().isBadRequest());
        assertPosition("ACTIVE", "3", "300", "300", "0", "-300");
    }

    @Test
    void roundsContributionOnceAndRejectsDerivedZeroAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> buy("0.00000001", "0.00000001"));
        assertThrows(IllegalArgumentException.class, () -> buy("9999999999", "2"));
        buy("0.12345678", "0.12345678");
        decimal("300.01524158", jdbc.queryForObject("SELECT total_invested FROM investment_positions WHERE asset_id=?", BigDecimal.class, assetId));
        decimal("0.01524158", jdbc.queryForObject("SELECT amount FROM investment_transactions WHERE asset_id=? ORDER BY id DESC LIMIT 1", BigDecimal.class, assetId));
    }

    @Test
    void rejectsAccumulatedOverflowBeforeWritingCash() {
        value("9999999999.99999999");
        assertThrows(IllegalArgumentException.class, () -> buy("1", "1"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM transactions WHERE pocket_id=?", Integer.class, pocketId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM investment_transactions WHERE asset_id=?", Integer.class, assetId));
    }

    @Test
    void emptyPositionCannotBeValuedOrSoldButZeroValuationIsAllowedWithUnits() throws Exception {
        value("0");
        assertPosition("ACTIVE", "3", "300", "0", "0", "-300");
        jdbc.update("UPDATE investment_positions SET quantity=0,average_price=0,total_invested=0,remaining_cost=0 WHERE asset_id=?", assetId);
        mvc.perform(patch(path() + "/position").contextPath("/api").with(user(owner))
                        .contentType("application/json").content("{\"currentValue\":1,\"lastValuationDate\":\"2026-11-04\"}"))
                .andExpect(status().isConflict());
        assertThrows(IllegalStateException.class, this::sell);
        value("0");
    }

    @Test
    void missingAndForeignOwnedAssetsAreUnavailableToWriters() throws Exception {
        User stranger = new User();
        stranger.setId(-1L);
        var unauthorized = new UsernamePasswordAuthenticationToken(stranger, null);
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> movements.sell(assetId,
                new InvestmentSellDto(new BigDecimal("330"), BigDecimal.ZERO, date, pocketId, 41L, null, new BigDecimal("3"), java.util.UUID.randomUUID()), unauthorized));
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> movements.buy(-1L,
                new InvestmentBuyDto(BigDecimal.ONE, BigDecimal.ONE, date, pocketId, 39L, null), auth()));
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> service.updatePosition(assetId,
                new InvestmentPositionUpdateDto(BigDecimal.ONE, date), unauthorized));
        assertPosition("ACTIVE", "3", "300", "300", "0", "-300");
    }
}
