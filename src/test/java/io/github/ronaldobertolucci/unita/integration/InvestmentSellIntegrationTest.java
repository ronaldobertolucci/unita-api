package io.github.ronaldobertolucci.unita.integration;

import io.github.ronaldobertolucci.unita.model.finance.*;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.model.pocket.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InvestmentSellIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired LegalEntityRepository legalEntities;
    @Autowired AssetRepository assets;
    @Autowired PocketRepository pockets;
    @Autowired InvestmentPositionRepository positions;
    @Autowired InvestmentTransactionRepository investments;
    @Autowired TransactionRepository transactions;
    private User owner;
    private Asset asset;
    private Cash pocket;

    private void setPosition(String quantity, String cost, String value, AssetStatus status) {
        var position = positions.findByAssetId(asset.getId()).orElseThrow();
        position.setQuantity(new BigDecimal(quantity));
        position.setRemainingCost(new BigDecimal(cost));
        position.setTotalInvested(new BigDecimal(cost));
        position.setAveragePrice(new BigDecimal(cost).divide(new BigDecimal(quantity), 8, java.math.RoundingMode.HALF_UP));
        position.setCurrentValue(new BigDecimal(value));
        position.setRedeemedValue(BigDecimal.ZERO);
        var managedAsset = assets.findById(asset.getId()).orElseThrow();
        managedAsset.setStatus(status);
        investments.findAllByAssetIdOrderByTransactionDateDesc(asset.getId()).getFirst().setAmount(new BigDecimal(cost));
        em.flush();
        em.clear();
    }

    private tools.jackson.databind.JsonNode sell(String quantity, String gross, String tax) throws Exception {
        var response = mvc.perform(post(path()).contextPath("/api").with(user(owner))
                        .contentType("application/json").content(body(gross, tax).replace("\"quantity\":2", "\"quantity\":" + quantity)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        em.flush();
        em.clear();
        var result = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response);
        assertEquals(result.get(0).get("id").asLong(), result.get(1).get("relatedSellId").asLong());
        decimal(quantity, result.get(0).get("quantity").decimalValue());
        decimal(gross, result.get(0).get("amount").decimalValue());
        assertEquals(result.get(0).get("id").asLong(), investments.findById(result.get(1).get("id").asLong())
                .orElseThrow().getRelatedSell().getId().longValue());
        return result;
    }

    @Test
    void threeUnitsPartialSaleThenNewContributionUsesRemainingCost() throws Exception {
        setPosition("3", "3000", "3300", AssetStatus.ACTIVE);
        var result = sell("1", "1050", "10");
        decimal("1000", result.get(0).get("costBasis").decimalValue());
        var position = positions.findByAssetId(asset.getId()).orElseThrow();
        decimal("2", position.getQuantity());
        decimal("2000", position.getRemainingCost());
        decimal("3000", position.getTotalInvested());
        decimal("1000", position.getAveragePrice());
        decimal("2200", position.getCurrentValue());
        decimal("1040", position.getRedeemedValue());
        assertEquals(AssetStatus.ACTIVE, assets.findById(asset.getId()).orElseThrow().getStatus());
        assertEquals(LocalDate.of(2026, 10, 12), position.getLastValuationDate());
        decimal("1060", transactions.calculateBalanceByPocketId(pocket.getId()));
        mvc.perform(post("/api/assets/" + asset.getId() + "/transactions/buy").contextPath("/api").with(user(owner))
                        .contentType("application/json").content("{\"amount\":1200,\"quantity\":1,\"transactionDate\":\"2026-10-14\",\"categoryId\":39,\"pocketId\":" + pocket.getId() + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.amount").value(1200));
        em.flush(); em.clear();
        var newBuy = investments.findAllByAssetIdOrderByTransactionDateDesc(asset.getId()).stream()
                .filter(t -> t.getType() == InvestmentTransactionType.BUY && t.getQuantity() != null).findFirst().orElseThrow();
        decimal("1", newBuy.getQuantity());
        decimal("1200", newBuy.getAmount());
        position = positions.findByAssetId(asset.getId()).orElseThrow();
        decimal("3", position.getQuantity());
        decimal("4200", position.getTotalInvested());
        decimal("3200", position.getRemainingCost());
        decimal("1066.66666667", position.getAveragePrice());
        decimal("3400", position.getCurrentValue());
        decimal("1040", position.getRedeemedValue());
        var next = sell("1", "1100", "0");
        decimal("1066.66666667", next.get(0).get("costBasis").decimalValue());
        var last = sell("2", "2200", "0");
        decimal("2133.33333333", last.get(0).get("costBasis").decimalValue());
        position = positions.findByAssetId(asset.getId()).orElseThrow();
        decimal("4200", position.getTotalInvested());
        decimal("4340", position.getRedeemedValue());
        assertClosed(position);
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,500,0", "ACTIVE,1500,0", "ACTIVE,100,100", "MATURED,1050,10", "MATURED,100,100"})
    void partialSalePreservesStatusAndValuationRegardlessOfNegotiatedPrice(String status, String gross, String tax) throws Exception {
        setPosition("3", "3000", "3300", AssetStatus.valueOf(status));
        sell("1", gross, tax);
        var position = positions.findByAssetId(asset.getId()).orElseThrow();
        assertEquals(AssetStatus.valueOf(status), assets.findById(asset.getId()).orElseThrow().getStatus());
        decimal("2", position.getQuantity());
        decimal("2000", position.getRemainingCost());
        decimal("2200", position.getCurrentValue());
        decimal("1000", position.getAveragePrice());
        decimal(new BigDecimal(gross).subtract(new BigDecimal(tax)).toPlainString(), position.getRedeemedValue());
        sell("2", "1", "1");
        assertClosed(positions.findByAssetId(asset.getId()).orElseThrow());
    }

    @Test
    void fractionalSalesAllocateRoundingResidualToFinalSale() throws Exception {
        setPosition("1", "1", "2", AssetStatus.ACTIVE);
        var first = sell("0.33333333", "0.50", "0");
        var second = sell("0.33333333", "0.50", "0");
        var third = sell("0.33333334", "0.50", "0");
        decimal("1", first.get(0).get("costBasis").decimalValue()
                .add(second.get(0).get("costBasis").decimalValue()).add(third.get(0).get("costBasis").decimalValue()));
        assertClosed(positions.findByAssetId(asset.getId()).orElseThrow());
    }

    @Test
    void smallestSupportedQuantityDoesNotDisappear() throws Exception {
        sell("0.00000001", "0.01", "0");
        var position = positions.findByAssetId(asset.getId()).orElseThrow();
        decimal("1.99999999", position.getQuantity());
        decimal("79.99999960", position.getRemainingCost());
        decimal("99.99999950", position.getCurrentValue());
    }

    @ParameterizedTest
    @CsvSource({"null,400", "0,400", "-1,400", "0.000000001,400", "10000000000,400", "2.00000001,409"})
    void invalidQuantityDoesNotChangePositionHistoryOrCash(String quantity, int expectedStatus) throws Exception {
        mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json")
                        .content(body("100", "10").replace("\"quantity\":2", "\"quantity\":" + quantity)))
                .andExpect(status().is(expectedStatus));
        assertPreserved();
    }

    @Test
    void missingQuantityNeverDefaultsToFullSale() throws Exception {
        mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json")
                        .content(body("100", "10").replace("\"quantity\":2,", "")))
                .andExpect(status().isBadRequest());
        assertPreserved();
    }

    private void assertClosed(InvestmentPosition position) {
        decimal("0", position.getQuantity());
        decimal("0", position.getRemainingCost());
        decimal("0", position.getCurrentValue());
        decimal("0", position.getAveragePrice());
        assertEquals(AssetStatus.REDEEMED, assets.findById(asset.getId()).orElseThrow().getStatus());
        assertEquals(LocalDate.of(2026, 10, 12), position.getLastValuationDate());
    }

    @Test
    void persistsAndSerializesNewHistoryFieldsWithoutInventingLegacyUnits() throws Exception {
        var sell = investments.save(InvestmentTransaction.builder().asset(asset)
                .type(InvestmentTransactionType.SELL).amount(new BigDecimal("50"))
                .quantity(new BigDecimal("0.12500001")).costBasis(new BigDecimal("40.12345678"))
                .transactionDate(LocalDate.of(2026, 10, 13)).build());
        var tax = investments.save(InvestmentTransaction.builder().asset(asset)
                .type(InvestmentTransactionType.TAX).amount(new BigDecimal("1"))
                .relatedSell(sell).transactionDate(LocalDate.of(2026, 10, 13)).build());
        em.flush();
        em.clear();
        var persistedSell = investments.findById(sell.getId()).orElseThrow();
        decimal("0.12500001", persistedSell.getQuantity());
        decimal("40.12345678", persistedSell.getCostBasis());
        assertEquals(sell.getId(), investments.findById(tax.getId()).orElseThrow().getRelatedSell().getId());
        var response = mvc.perform(get("/api/assets/" + asset.getId() + "/transactions")
                        .contextPath("/api").with(user(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var history = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response);
        for (var movement : history) {
            switch (movement.get("type").asString()) {
                case "SELL" -> {
                    decimal("0.12500001", movement.get("quantity").decimalValue());
                    decimal("40.12345678", movement.get("costBasis").decimalValue());
                    assertTrue(movement.get("relatedSellId").isNull());
                }
                case "TAX" -> {
                    assertEquals(sell.getId().longValue(), movement.get("relatedSellId").asLong());
                    assertTrue(movement.get("quantity").isNull());
                    assertTrue(movement.get("costBasis").isNull());
                }
                case "BUY" -> assertTrue(movement.get("quantity").isNull());
                default -> fail("Unexpected movement");
            }
        }
    }

    @BeforeEach
    void setup() {
        owner = users.save(User.builder().firstName("Sell").lastName("Test")
                .email(UUID.randomUUID() + "@example.com").password("encoded-password")
                .dateOfBirth(LocalDate.of(1990, 1, 1)).enabled(true).emailVerified(true)
                .roles(Set.of(roles.findByName("USER").orElseThrow())).build());
        LegalEntity issuer = legalEntities.save(LegalEntity.builder().user(owner)
                .cnpj("12345678000190").corporateName("Test issuer").build());
        asset = assets.save(Asset.builder().user(owner).legalEntity(issuer).name("Test investment")
                .category(AssetCategory.RENDA_FIXA).status(AssetStatus.ACTIVE).build());
        pocket = new Cash();
        pocket.setUser(owner);
        pocket = pockets.save(pocket);
        positions.save(InvestmentPosition.builder().asset(asset).quantity(new BigDecimal("2"))
                .averagePrice(new BigDecimal("40")).totalInvested(new BigDecimal("80")).remainingCost(new BigDecimal("80"))
                .currentValue(new BigDecimal("100")).redeemedValue(new BigDecimal("5"))
                .lastValuationDate(LocalDate.of(2026, 10, 12)).build());
        Category category = em.find(Category.class, 41L);
        transactions.save(Transaction.builder().pocket(pocket).category(category).direction(Direction.INCOME)
                .amount(new BigDecimal("20")).description("Initial balance").transactionDate(LocalDate.of(2026, 10, 12)).build());
        investments.save(InvestmentTransaction.builder().asset(asset).type(InvestmentTransactionType.BUY)
                .amount(new BigDecimal("80")).transactionDate(LocalDate.of(2026, 10, 12)).build());
        em.flush();
        em.clear();
    }

    private String path() { return "/api/assets/" + asset.getId() + "/transactions/sell"; }
    private String body(String gross, String tax) {
        return "{\"operationId\":\"" + UUID.randomUUID() + "\",\"quantity\":2,\"grossAmount\":" + gross + ",\"taxAmount\":" + tax
                + ",\"transactionDate\":\"2026-10-13\",\"pocketId\":" + pocket.getId()
                + ",\"categoryId\":41,\"notes\":\"Resgate total\"}";
    }
    private void decimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
    private void assertPreserved() {
        em.flush();
        em.clear();
        InvestmentPosition persisted = positions.findByAssetId(asset.getId()).orElseThrow();
        assertEquals(AssetStatus.ACTIVE, assets.findById(asset.getId()).orElseThrow().getStatus());
        decimal("2", persisted.getQuantity());
        decimal("40", persisted.getAveragePrice());
        decimal("80", persisted.getTotalInvested());
        decimal("100", persisted.getCurrentValue());
        decimal("5", persisted.getRedeemedValue());
        assertEquals(LocalDate.of(2026, 10, 12), persisted.getLastValuationDate());
        decimal("20", transactions.calculateBalanceByPocketId(pocket.getId()));
        assertEquals(1, transactions.findAllByPocketIdAndPeriod(pocket.getId(), null, null).size());
        var history = investments.findAllByAssetIdOrderByTransactionDateDesc(asset.getId());
        assertEquals(1, history.size());
        assertEquals(InvestmentTransactionType.BUY, history.getFirst().getType());
        decimal("80", history.getFirst().getAmount());
    }

    @ParameterizedTest
    @CsvSource({"null,0", "0,0", "-1,0", "100,null", "100,-50", "100,150"})
    void invalidAmountsReturn400AndPreservePersistedState(String gross, String tax) throws Exception {
        var result = mvc.perform(post(path()).contextPath("/api").with(user(owner))
                        .contentType("application/json").content(body(gross, tax)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(path()));
        if (tax.equals("150")) {
            result.andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("taxAmount must not exceed grossAmount"));
        }
        assertPreserved();
    }

    @ParameterizedTest
    @CsvSource({"0,100", "10,90", "100,0", "100.00000000,0"})
    void validAmountsKeepFullRedemptionIncludingZeroNet(String tax, String net) throws Exception {
        mvc.perform(post(path()).contextPath("/api").with(user(owner))
                        .contentType("application/json").content(body("100", tax)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("SELL"))
                .andExpect(jsonPath("$[0].amount").value(100))
                .andExpect(jsonPath("$[1].type").value("TAX"))
                .andExpect(jsonPath("$[0].pocketTransactionId").isNumber())
                .andExpect(jsonPath("$[1].pocketTransactionId").isEmpty());
        em.flush();
        em.clear();
        var persisted = positions.findByAssetId(asset.getId()).orElseThrow();
        assertEquals(AssetStatus.REDEEMED, assets.findById(asset.getId()).orElseThrow().getStatus());
        decimal("0", persisted.getQuantity());
        decimal("0", persisted.getAveragePrice());
        decimal("0", persisted.getCurrentValue());
        decimal("80", persisted.getTotalInvested());
        decimal(new BigDecimal("5").add(new BigDecimal(net)).toPlainString(), persisted.getRedeemedValue());
        decimal(new BigDecimal("20").add(new BigDecimal(net)).toPlainString(), transactions.calculateBalanceByPocketId(pocket.getId()));
        assertEquals(2, transactions.findAllByPocketIdAndPeriod(pocket.getId(), null, null).size());
        assertEquals(3, investments.findAllByAssetIdOrderByTransactionDateDesc(asset.getId()).size());
    }

    @Test
    void operationIdIsRequiredAndMustBeAUuid() throws Exception {
        String valid = body("100", "10");
        for (String invalid : new String[]{valid.replaceFirst("\"operationId\":\"[^\"]+\",", ""),
                valid.replaceFirst("\"operationId\":\"[^\"]+\"", "\"operationId\":null"),
                valid.replaceFirst("\"operationId\":\"[^\"]+\"", "\"operationId\":\"invalid\"")}) {
            mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
            assertPreserved();
        }
    }

    @Test
    void httpRetryReturnsOriginalSaleAndConflictingCommandReturns409() throws Exception {
        String command = body("100", "10");
        var first = mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json").content(command))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        em.flush(); em.clear();
        mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json").content(command))
                .andExpect(status().isCreated()).andExpect(content().json(first));
        mvc.perform(post(path()).contextPath("/api").with(user(owner)).contentType("application/json")
                        .content(command.replace("\"grossAmount\":100", "\"grossAmount\":101")))
                .andExpect(status().isConflict());
        decimal("110", transactions.calculateBalanceByPocketId(pocket.getId()));
        assertEquals(3, investments.findAllByAssetIdOrderByTransactionDateDesc(asset.getId()).size());
    }

    @Test
    void openApiDocumentsTheRealRedemptionContract() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/assets/{id}/transactions/sell'].post.description")
                        .value("Requires 0 < quantity <= available units, grossAmount > 0 and 0 <= taxAmount <= grossAmount. "
                                + "Tax is supplied by the client. Zero net amount is permitted. A user-scoped operationId UUID is required; retrying the same command returns the original SELL/TAX result."))
                .andExpect(jsonPath("$.paths['/assets/{id}/transactions/sell'].post.responses['400']").exists())
                .andExpect(jsonPath("$.components.schemas.InvestmentSellDto.required", org.hamcrest.Matchers.hasItem("quantity")))
                .andExpect(jsonPath("$.components.schemas.InvestmentSellDto.required", org.hamcrest.Matchers.hasItem("operationId")))
                .andExpect(jsonPath("$.components.schemas.InvestmentSellDto.properties.operationId.format").value("uuid"))
                .andExpect(jsonPath("$.components.schemas.InvestmentSellDto.properties.quantity.exclusiveMinimum").value(0))
                .andExpect(jsonPath("$.components.schemas.InvestmentSellDto.properties.taxAmount.description")
                        .value("Tax informed by the client; must be non-negative and no greater than grossAmount"));
    }

    @Test
    void otherOwnerCannotRedeem() throws Exception {
        User other = User.builder().id(-1L).email("other@example.com").password("encoded")
                .enabled(true).emailVerified(true).roles(owner.getRoles()).build();
        mvc.perform(post(path()).contextPath("/api").with(user(other))
                        .contentType("application/json").content(body("100", "10")))
                .andExpect(status().isNotFound());
        assertPreserved();
    }
}
