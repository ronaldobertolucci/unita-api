package io.github.ronaldobertolucci.unita.integration;

import io.github.ronaldobertolucci.unita.investment.LegacyInvestmentFixture;
import io.github.ronaldobertolucci.unita.service.investment.InvestmentBaselineCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Run ONLY against a disposable test PostgreSQL:
 * -Dinv001.postgres.url=jdbc:postgresql://localhost:PORT/inv001_test
 * -Dinv001.postgres.username=... -Dinv001.postgres.password=...
 * Creates/removes its own random schema; never reads the application's default schema.
 */
@EnabledIfSystemProperty(named = "inv001.postgres.url", matches = "jdbc:postgresql:.*")
class InvestmentBaselinePostgreSQLTest {
    @Test
    void readsRealLegacyPostgreSQLWithoutChangingRowsSequencesOrIndividualCashBalances() throws Exception {
        try (var fixture = new LegacyInvestmentFixture(System.getProperty("inv001.postgres.url"),
                System.getProperty("inv001.postgres.username"), System.getProperty("inv001.postgres.password"))) {
            var rows = fixture.rawRows();
            var sequences = fixture.sequences();
            var collector = new InvestmentBaselineCollector(fixture.dataSource);
            var first = collector.collect();
            var second = collector.collect();
            assertEquals(first.state(), second.state());
            assertEquals(first.canonicalJson(), second.canonicalJson());
            assertEquals(first.checksum(), second.checksum());
            assertEquals(rows, fixture.rawRows());
            assertEquals(sequences, fixture.sequences());
            assertFalse(sequences.isEmpty());
            assertEquals(2, first.state().users().size());
            assertEquals("550.87654321", first.state().users().getFirst().assets().get(1).position().currentValue());
            assertEquals("1612.46", first.state().users().getFirst().pockets().getFirst().balance());
            assertEquals("-50", first.state().users().getFirst().pockets().get(1).balance());
            assertEquals("0", first.state().users().getFirst().pockets().getLast().balance());
            // Same source fixture must have the same canonical representation on both engines.
            try (var h2 = LegacyInvestmentFixture.h2()) {
                assertEquals(first.canonicalJson(), new InvestmentBaselineCollector(h2.dataSource).collect().canonicalJson());
            }
        }
    }
}
