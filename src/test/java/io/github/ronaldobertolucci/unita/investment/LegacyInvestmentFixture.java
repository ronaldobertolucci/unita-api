package io.github.ronaldobertolucci.unita.investment;

import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Disposable legacy schema; pinned to the source version, independent of future migrations. */
public final class LegacyInvestmentFixture implements AutoCloseable {
    public final String schema = "inv001_" + UUID.randomUUID().toString().replace("-", "");
    public final DataSource dataSource;
    public final JdbcTemplate jdbc;
    private final Connection keeper;

    public LegacyInvestmentFixture(String url, String username, String password) throws SQLException {
        keeper = DriverManager.getConnection(url, username, password);
        // Keep the migration connection alive for H2 CHECK comparators, as in RefundMigrationTest.
        Flyway.configure().dataSource(new SingleConnectionDataSource(keeper, true))
                .schemas(schema).defaultSchema(schema).target("24").load().migrate();
        keeper.setSchema(schema);
        ScriptUtils.executeSqlScript(keeper, new ClassPathResource("investments/legacy-baseline.sql"));
        dataSource = new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                var connection = DriverManager.getConnection(url, username, password);
                connection.setSchema(schema);
                return connection;
            }
            @Override public Connection getConnection(String user, String pass) throws SQLException {
                throw new SQLFeatureNotSupportedException();
            }
        };
        jdbc = new JdbcTemplate(dataSource);
    }

    public static LegacyInvestmentFixture h2() throws SQLException {
        return new LegacyInvestmentFixture("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH", "sa", "");
    }

    public Map<String, List<Map<String, Object>>> rawRows() {
        var rows = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("users", "legal_entities", "assets", "investment_positions",
                "investment_transactions", "pockets", "transactions"))
            rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        for (String table : List.of("fixed_income_details", "pension_details"))
            rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY asset_id"));
        return rows;
    }

    public List<Map<String, Object>> sequences() throws SQLException {
        if (keeper.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
            return jdbc.queryForList("""
                    SELECT sequencename,start_value,increment_by,last_value
                    FROM pg_sequences WHERE schemaname=? ORDER BY sequencename
                    """, schema);
        }
        return jdbc.queryForList("""
                SELECT table_name,column_name,identity_base FROM information_schema.columns
                WHERE table_schema=? AND is_identity='YES' ORDER BY table_name,column_name
                """, schema);
    }

    @Override public void close() throws SQLException {
        try (var statement = keeper.createStatement()) {
            // Only the randomly generated schema created by this fixture is removed.
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        } finally {
            keeper.close();
        }
    }
}
