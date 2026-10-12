package io.github.ronaldobertolucci.unita.security;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

class EmailVerificationMigrationTest {
    @Test void migrationPreservesLegacyAccessAndNeverEnablesAmbiguousAccounts() {
        String url = "jdbc:h2:mem:migration_" + java.util.UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var source = new DriverManagerDataSource(url, "sa", "");
        assertMigration(source);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "AUTH_TEST_POSTGRES_URL", matches = ".+")
    void migrationPreservesLegacyAccountsOnPostgreSQL() {
        String schema = "migration_" + java.util.UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("AUTH_TEST_POSTGRES_URL");
        var source = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                "auth_test", "auth_test");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + schema);
        try { assertMigration(source); }
        finally { jdbc.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }

    private void assertMigration(DriverManagerDataSource source) {
        var jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target("20").load().migrate();
        // enabled+pending/absent and disabled+absent/pending/expired/consumed cases.
        for (int id = 1; id <= 6; id++) {
            jdbc.update("INSERT INTO users(id, first_name, last_name, email, date_of_birth, password, enabled)"
                    + " VALUES (?, 'Legacy', 'Example', ?, DATE '1990-01-01', 'hash', ?)",
                    1000 + id, "legacy" + id + "@example.com", id <= 2);
        }
        for (int id : new int[]{2, 4, 5, 6}) {
            jdbc.update("INSERT INTO email_verification_tokens(token, user_id, expiry_date, used)"
                    + " VALUES (?, ?, ?, ?)", "legacy-token-" + id, 1000 + id,
                    java.sql.Timestamp.valueOf(id >= 5 ? "2000-01-01 00:00:00" : "2099-01-01 00:00:00"), id == 6);
        }
        var flyway = Flyway.configure().dataSource(source).load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        for (int id = 1; id <= 6; id++) {
            assertEquals(id <= 2, jdbc.queryForObject("SELECT enabled FROM users WHERE id = ?", Boolean.class, 1000 + id));
            assertEquals(id <= 2 || id == 6,
                    jdbc.queryForObject("SELECT email_verified FROM users WHERE id = ?", Boolean.class, 1000 + id));
        }
        jdbc.update("INSERT INTO users(first_name, last_name, email, date_of_birth, password)"
                + " VALUES ('New', 'Example', 'new@example.com', DATE '1990-01-01', 'hash')");
        assertTrue(jdbc.queryForObject("SELECT enabled FROM users WHERE email = 'new@example.com'", Boolean.class));
        assertFalse(jdbc.queryForObject("SELECT email_verified FROM users WHERE email = 'new@example.com'", Boolean.class));
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
    }
}
