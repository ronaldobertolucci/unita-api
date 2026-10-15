package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogFactory;

import java.sql.Connection;
import java.util.*;

/** Audits before DDL. Never rewrites registrations or invents a missing custodian. */
public class V25__private_institutions_and_custody extends BaseJavaMigration {
    private static final Log LOG = LogFactory.getLog(V25__private_institutions_and_custody.class);

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<String> problems = new ArrayList<>();
        Map<String, Long> registrations = new HashMap<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id,user_id,cnpj FROM legal_entities ORDER BY id")) {
            while (rows.next()) {
                long id = rows.getLong(1), user = rows.getLong(2);
                String original = rows.getString(3);
                // Frozen migration rule: only conventional mask characters may be removed.
                String normalized = original.replace(".", "").replace("/", "").replace("-", "").toUpperCase(Locale.ROOT);
                if (!normalized.matches("[A-Z0-9]{12}[0-9]{2}")) {
                    LOG.warn("INV-003 invalid historical CNPJ: legal_entity_id=" + id + ", user_id=" + user
                            + "; preserved unchanged; explicitly correct before editing this registration");
                } else if (!original.equals(normalized)) {
                    LOG.warn("INV-003 noncanonical historical CNPJ: legal_entity_id=" + id + ", user_id=" + user
                            + "; preserved unchanged; explicit correction required");
                }
                Long previous = registrations.putIfAbsent(user + ":" + normalized, id);
                if (previous != null) problems.add("CNPJ normalization collision: user_id=" + user
                        + ", legal_entity_ids=" + previous + "," + id);
            }
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT a.id,a.user_id,a.legal_entity_id,a.custodian_legal_entity_id,
                       issuer.id,issuer.user_id,custodian.id,custodian.user_id
                FROM assets a LEFT JOIN legal_entities issuer ON issuer.id=a.legal_entity_id
                LEFT JOIN legal_entities custodian ON custodian.id=a.custodian_legal_entity_id ORDER BY a.id
                """)) {
            while (rows.next()) {
                long asset = rows.getLong(1), user = rows.getLong(2);
                if (rows.getObject(5) == null || rows.getLong(6) != user)
                    problems.add("invalid issuer reference: asset_id=" + asset + ", user_id=" + user + ", legal_entity_id=" + rows.getLong(3));
                if (rows.getObject(4) != null && (rows.getObject(7) == null || rows.getLong(8) != user))
                    problems.add("invalid custodian reference: asset_id=" + asset + ", user_id=" + user + ", custodian_legal_entity_id=" + rows.getLong(4));
            }
        }
        if (!problems.isEmpty()) throw new FlywayException("INV-003 preflight failed. Explicitly correct the reported source records and retry; no rows were changed. " + String.join("; ", problems));

        // Discover actual UNIQUE constraint names by their columns, including Hibernate-generated names.
        Map<String, List<String>> constraints = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("""
                SELECT tc.constraint_name,kcu.column_name FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu ON tc.constraint_catalog=kcu.constraint_catalog
                  AND tc.constraint_schema=kcu.constraint_schema AND tc.constraint_name=kcu.constraint_name
                WHERE tc.table_schema=? AND tc.table_name='legal_entities' AND tc.constraint_type='UNIQUE'
                ORDER BY tc.constraint_name,kcu.ordinal_position
                """)) {
            statement.setString(1, connection.getSchema());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) constraints.computeIfAbsent(rows.getString(1), k -> new ArrayList<>()).add(rows.getString(2));
            }
        }
        try (var statement = connection.createStatement()) {
            for (var constraint : constraints.entrySet()) {
                if (constraint.getValue().equals(List.of("cnpj"))) {
                    LOG.info("INV-003 removing global CNPJ constraint " + constraint.getKey());
                    statement.execute("ALTER TABLE legal_entities DROP CONSTRAINT " + quote(constraint.getKey()));
                }
            }
            statement.execute("ALTER TABLE legal_entities ADD CONSTRAINT uk_legal_entities_user_cnpj UNIQUE(user_id,cnpj)");
            statement.execute("ALTER TABLE legal_entities ADD CONSTRAINT uk_legal_entities_id_user UNIQUE(id,user_id)");
            statement.execute("ALTER TABLE assets DROP CONSTRAINT fk_assets_custodian_legal_entity");
            statement.execute("ALTER TABLE assets ADD CONSTRAINT fk_assets_custodian_legal_entity FOREIGN KEY(custodian_legal_entity_id) REFERENCES legal_entities(id)");
            statement.execute("ALTER TABLE assets ADD CONSTRAINT fk_assets_legal_entity_owner FOREIGN KEY(legal_entity_id,user_id) REFERENCES legal_entities(id,user_id)");
            statement.execute("ALTER TABLE assets ADD CONSTRAINT fk_assets_custodian_owner FOREIGN KEY(custodian_legal_entity_id,user_id) REFERENCES legal_entities(id,user_id)");
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
