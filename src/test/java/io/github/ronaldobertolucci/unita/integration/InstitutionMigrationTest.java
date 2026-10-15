package io.github.ronaldobertolucci.unita.integration;

import io.github.ronaldobertolucci.unita.investment.LegacyInvestmentFixture;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;


import static org.junit.jupiter.api.Assertions.*;

/** Optional PostgreSQL run uses only random disposable schemas, never the default application schema. */
@ExtendWith(OutputCaptureExtension.class)
class InstitutionMigrationTest {
    private LegacyInvestmentFixture fixture() throws Exception {
        String url = System.getProperty("inv003.postgres.url");
        return url == null ? LegacyInvestmentFixture.h2() : new LegacyInvestmentFixture(url,
                System.getProperty("inv003.postgres.username"), System.getProperty("inv003.postgres.password"));
    }

    private Flyway upgrade(LegacyInvestmentFixture fixture) {
        return Flyway.configure().dataSource(fixture.dataSource).schemas(fixture.schema)
                .defaultSchema(fixture.schema).target("25").load();
    }

    private void correctSyntheticReferences(LegacyInvestmentFixture fixture) {
        // Explicit fixture corrections, never automatic repair by the migration.
        fixture.jdbc.update("UPDATE assets SET custodian_legal_entity_id=92002 WHERE id IN (93002,93003)");
    }

    @Test
    void upgrades24DiscoversGeneratedConstraintAndPreservesAllRowsAndSequences(CapturedOutput output) throws Exception {
        try (var fixture = fixture()) {
            correctSyntheticReferences(fixture);
            fixture.jdbc.update("UPDATE legal_entities SET cnpj='22345678000190' WHERE id=92003");
            fixture.jdbc.execute("ALTER TABLE legal_entities ADD CONSTRAINT \"UK_opaque_hibernate_731\" UNIQUE(cnpj)");
            fixture.jdbc.update("INSERT INTO legal_entities(id,user_id,cnpj,corporate_name) VALUES(92004,91001,'invalid','Historical invalid')");
            fixture.jdbc.update("INSERT INTO legal_entities(id,user_id,cnpj,corporate_name) VALUES(92005,91001,'00000000e08g12','Historical lowercase')");
            var before = fixture.rawRows();
            var sequences = fixture.sequences();
            var flyway = upgrade(fixture);
            assertEquals(1, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEquals(before, fixture.rawRows());
            assertEquals(sequences, fixture.sequences());
            assertTrue(output.toString().contains("invalid historical CNPJ: legal_entity_id=92004"));
            assertTrue(output.toString().contains("noncanonical historical CNPJ: legal_entity_id=92005"));
            fixture.jdbc.update("INSERT INTO legal_entities(id,user_id,cnpj,corporate_name) VALUES(92006,91002,'12345678000190','Same institution other owner')");
            var duplicate = assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> fixture.jdbc.update(
                    "INSERT INTO legal_entities(id,user_id,cnpj,corporate_name) VALUES(92007,91001,'12345678000190','Duplicate owner')"));
            var deletion = assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> fixture.jdbc.update("DELETE FROM legal_entities WHERE id=92002"));
            // Verify real driver exceptions too: only the known constraints become business errors.
            var handler = new io.github.ronaldobertolucci.unita.exception.GlobalExceptionHandler();
            var request = new org.springframework.mock.web.MockHttpServletRequest("DELETE", "/api/legal-entities/92002");
            assertEquals(400, handler.handleFinancialIntegrity(duplicate, request).getStatusCode().value());
            var conflict = handler.handleFinancialIntegrity(deletion, request);
            assertEquals(409, conflict.getStatusCode().value());
            assertEquals("Legal entity is in use and cannot be deleted", conflict.getBody().message());
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> fixture.jdbc.update("UPDATE assets SET custodian_legal_entity_id=92999 WHERE id=93001"));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> fixture.jdbc.update("UPDATE assets SET custodian_legal_entity_id=92003 WHERE id=93001"));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> fixture.jdbc.update("UPDATE assets SET legal_entity_id=92003 WHERE id=93001"));
            assertEquals(before.get("investment_transactions"), fixture.rawRows().get("investment_transactions"));
        }
    }

    @Test
    void orphanAndForeignOwnerFailWithActionableIdsWithoutChangingData() throws Exception {
        try (var fixture = fixture()) {
            var before = fixture.rawRows();
            var exception = assertThrows(Exception.class, () -> upgrade(fixture).migrate());
            String messages = causes(exception);
            assertTrue(messages.contains("asset_id=93002"), messages);
            assertTrue(messages.contains("custodian_legal_entity_id=92999"), messages);
            assertTrue(messages.contains("asset_id=93003"), messages);
            assertTrue(messages.contains("Explicitly correct"), messages);
            assertEquals(before, fixture.rawRows());
        }
    }

    @Test
    void canonicalCollisionRequiresExplicitCorrectionWithoutRewritingHistoricalText() throws Exception {
        try (var fixture = fixture()) {
            correctSyntheticReferences(fixture);
            fixture.jdbc.update("INSERT INTO legal_entities(id,user_id,cnpj,corporate_name) VALUES(92004,91001,'00000000e08g12','Lowercase')");
            fixture.jdbc.update("INSERT INTO legal_entities(id,user_id,cnpj,corporate_name) VALUES(92005,91001,'00000000E08G12','Uppercase')");
            var before = fixture.rawRows();
            String messages = causes(assertThrows(Exception.class, () -> upgrade(fixture).migrate()));
            assertTrue(messages.contains("CNPJ normalization collision: user_id=91001, legal_entity_ids=92004,92005"), messages);
            assertEquals(before, fixture.rawRows());
        }
    }

    private String causes(Throwable error) {
        StringBuilder result = new StringBuilder();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) result.append(cause.getMessage()).append('\n');
        return result.toString();
    }
}
