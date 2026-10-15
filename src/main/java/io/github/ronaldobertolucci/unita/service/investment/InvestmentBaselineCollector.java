package io.github.ronaldobertolucci.unita.service.investment;

import io.github.ronaldobertolucci.unita.dto.investment.baseline.InvestmentBaseline;
import io.github.ronaldobertolucci.unita.dto.investment.baseline.InvestmentBaseline.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Local audit/migration helper for the legacy investment schema (through V24).
 * Uses raw rows so a broken custodial FK cannot hide an asset or trigger JPA hydration.
 * A dedicated read-only, repeatable-read transaction cannot flush caller entities or
 * observe different committed versions between queries. It always rolls back.
 * No credentials or user personal data are collected. No public all-user API exists.
 */
@Service
public class InvestmentBaselineCollector {
    private final DataSource dataSource;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    public InvestmentBaselineCollector(DataSource dataSource) {
        this(dataSource, Clock.systemUTC());
    }

    public InvestmentBaselineCollector(DataSource dataSource, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.clock = Objects.requireNonNull(clock);
    }

    public InvestmentBaseline collect() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                State state = read(jdbc);
                String canonical = json.writeValueAsString(state);
                return new InvestmentBaseline(clock.instant(), sha256(canonical), canonical, state);
            } finally {
                connection.rollback();
            }
        }
    }

    private State read(JdbcTemplate jdbc) {
        List<String> users = jdbc.query("SELECT id FROM users ORDER BY id", (r, n) -> id(r, "id"));
        var institutions = jdbc.query("""
                SELECT id,user_id,cnpj,corporate_name,trade_name,state_registration
                FROM legal_entities ORDER BY id
                """, (r, n) -> new Institution(id(r, "id"), id(r, "user_id"), r.getString("cnpj"),
                r.getString("corporate_name"), r.getString("trade_name"), r.getString("state_registration")));
        var positions = jdbc.query("SELECT * FROM investment_positions ORDER BY id", (r, n) ->
                new Position(id(r, "id"), id(r, "asset_id"), decimal(r, "quantity"), decimal(r, "average_price"),
                        decimal(r, "total_invested"), decimal(r, "current_value"), decimal(r, "redeemed_value"),
                        r.getString("last_valuation_date")));
        var fixed = jdbc.query("SELECT * FROM fixed_income_details ORDER BY asset_id", (r, n) ->
                new FixedIncome(id(r, "asset_id"), r.getString("indexer"), decimal(r, "annual_rate"),
                        r.getString("maturity_date"), r.getBoolean("is_tax_free")));
        var pensions = jdbc.query("SELECT * FROM pension_details ORDER BY asset_id", (r, n) ->
                new Pension(id(r, "asset_id"), r.getString("pension_type"), r.getString("tax_regime")));
        // Economic date + ID resolves same-day ties without pairing SELL and TAX.
        var investments = jdbc.query("SELECT * FROM investment_transactions ORDER BY transaction_date,id", (r, n) ->
                new InvestmentTransaction(id(r, "id"), id(r, "asset_id"), id(r, "transaction_id"), r.getString("type"),
                        decimal(r, "amount"), r.getString("transaction_date"), r.getString("notes")));
        var cash = jdbc.query("SELECT * FROM transactions ORDER BY transaction_date,id", (r, n) ->
                new CashTransaction(id(r, "id"), id(r, "pocket_id"), decimal(r, "amount"), r.getString("direction"),
                        r.getString("transaction_date"), r.getString("description"), id(r, "category_id")));
        Map<String, Position> positionByAsset = index(positions, Position::assetId);
        Map<String, FixedIncome> fixedByAsset = index(fixed, FixedIncome::assetId);
        Map<String, Pension> pensionByAsset = index(pensions, Pension::assetId);
        var investmentsByAsset = investments.stream().collect(Collectors.groupingBy(InvestmentTransaction::assetId));
        var cashByPocket = cash.stream().collect(Collectors.groupingBy(CashTransaction::pocketId));
        var assets = jdbc.query("SELECT * FROM assets ORDER BY id", (r, n) -> {
            String assetId = id(r, "id");
            var movements = investmentsByAsset.getOrDefault(assetId, List.of());
            Position position = positionByAsset.get(assetId);
            BigDecimal tax = movements.stream().filter(t -> "TAX".equals(t.type()))
                    .map(t -> new BigDecimal(t.amount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            // Observe the existing dashboard formula. This is NOT the correct economic
            // result contract: INV-002 owns the correction of the double tax subtraction.
            BigDecimal displayed = "REDEEMED".equals(r.getString("status")) && position != null
                    ? new BigDecimal(position.redeemedValue()).subtract(new BigDecimal(position.totalInvested())).subtract(tax)
                    : BigDecimal.ZERO;
            return new AssetState(assetId, id(r, "user_id"), id(r, "legal_entity_id"), id(r, "custodian_legal_entity_id"),
                    r.getString("name"), r.getString("category"), r.getString("status"), r.getString("liquidity_type"),
                    position, fixedByAsset.get(assetId), pensionByAsset.get(assetId), movements,
                    counts(movements, InvestmentTransaction::type), exact(tax), exact(displayed));
        });
        var pockets = jdbc.query("SELECT * FROM pockets ORDER BY id", (r, n) -> {
            String pocketId = id(r, "id");
            var transactions = cashByPocket.getOrDefault(pocketId, List.of());
            var balance = transactions.stream().map(t -> "INCOME".equals(t.direction())
                    ? new BigDecimal(t.amount()) : new BigDecimal(t.amount()).negate())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new PocketState(pocketId, id(r, "user_id"), r.getString("dtype"), exact(balance), transactions.size());
        });
        Map<String, Institution> institutionById = index(institutions, Institution::id);
        Map<String, CashTransaction> cashById = index(cash, CashTransaction::id);
        Map<String, PocketState> pocketById = index(pockets, PocketState::id);
        var cashReferences = investments.stream().filter(t -> t.pocketTransactionId() != null)
                .collect(Collectors.groupingBy(InvestmentTransaction::pocketTransactionId, Collectors.counting()));

        return new State("1", users.stream().map(userId -> {
            var ownAssets = assets.stream().filter(a -> userId.equals(a.userId())).toList();
            var ownPockets = pockets.stream().filter(p -> userId.equals(p.userId())).toList();
            var ownInstitutions = institutions.stream().filter(i -> userId.equals(i.userId())).toList();
            var pocketIds = ownPockets.stream().map(PocketState::id).collect(Collectors.toSet());
            var ownCash = cash.stream().filter(t -> pocketIds.contains(t.pocketId())).toList();
            var ownInvestments = ownAssets.stream().flatMap(a -> a.transactions().stream()).toList();
            var counts = new Counts(ownAssets.size(), (int) ownAssets.stream().filter(a -> a.position() != null).count(),
                    ownInvestments.size(), ownPockets.size(), ownCash.size(), ownInstitutions.size(),
                    counts(ownAssets, AssetState::category), counts(ownAssets, AssetState::status),
                    counts(ownInvestments, InvestmentTransaction::type));
            BigDecimal displayed = ownAssets.stream().map(a -> new BigDecimal(a.displayedNetProfit()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Finding> findings = new ArrayList<>();
            for (var asset : ownAssets) {
                checkInstitution(findings, asset, asset.legalEntityId(), "PRIMARY", institutionById);
                checkInstitution(findings, asset, asset.custodianLegalEntityId(), "CUSTODIAN", institutionById);
                if (asset.position() == null) findings.add(new Finding("MISSING_POSITION", asset.id(), asset.id(), null));
                for (var t : asset.transactions()) {
                    String reference = t.pocketTransactionId();
                    if (reference == null) {
                        findings.add(new Finding("MISSING_CASH_REFERENCE", asset.id(), t.id(), null));
                    } else {
                        if (cashReferences.get(reference) > 1)
                            findings.add(new Finding("SHARED_CASH_REFERENCE", asset.id(), t.id(), reference));
                        var transaction = cashById.get(reference);
                        if (transaction == null) {
                            findings.add(new Finding("INVALID_CASH_REFERENCE", asset.id(), t.id(), reference));
                        } else if (!userId.equals(pocketById.get(transaction.pocketId()).userId())) {
                            findings.add(new Finding("FOREIGN_CASH_REFERENCE", asset.id(), t.id(), reference));
                        }
                    }
                }
            }
            return new UserState(userId, counts, ownInstitutions, ownAssets, ownPockets, ownCash,
                    exact(displayed), List.copyOf(findings));
        }).toList());
    }

    private static void checkInstitution(List<Finding> findings, AssetState asset, String reference,
                                          String role, Map<String, Institution> institutions) {
        if (reference == null) return;
        var institution = institutions.get(reference);
        if (institution == null) findings.add(new Finding("INVALID_" + role + "_INSTITUTION", asset.id(), asset.id(), reference));
        else if (!asset.userId().equals(institution.userId()))
            findings.add(new Finding("FOREIGN_" + role + "_INSTITUTION", asset.id(), asset.id(), reference));
    }

    private static <T> Map<String, T> index(List<T> rows, Function<T, String> key) {
        return rows.stream().collect(Collectors.toMap(key, Function.identity()));
    }

    private static <T> List<Count> counts(List<T> rows, Function<T, String> key) {
        return rows.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> new Count(e.getKey(), e.getValue())).toList();
    }

    private static String id(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : Long.toString(value);
    }

    private static String decimal(ResultSet row, String column) throws SQLException {
        BigDecimal value = row.getBigDecimal(column);
        return value == null ? null : exact(value);
    }

    private static String exact(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
