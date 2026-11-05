package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/** Frozen legacy audit: never infer units, pair old taxes or rewrite historical values. */
public class V26__investment_remaining_cost_and_history extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<String> problems = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT a.id,a.status,p.id AS position_id,p.quantity,p.average_price,
                       p.total_invested,p.current_value,p.redeemed_value,
                       COALESCE(t.buys,0) AS buys,COALESCE(t.receipts,0) AS receipts,
                       COALESCE(t.sells,0) AS sells
                FROM assets a LEFT JOIN investment_positions p ON p.asset_id=a.id
                LEFT JOIN (
                    SELECT asset_id,
                           SUM(CASE WHEN type='BUY' THEN amount ELSE 0 END) AS buys,
                           SUM(CASE WHEN type IN ('SELL','YIELD') THEN amount
                                    WHEN type='TAX' THEN -amount ELSE 0 END) AS receipts,
                           SUM(CASE WHEN type='SELL' THEN 1 ELSE 0 END) AS sells
                    FROM investment_transactions GROUP BY asset_id
                ) t ON t.asset_id=a.id ORDER BY a.id
                """)) {
            while (rows.next()) {
                String prefix = "asset_id=" + rows.getLong("id") + ": ";
                if (rows.getObject("position_id") == null) {
                    problems.add(prefix + "missing position");
                    continue;
                }
                for (String field : List.of("quantity", "average_price", "total_invested", "current_value", "redeemed_value")) {
                    if (rows.getBigDecimal(field).signum() < 0) problems.add(prefix + "negative " + field);
                }
                boolean redeemed = "REDEEMED".equals(rows.getString("status"));
                if (redeemed && (rows.getBigDecimal("quantity").signum() != 0
                        || rows.getBigDecimal("average_price").signum() != 0
                        || rows.getBigDecimal("current_value").signum() != 0)) {
                    problems.add(prefix + "redeemed position has remaining balance");
                }
                if (!redeemed && rows.getBigDecimal("quantity").signum() == 0
                        && (rows.getBigDecimal("total_invested").signum() != 0
                        || rows.getBigDecimal("current_value").signum() != 0
                        || rows.getBigDecimal("average_price").signum() != 0)) {
                    problems.add(prefix + "open position has values without units");
                }
                if (rows.getBigDecimal("total_invested").compareTo(rows.getBigDecimal("buys")) != 0)
                    problems.add(prefix + "total_invested differs from BUY history");
                if (rows.getBigDecimal("redeemed_value").compareTo(rows.getBigDecimal("receipts")) != 0)
                    problems.add(prefix + "redeemed_value differs from SELL + YIELD - TAX history");
                if (redeemed != (rows.getLong("sells") > 0))
                    problems.add(prefix + "status differs from legacy full-sale history");
            }
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT id,asset_id FROM investment_transactions WHERE amount < 0 ORDER BY id")) {
            while (rows.next()) problems.add("asset_id=" + rows.getLong("asset_id")
                    + ": negative movement amount, transaction_id=" + rows.getLong("id"));
        }
        if (!problems.isEmpty()) throw new FlywayException("BE-01 preflight failed. Explicitly correct the reported source records and retry; "
                + "no investment rows or columns were changed. " + String.join("; ", problems));

        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE investment_positions ADD COLUMN remaining_cost DECIMAL(18,8)");
            statement.execute("""
                    UPDATE investment_positions SET remaining_cost = CASE
                        WHEN EXISTS (SELECT 1 FROM assets a WHERE a.id=asset_id AND a.status='REDEEMED')
                        THEN 0 ELSE total_invested END
                    """);
            statement.execute("ALTER TABLE investment_positions ALTER COLUMN remaining_cost SET DEFAULT 0");
            statement.execute("ALTER TABLE investment_positions ALTER COLUMN remaining_cost SET NOT NULL");
            statement.execute("""
                    ALTER TABLE investment_positions ADD CONSTRAINT ck_investment_position_nonnegative
                    CHECK (quantity>=0 AND average_price>=0 AND total_invested>=0
                           AND current_value>=0 AND redeemed_value>=0 AND remaining_cost>=0)
                    """);
            statement.execute("ALTER TABLE investment_transactions ADD COLUMN quantity DECIMAL(18,8)");
            statement.execute("ALTER TABLE investment_transactions ADD COLUMN cost_basis DECIMAL(18,8)");
            statement.execute("ALTER TABLE investment_transactions ADD COLUMN related_sell_id BIGINT");
            // A constant discriminator permits a declarative same-asset, SELL-only FK on both PostgreSQL and H2.
            statement.execute("ALTER TABLE investment_transactions ADD COLUMN related_sell_type VARCHAR(5) DEFAULT 'SELL' NOT NULL");
            statement.execute("""
                    ALTER TABLE investment_transactions ADD CONSTRAINT ck_investment_history_fields
                    CHECK (related_sell_type='SELL'
                        AND (quantity IS NULL OR (type IN ('BUY','SELL') AND quantity>0))
                        AND (cost_basis IS NULL OR (type='SELL' AND cost_basis>=0))
                        AND (related_sell_id IS NULL OR type='TAX'))
                    """);
            statement.execute("""
                    ALTER TABLE investment_transactions ADD CONSTRAINT uk_investment_movement_asset_type
                    UNIQUE (id,asset_id,type)
                    """);
            statement.execute("""
                    ALTER TABLE investment_transactions ADD CONSTRAINT fk_investment_tax_sell
                    FOREIGN KEY (related_sell_id,asset_id,related_sell_type)
                    REFERENCES investment_transactions(id,asset_id,type)
                    """);
            statement.execute("CREATE INDEX idx_investment_related_sell ON investment_transactions(related_sell_id)");
        }
    }
}
