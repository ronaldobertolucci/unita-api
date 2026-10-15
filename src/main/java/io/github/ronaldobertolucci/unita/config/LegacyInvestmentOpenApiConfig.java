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
        };
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
