package io.github.ronaldobertolucci.unita.integration;

import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.TransactionRepository;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Characterizes the HTTP contract without imposing the legacy dashboard tax bug. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/investments/institution-validated-baseline.sql")
class InvestmentLegacyContractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TransactionRepository cash;

    private User owner() { return users.findById(91001L).orElseThrow(); }

    @Test
    void legacyFixedIncomeAndPensionResponsesKeepNumericValuesAndSinglePosition() throws Exception {
        mvc.perform(get("/api/assets/93001").contextPath("/api").with(user(owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.category").value("RENDA_FIXA"))
                .andExpect(jsonPath("$.status").value("REDEEMED"))
                .andExpect(jsonPath("$.legalEntity.id").value(92001))
                .andExpect(jsonPath("$.custodianLegalEntity.id").value(92002))
                .andExpect(jsonPath("$.position.quantity").value(0))
                .andExpect(jsonPath("$.position.totalInvested").value(1000))
                .andExpect(jsonPath("$.position.redeemedValue").value(1085))
                .andExpect(jsonPath("$.position.lastValuationDate").value("2026-10-12"))
                .andExpect(jsonPath("$.fixedIncomeDetails.indexer").value("PREFIXADO"))
                .andExpect(jsonPath("$.pensionDetails").isEmpty());
        mvc.perform(get("/api/assets/93003").contextPath("/api").with(user(owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("PREVIDENCIA"))
                .andExpect(jsonPath("$.status").value("MATURED"))
                .andExpect(jsonPath("$.position.currentValue").value(220))
                .andExpect(jsonPath("$.pensionDetails.pensionType").value("VGBL"))
                .andExpect(jsonPath("$.pensionDetails.taxRegime").value("REGRESSIVO"));
    }

    @Test
    void historyKeepsOriginalTypesAmountsDatesNotesAndNullableCashReferences() throws Exception {
        var response = mvc.perform(get("/api/assets/93002/transactions").contextPath("/api").with(user(owner())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
                .andReturn().getResponse().getContentAsString();
        var movements = JsonMapper.builder().build().readTree(response);
        var detached = movements.findValues("id");
        assertEquals(6, detached.size());
        for (var movement : movements) {
            assertTrue(movement.get("id").isNumber());
            assertTrue(movement.get("amount").isNumber());
            if (movement.get("id").asLong() == 97007) {
                assertTrue(movement.get("pocketTransactionId").isNull());
                assertEquals("2026-10-12", movement.get("transactionDate").asString());
                assertEquals("YIELD", movement.get("type").asString());
                assertTrue(movement.get("notes").asString().contains("\"á\""));
                assertEquals(0, new BigDecimal("15.12345678").compareTo(movement.get("amount").decimalValue()));
            }
        }
    }

    @Test
    void existingCommandPayloadsPreserveBuyYieldAndFullSellCashSemantics() throws Exception {
        var response = mvc.perform(post("/api/assets/fixed-income").contextPath("/api").with(user(owner()))
                .contentType("application/json").content("""
                        {"name":"Contract contribution","legalEntityId":92001,"indexer":"CDI",
                         "annualRate":1.1,"maturityDate":"2027-10-01","taxFree":false,"liquidityType":"DIARIA"}
                        """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = JsonMapper.builder().build().readTree(response).get("id").asLong();
        String path = "/api/assets/" + id;
        mvc.perform(post(path + "/transactions/buy").contextPath("/api").with(user(owner()))
                .contentType("application/json").content("""
                        {"amount":100,"quantity":2,"transactionDate":"2026-10-12",
                         "pocketId":94003,"categoryId":39,"notes":"Contract buy"}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("BUY"))
                .andExpect(jsonPath("$.amount").value(200)).andExpect(jsonPath("$.pocketTransactionId").isNumber());
        mvc.perform(post(path + "/transactions/yield").contextPath("/api").with(user(owner()))
                .contentType("application/json").content("""
                        {"amount":10,"transactionDate":"2026-10-12","pocketId":94003,"categoryId":40,"notes":"Contract yield"}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("YIELD"));
        mvc.perform(get(path).contextPath("/api").with(user(owner())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.position.quantity").value(2))
                .andExpect(jsonPath("$.position.currentValue").value(190))
                .andExpect(jsonPath("$.position.redeemedValue").value(10));
        mvc.perform(post(path + "/transactions/sell").contextPath("/api").with(user(owner()))
                .contentType("application/json").content("""
                        {"grossAmount":220,"taxAmount":15,"transactionDate":"2026-10-13",
                         "pocketId":94003,"categoryId":41,"notes":"Contract full sell"}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("SELL")).andExpect(jsonPath("$[0].amount").value(220))
                .andExpect(jsonPath("$[1].type").value("TAX")).andExpect(jsonPath("$[1].amount").value(15))
                .andExpect(jsonPath("$[1].pocketTransactionId").isEmpty());
        mvc.perform(get(path).contextPath("/api").with(user(owner())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REDEEMED"))
                .andExpect(jsonPath("$.position.quantity").value(0))
                .andExpect(jsonPath("$.position.totalInvested").value(200))
                .andExpect(jsonPath("$.position.redeemedValue").value(215));
        assertEquals(0, new BigDecimal("15").compareTo(cash.calculateBalanceByPocketId(94003L)));
    }

    @Test
    void personalHistoryAndDetailRequireAuthenticationAndHideOtherOwners() throws Exception {
        mvc.perform(get("/api/assets/93001").contextPath("/api")).andExpect(status().isUnauthorized());
        for (String path : new String[]{"/api/assets/93005", "/api/assets/93005/transactions"})
            mvc.perform(get(path).contextPath("/api").with(user(owner())))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void openApiMaterializesLegacyContractsAndExcludesInternalAllUserBaseline() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/assets/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/assets/{id}/transactions'].get.responses['401']").exists())
                .andExpect(jsonPath("$.components.schemas.AssetDetailDto.properties.id.type").value("integer"))
                .andExpect(jsonPath("$.components.schemas.AssetDetailDto.properties.position").exists())
                .andExpect(jsonPath("$.components.schemas.InvestmentPositionDto.properties.totalInvested.type").value("number"))
                .andExpect(jsonPath("$.components.schemas.InvestmentBuyDto.properties.amount.description")
                        .value("Legacy unit price, multiplied by quantity to obtain the contribution"))
                .andExpect(jsonPath("$.components.schemas.InvestmentTransactionDto.properties.pocketTransactionId.description")
                        .value("Original cash transaction reference; may be absent or shared by legacy movements"))
                .andExpect(jsonPath("$.paths['/investments/baseline']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.InvestmentBaseline").doesNotExist());
    }
}
