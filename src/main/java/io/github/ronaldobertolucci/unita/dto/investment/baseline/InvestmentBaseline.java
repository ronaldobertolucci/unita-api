package io.github.ronaldobertolucci.unita.dto.investment.baseline;

import java.time.Instant;
import java.util.List;

/**
 * Internal preservation contract, never exposed by an HTTP controller.
 * IDs and exact decimals are strings; null is an absent source value.
 * The checksum covers canonicalJson (UTF-8), excluding capture metadata.
 */
public record InvestmentBaseline(Instant capturedAt, String checksum, String canonicalJson, State state) {
    public record State(String formatVersion, List<UserState> users) {}

    public record UserState(String userId, Counts counts, List<Institution> institutions,
                            List<AssetState> assets, List<PocketState> pockets,
                            List<CashTransaction> cashTransactions, String displayedNetProfit,
                            List<Finding> findings) {}

    public record Counts(int assets, int positions, int investmentTransactions, int pockets,
                         int cashTransactions, int institutions, List<Count> assetsByCategory,
                         List<Count> assetsByStatus, List<Count> investmentTransactionsByType) {}

    public record Count(String value, long count) {}

    public record Institution(String id, String userId, String cnpj, String corporateName,
                              String tradeName, String stateRegistration) {}

    public record AssetState(String id, String userId, String legalEntityId, String custodianLegalEntityId,
                             String name, String category, String status, String liquidityType,
                             Position position, FixedIncome fixedIncomeDetails, Pension pensionDetails,
                             List<InvestmentTransaction> transactions, List<Count> transactionsByType,
                             String taxTotal, String displayedNetProfit) {}

    public record Position(String id, String assetId, String quantity, String averagePrice,
                           String totalInvested, String currentValue, String redeemedValue,
                           String lastValuationDate) {}

    public record FixedIncome(String assetId, String indexer, String annualRate,
                              String maturityDate, boolean taxFree) {}

    public record Pension(String assetId, String pensionType, String taxRegime) {}

    public record InvestmentTransaction(String id, String assetId, String pocketTransactionId,
                                        String type, String amount, String transactionDate, String notes) {}

    public record PocketState(String id, String userId, String type, String balance, int transactionCount) {}

    public record CashTransaction(String id, String pocketId, String amount, String direction,
                                  String transactionDate, String description, String categoryId) {}

    /** Findings describe source gaps; no SELL/TAX pairing or repairs are inferred. */
    public record Finding(String code, String assetId, String recordId, String referenceId) {}
}
