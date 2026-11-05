package io.github.ronaldobertolucci.unita.service.investment;

import io.github.ronaldobertolucci.unita.dto.investment.InvestmentSellDto;
import io.github.ronaldobertolucci.unita.dto.investment.InvestmentTransactionDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** Stable v1 comparison: monetary scale is insignificant, but dates, IDs and notes are exact. */
final class InvestmentSaleSnapshot {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<InvestmentTransactionDto>> RESULT = new TypeReference<>() {};

    private InvestmentSaleSnapshot() {}

    private record Command(int version, Long assetId, Long pocketId, Long categoryId,
                           String quantity, String grossAmount, String taxAmount, String date, String notes) {}

    static String command(Long assetId, InvestmentSellDto dto) {
        return JSON.writeValueAsString(new Command(1, assetId, dto.pocketId(), dto.categoryId(),
                dto.quantity().stripTrailingZeros().toPlainString(),
                dto.grossAmount().stripTrailingZeros().toPlainString(),
                dto.taxAmount().stripTrailingZeros().toPlainString(), dto.transactionDate().toString(), dto.notes()));
    }

    static String response(List<InvestmentTransactionDto> response) {
        return JSON.writeValueAsString(response);
    }

    static List<InvestmentTransactionDto> response(String snapshot) {
        return JSON.readValue(snapshot, RESULT);
    }
}
