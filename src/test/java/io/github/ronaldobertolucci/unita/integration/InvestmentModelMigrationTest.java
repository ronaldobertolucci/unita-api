package io.github.ronaldobertolucci.unita.integration;

import io.github.ronaldobertolucci.unita.investment.LegacyInvestmentFixture;
import io.github.ronaldobertolucci.unita.service.investment.InvestmentBaselineCollector;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Optional PostgreSQL: -Dbe01.postgres.url=... -Dbe01.postgres.username=... -Dbe01.postgres.password=... */
class InvestmentModelMigrationTest {
    private LegacyInvestmentFixture fixture() throws Exception {
        String url = System.getProperty("be01.postgres.url");
        var fixture = url == null ? LegacyInvestmentFixture.h2() : new LegacyInvestmentFixture(url,
                System.getProperty("be01.postgres.username"), System.getProperty("be01.postgres.password"));
        try {
            // Explicit synthetic repairs; original inconsistent fixture remains untouched for baseline tests.
            fixture.jdbc.update("UPDATE assets SET custodian_legal_entity_id=92002 WHERE id IN (93002,93003)");
            fixture.jdbc.update("DELETE FROM investment_transactions WHERE id IN (97008,97009)");
            fixture.jdbc.update("UPDATE investment_positions SET redeemed_value=27.46345678 WHERE asset_id=93002");
            fixture.jdbc.update("""
                    INSERT INTO investment_positions(asset_id,quantity,average_price,total_invested,current_value)
                    VALUES(93004,3,1000,3000,3300)
                    """);
            fixture.jdbc.update("""
                    INSERT INTO investment_transactions(id,asset_id,type,amount,transaction_date)
                    VALUES(97012,93004,'BUY',3000,DATE '2026-01-01')
                    """);
            fixture.jdbc.update("""
                    INSERT INTO assets(id,user_id,legal_entity_id,name,category,status)
                    VALUES(93006,91001,92001,'Empty investment','PREVIDENCIA','ACTIVE')
                    """);
            fixture.jdbc.update("INSERT INTO investment_positions(asset_id) VALUES(93006)");
            flyway(fixture, "25").migrate();
            return fixture;
        } catch (Exception e) {
            fixture.close();
            throw e;
        }
    }

    private Flyway flyway(LegacyInvestmentFixture fixture, String version) {
        return fixture.migrationTo(version);
    }

