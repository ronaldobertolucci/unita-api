package io.github.ronaldobertolucci.unita.config;

import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;

@Configuration
public class LegacyInvestmentOpenApiConfig {
    @Bean
    public OpenApiCustomizer legacyInvestmentPositiveAmounts() {
        return api -> {
            if (api.getComponents() == null || api.getComponents().getSchemas() == null) {
                return;
            }
            Map<String, Schema> schemas = api.getComponents().getSchemas();
            requirePositive(schemas.get("InvestmentBuyDto"), "amount", "quantity");
            requirePositive(schemas.get("InvestmentYieldDto"), "amount");
            requirePositive(schemas.get("InvestmentSellDto"), "quantity", "grossAmount");
            var sell = api.getPaths().get("/assets/{id}/transactions/sell").getPost();
            var content = sell.getRequestBody().getContent().get("application/json");
            content.addExamples("partial", saleExample("Sell 1 of 3 units", 1, 1050, 10,
                    "f627f953-ffbd-4e4b-92c3-333ce72f22d5"));
            content.addExamples("final", saleExample("Sell the remaining 2 units after partial", 2, 2100, 20,
                    "e627f953-ffbd-4e4b-92c3-333ce72f22d5"));
            content.addExamples("retry", saleExample("Retry partial unchanged, even after final; returns the original response", 1, 1050, 10,
                    "f627f953-ffbd-4e4b-92c3-333ce72f22d5"));
        };
    }

    private static io.swagger.v3.oas.models.examples.Example saleExample(
            String summary, int quantity, int gross, int tax, String operationId) {
        return new io.swagger.v3.oas.models.examples.Example().summary(summary)
                .description("Start with 3 units, cost 3000 and valuation 3300. Replace asset path and pocket/category IDs with authorized records. Use new UUIDs for a new scenario.")
                .value(Map.of("quantity", quantity, "grossAmount", gross, "taxAmount", tax,
                        "operationId", operationId, "pocketId", 5, "categoryId", 41,
                        "transactionDate", "2026-11-04", "notes", "Documented sale"));
    }

    private static void requirePositive(Schema<?> schema, String... fields) {
        if (schema == null || schema.getProperties() == null) {
            return;
        }
        for (String field : fields) {
            Schema<?> property = schema.getProperties().get(field);
            // swagger-core does not carry @Positive into record component schemas;
            // OpenAPI 3.1 requires a numeric exclusiveMinimum, including the value zero.
            property.setExclusiveMinimumValue(BigDecimal.ZERO);
        }
    }
}
