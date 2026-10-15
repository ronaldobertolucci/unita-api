package io.github.ronaldobertolucci.unita.service.investment;

import io.github.ronaldobertolucci.unita.dto.investment.baseline.InvestmentBaseline.*;
import io.github.ronaldobertolucci.unita.investment.LegacyInvestmentFixture;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.*;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvestmentBaselineCollectorTest {
    private static final Instant CAPTURE = Instant.parse("2026-10-14T12:00:00Z");

    @Test
    void deterministicReadableSnapshotPreservesEveryRowPocketAndSequence() throws Exception {
        try (var fixture = LegacyInvestmentFixture.h2()) {
            var beforeRows = fixture.rawRows();
            var beforeSequences = fixture.sequences();
            var first = new InvestmentBaselineCollector(fixture.dataSource, Clock.fixed(CAPTURE, ZoneOffset.UTC)).collect();
            var second = new InvestmentBaselineCollector(fixture.dataSource,
                    Clock.fixed(CAPTURE.plusSeconds(60), ZoneOffset.UTC)).collect();
            assertEquals(first.state(), second.state());
            assertEquals(first.canonicalJson(), second.canonicalJson());
            assertEquals(first.checksum(), second.checksum());
            assertEquals(64, first.checksum().length());
            assertNotEquals(first.capturedAt(), second.capturedAt());
            assertEquals(beforeRows, fixture.rawRows());
            assertEquals(beforeSequences, fixture.sequences());
            assertFalse(beforeSequences.isEmpty());

            var state = first.state();
            assertEquals("1", state.formatVersion());
            assertEquals(List.of("91001", "91002"), state.users().stream().map(UserState::userId).toList());
            var owner = state.users().getFirst();
            assertEquals(4, owner.counts().assets());
            assertEquals(3, owner.counts().positions());
            assertEquals(10, owner.counts().investmentTransactions());
            assertEquals(8, owner.counts().cashTransactions());
            assertEquals(List.of(new Count("BUY", 4), new Count("SELL", 2), new Count("TAX", 2), new Count("YIELD", 2)),
                    owner.counts().investmentTransactionsByType());
            assertEquals(List.of(new Count("ACTIVE", 2), new Count("MATURED", 1), new Count("REDEEMED", 1)),
                    owner.counts().assetsByStatus());
            assertEquals(List.of(new Count("PREVIDENCIA", 2), new Count("RENDA_FIXA", 2)), owner.counts().assetsByCategory());
            assertEquals(List.of("1612.46", "-50", "0"), owner.pockets().stream().map(PocketState::balance).toList());
            assertEquals(List.of("66.67", "0"), state.users().getLast().pockets().stream().map(PocketState::balance).toList());
            assertEquals(2, owner.institutions().size());
            assertEquals(1, state.users().getLast().institutions().size());
            assertEquals(owner.institutions().getFirst().cnpj(), state.users().getLast().institutions().getFirst().cnpj());
            var active = owner.assets().get(1);
            assertEquals("500.12345678", active.position().totalInvested());
            assertEquals("550.87654321", active.position().currentValue());
            assertEquals("5.12345678", active.position().quantity());
            assertEquals("2025-12-31", active.position().lastValuationDate());
            assertEquals("92999", active.custodianLegalEntityId());
            assertEquals("1.1", active.fixedIncomeDetails().annualRate());
            assertEquals("REGRESSIVO", owner.assets().get(2).pensionDetails().taxRegime());
            assertNull(owner.assets().get(3).position());
            assertNull(state.users().getLast().assets().getFirst().position().lastValuationDate());
            assertEquals(List.of("97004", "97005", "97006", "97007", "97008", "97009"),
                    active.transactions().stream().map(InvestmentTransaction::id).toList());
            assertNull(active.transactions().get(3).pocketTransactionId());
            assertTrue(active.transactions().get(3).notes().contains("\"á\""));
            assertTrue(owner.cashTransactions().stream().anyMatch(t -> t.id().equals("95007")));
            assertTrue(owner.findings().contains(new Finding("INVALID_CUSTODIAN_INSTITUTION", "93002", "93002", "92999")));
            assertTrue(owner.findings().contains(new Finding("FOREIGN_CUSTODIAN_INSTITUTION", "93003", "93003", "92003")));
            assertEquals(2, owner.findings().stream().filter(f -> f.code().equals("SHARED_CASH_REFERENCE")).count());
            assertTrue(owner.findings().contains(new Finding("MISSING_POSITION", "93004", "93004", null)));
            assertFalse(first.canonicalJson().contains("not-a-login-hash"));
            assertFalse(first.canonicalJson().contains("example.invalid"));
            var tree = JsonMapper.builder().build().readTree(first.canonicalJson());
            assertTrue(tree.get("users").get(0).get("userId").isString());
            assertTrue(tree.get("users").get(0).get("assets").get(1).get("position").get("quantity").isString());
        }
    }

    @Test
    void s01ProvesCorrectCashAndEconomicResultSeparatelyFromLegacyPresentation() throws Exception {
        var json = JsonMapper.builder().build();
        var expected = json.readTree(new ClassPathResource("investments/s01-economic-expectation.json")
                .getContentAsString(StandardCharsets.UTF_8));
        try (var fixture = LegacyInvestmentFixture.h2()) {
            var state = new InvestmentBaselineCollector(fixture.dataSource).collect().state();
            var asset = state.users().getFirst().assets().getFirst();
            assertEquals(expected.get("assetId").asString(), asset.id());
            var principal = new BigDecimal(expected.get("principal").asString());
            var gross = new BigDecimal(expected.get("grossRedemption").asString());
            var tax = new BigDecimal(expected.get("withheldTax").asString());
            decimal(expected.get("expectedGrossProfit").asString(), gross.subtract(principal));
            decimal(expected.get("expectedNetProfit").asString(), gross.subtract(tax).subtract(principal));
            var cash = state.users().getFirst().cashTransactions().stream()
                    .filter(t -> t.id().equals(expected.get("sellCashTransactionId").asString())).findFirst().orElseThrow();
            assertEquals(expected.get("pocketId").asString(), cash.pocketId());
            assertEquals(expected.get("expectedCashCredit").asString(), cash.amount());
            assertEquals("INCOME", cash.direction());
            assertEquals("1085", asset.position().redeemedValue());
            assertEquals("1000", asset.position().totalInvested());
            // Source observation only. No assertion requiring the dashboard to keep its bug.
            decimal(asset.displayedNetProfit(), new BigDecimal(asset.position().redeemedValue())
                    .subtract(principal).subtract(new BigDecimal(asset.taxTotal())));
            assertNull(asset.transactions().getLast().pocketTransactionId());
            assertEquals("TAX", asset.transactions().getLast().type());
            assertEquals(3, asset.transactions().size());
        }
    }

    @Test
    void checksumDetectsNotesDatesPositionsAndPocketChangesEvenWhenGlobalBalanceIsUnchanged() throws Exception {
        try (var fixture = LegacyInvestmentFixture.h2()) {
            var collector = new InvestmentBaselineCollector(fixture.dataSource);
            var previous = collector.collect();
            for (String sql : List.of(
                    "UPDATE investment_transactions SET notes='Changed note' WHERE id=97001",
                    "UPDATE investment_transactions SET transaction_date=DATE '2026-02-03' WHERE id=97001",
                    "UPDATE investment_positions SET current_value=current_value+0.00000001 WHERE id=96002",
                    "UPDATE transactions SET pocket_id=94002 WHERE id=95007")) {
                fixture.jdbc.update(sql);
                var changed = collector.collect();
                assertNotEquals(previous.checksum(), changed.checksum());
                assertNotEquals(previous.canonicalJson(), changed.canonicalJson());
                previous = changed;
            }
            var owner = previous.state().users().getFirst();
            assertEquals(List.of("1597.34", "-34.88", "0"), owner.pockets().stream().map(PocketState::balance).toList());
            decimal("1562.46", owner.pockets().stream().map(p -> new BigDecimal(p.balance()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    @Test
    void usesOnlySelectInDedicatedReadOnlyRepeatableReadTransactionAndAlwaysRollsBack() throws Exception {
        try (var fixture = LegacyInvestmentFixture.h2(); var connection = fixture.dataSource.getConnection()) {
            Connection tracked = spy(connection);
            var trackedStatements = new ArrayList<Statement>();
            doAnswer(invocation -> {
                var statement = spy(connection.createStatement());
                trackedStatements.add(statement);
                return statement;
            }).when(tracked).createStatement();
            DataSource dedicated = new AbstractDataSource() {
                @Override public Connection getConnection() { return tracked; }
                @Override public Connection getConnection(String u, String p) { return tracked; }
            };
            new InvestmentBaselineCollector(dedicated).collect();
            verify(tracked).setReadOnly(true);
            verify(tracked).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            verify(tracked).setAutoCommit(false);
            verify(tracked).rollback();
            verify(tracked, never()).commit();
            verify(tracked).close();
            var statements = trackedStatements.stream().flatMap(statement -> mockingDetails(statement).getInvocations().stream())
                    .filter(i -> i.getMethod().getName().equals("executeQuery"))
                    .map(i -> (String) i.getArgument(0)).toList();
            assertFalse(statements.isEmpty());
            assertTrue(statements.stream().allMatch(sql -> sql.startsWith("SELECT ")));
        }
    }

    @Test
    void failedCollectionStillRollsBackAndClosesWithoutCommitting() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenThrow(new SQLException("Synthetic read failure"));
        assertThrows(RuntimeException.class, () -> new InvestmentBaselineCollector(source).collect());
        verify(connection).rollback();
        verify(connection).close();
        verify(connection, never()).commit();
    }

    private static void decimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