    @Test
    void upgradesAndPreservesEveryLegacyColumnBaselineCashAndSequence() throws Exception {
        try (var fixture = fixture()) {
            var before = fixture.rawRows();
            var sequences = fixture.sequences();
            var baseline = new InvestmentBaselineCollector(fixture.dataSource).collect();
            var migration = flyway(fixture, "26");
            assertEquals(1, migration.migrate().migrationsExecuted);
            migration.validate();
            assertEquals(0, migration.migrate().migrationsExecuted);
            assertEquals(baseline.canonicalJson(), new InvestmentBaselineCollector(fixture.dataSource).collect().canonicalJson());
            assertEquals(sequences, fixture.sequences());
            var after = fixture.rawRows();
            after.get("investment_positions").forEach(row -> row.remove("remaining_cost"));
            after.get("investment_transactions").forEach(row -> {
                assertNull(row.remove("quantity"));
                assertNull(row.remove("cost_basis"));
                assertNull(row.remove("related_sell_id"));
                assertEquals("SELL", row.remove("related_sell_type"));
            });
            assertEquals(before, after);
            for (var expected : List.of(new String[]{"93001", "0"}, new String[]{"93002", "500.12345678"},
                    new String[]{"93003", "200"}, new String[]{"93004", "3000"},
                    new String[]{"93005", "33.33333333"}, new String[]{"93006", "0"})) {
                assertEquals(0, new BigDecimal(expected[1]).compareTo(fixture.jdbc.queryForObject(
                        "SELECT remaining_cost FROM investment_positions WHERE asset_id=?", BigDecimal.class, Long.valueOf(expected[0]))));
            }
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "UPDATE investment_positions SET quantity=-1 WHERE asset_id=93004 | negative quantity",
            "UPDATE investment_positions SET quantity=1 WHERE asset_id=93001 | redeemed position has remaining balance",
            "UPDATE investment_positions SET quantity=0 WHERE asset_id=93004 | open position has values without units",
            "UPDATE investment_positions SET current_value=-1 WHERE asset_id=93004 | negative current_value",
            "UPDATE investment_positions SET total_invested=3001 WHERE asset_id=93004 | total_invested differs",
            "UPDATE investment_positions SET redeemed_value=1 WHERE asset_id=93004 | redeemed_value differs",
            "DELETE FROM investment_positions WHERE asset_id=93004 | missing position",
            "UPDATE assets SET status='ACTIVE' WHERE id=93001 | status differs",
            "UPDATE investment_transactions SET amount=-1 WHERE id=97012 | negative movement amount"
    })
    void inconsistentSourceFailsBeforeAnyInvestmentDdlOrDataChange(String corruption, String diagnostic) throws Exception {
        try (var fixture = fixture()) {
            fixture.jdbc.update(corruption);
            var before = fixture.rawRows();
            var sequences = fixture.sequences();
            var error = assertThrows(Exception.class, () -> flyway(fixture, "26").migrate());
            var messages = new StringBuilder();
            for (Throwable cause = error; cause != null; cause = cause.getCause()) messages.append(cause.getMessage());
            assertTrue(messages.toString().contains(diagnostic), messages.toString());
            assertTrue(messages.toString().contains("asset_id="), messages.toString());
            assertTrue(messages.toString().contains("Explicitly correct"), messages.toString());
            assertEquals(before, fixture.rawRows());
            assertEquals(sequences, fixture.sequences());
            assertFalse(fixture.rawRows().get("investment_positions").getFirst().containsKey("remaining_cost"));
        }
    }

    @Test
    void enforcesMovementTypesSameAssetSellReferenceAndNonnegativePosition() throws Exception {
        try (var fixture = fixture()) {
            flyway(fixture, "26").migrate();
            fixture.jdbc.update("UPDATE investment_transactions SET related_sell_id=97002 WHERE id=97003");
            fixture.jdbc.update("UPDATE investment_transactions SET quantity=3 WHERE id=97012");
            fixture.jdbc.update("UPDATE investment_transactions SET quantity=1,cost_basis=1000 WHERE id=97002");
            for (String invalid : List.of(
                    "UPDATE investment_transactions SET related_sell_id=97012 WHERE id=97003",
                    "UPDATE investment_transactions SET related_sell_id=97001 WHERE id=97003",
                    "UPDATE investment_transactions SET related_sell_id=99999 WHERE id=97003",
                    "UPDATE investment_transactions SET related_sell_id=97002 WHERE id=97001",
                    "UPDATE investment_transactions SET related_sell_type='BUY' WHERE id=97003",
                    "UPDATE investment_transactions SET type='BUY' WHERE id=97002",
                    "UPDATE investment_transactions SET asset_id=93004 WHERE id=97002",
                    "UPDATE investment_transactions SET quantity=1 WHERE id=97003",
                    "UPDATE investment_transactions SET quantity=0 WHERE id=97012",
                    "UPDATE investment_transactions SET cost_basis=1 WHERE id=97012",
                    "UPDATE investment_transactions SET cost_basis=-1 WHERE id=97002",
                    "UPDATE investment_positions SET remaining_cost=-1 WHERE asset_id=93004",
                    "UPDATE investment_positions SET quantity=-1 WHERE asset_id=93004",
                    "UPDATE investment_positions SET current_value=-1 WHERE asset_id=93004")) {
                assertThrows(DataIntegrityViolationException.class, () -> fixture.jdbc.update(invalid), invalid);
            }
            // A SELL of another asset is rejected independently of its type.
            fixture.jdbc.update("INSERT INTO investment_transactions(id,asset_id,type,amount,transaction_date) VALUES(97013,93004,'SELL',1,DATE '2026-01-02')");
            assertThrows(DataIntegrityViolationException.class, () -> fixture.jdbc.update(
                    "UPDATE investment_transactions SET related_sell_id=97013 WHERE id=97003"));
        }
    }
}
