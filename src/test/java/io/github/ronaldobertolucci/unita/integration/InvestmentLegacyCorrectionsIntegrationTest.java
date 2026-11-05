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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.*;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** INV-002 exercises the real HTTP writers, persisted cash/position and dashboard query. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InvestmentLegacyCorrectionsIntegrationTest {
    @Autowired io.github.ronaldobertolucci.unita.service.group.GroupDashboardService groupDashboard;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired LegalEntityRepository legalEntities;
    @Autowired AssetRepository assets;
    @Autowired PocketRepository pockets;
    @Autowired InvestmentPositionRepository positions;
    @Autowired InvestmentTransactionRepository investments;
    @Autowired TransactionRepository cash;
    @Autowired FixedIncomeDetailsRepository fixedIncome;
    @Autowired PensionDetailsRepository pensions;
    private User owner;
    private Asset asset;
    private Cash pocket;

    @BeforeEach
    void setup() {
        owner = users.save(User.builder().firstName("INV002").lastName("Synthetic")
                .email(UUID.randomUUID() + "@example.invalid").password("not-a-login-hash")
                .dateOfBirth(LocalDate.of(1990, 1, 1)).enabled(true).emailVerified(true)
                .roles(Set.of(roles.findByName("USER").orElseThrow())).build());
        LegalEntity issuer = legalEntities.save(LegalEntity.builder().user(owner)
                .cnpj("12345678000190").corporateName("Synthetic issuer").build());
        asset = assets.save(Asset.builder().user(owner).legalEntity(issuer).name("INV002 asset")
                .category(AssetCategory.RENDA_FIXA).status(AssetStatus.ACTIVE).build());
        positions.save(InvestmentPosition.builder().asset(asset).build());
        fixedIncome.save(FixedIncomeDetails.builder().asset(asset).indexer(Indexer.CDI)
                .annualRate(new BigDecimal("1.1")).maturityDate(TODAY.plusYears(1)).taxFree(false).build());
        pocket = new Cash();
        pocket.setUser(owner);
        pocket = pockets.save(pocket);
        cash.save(Transaction.builder().pocket(pocket).category(em.find(Category.class, 3L))
                .direction(Direction.INCOME).amount(new BigDecimal("1000"))
                .transactionDate(TODAY.minusDays(1100)).description("Synthetic opening cash").build());
        reload();
    }

    private void reload() { em.flush(); em.clear(); }
    private String path() { return "/api/assets/" + asset.getId(); }
    private ResultActions postCommand(String operation, String fields, LocalDate date, int category) throws Exception {
        if (operation.equals("sell")) fields += ",\"operationId\":\"" + UUID.randomUUID() + "\"";
        return mvc.perform(post(path() + "/transactions/" + operation).contextPath("/api").with(user(owner))
                .contentType("application/json").content("{" + fields + ",\"transactionDate\":\"" + date
                        + "\",\"pocketId\":" + pocket.getId() + ",\"categoryId\":" + category + "}"));
    }
    private void buy(String price, String quantity, LocalDate date) throws Exception {
        postCommand("buy", "\"amount\":" + price + ",\"quantity\":" + quantity, date, 39)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("BUY"));
        reload();
    }
    private ResultActions suggestion(String gross, LocalDate date) throws Exception {
        return mvc.perform(get(path() + "/tax-suggestion").contextPath("/api").with(user(owner))
                .param("grossAmount", gross).param("purchaseDate", date.toString()).param("quantity", "10").param("saleDate", TODAY.toString()));
    }

    private ResultActions partialSuggestion(String gross, String quantity, LocalDate purchaseDate, LocalDate saleDate) throws Exception {
        return mvc.perform(get(path() + "/tax-suggestion").contextPath("/api").with(user(owner))
                .param("grossAmount", gross).param("quantity", quantity)
                .param("purchaseDate", purchaseDate.toString()).param("saleDate", saleDate.toString()));
    }

    @ParameterizedTest
    @CsvSource({"FIXED,false,1100,100,15", "FIXED,true,1100,0,0", "FIXED,false,900,0,0",
            "VGBL,REGRESSIVO,1100,100,30", "VGBL,PROGRESSIVO,1100,100,15",
            "PGBL,REGRESSIVO,1100,1100,330", "PGBL,PROGRESSIVO,1100,1100,165",
            "ENTIDADE_FECHADA,REGRESSIVO,1100,1100,330", "ENTIDADE_FECHADA,PROGRESSIVO,1100,1100,165"})
    void partialSuggestionUsesOnlySoldCostAcrossExistingCategories(String type, String regime, String gross, String base, String tax) throws Exception {
        if (type.equals("FIXED")) {
            fixedIncome.findByAssetId(asset.getId()).orElseThrow().setTaxFree(Boolean.parseBoolean(regime));
        } else {
            var managed = assets.findById(asset.getId()).orElseThrow();
            managed.setFixedIncomeDetails(null);
            em.flush();
            managed.setCategory(AssetCategory.PREVIDENCIA);
            pensions.save(PensionDetails.builder().asset(managed).pensionType(PensionType.valueOf(type))
                    .taxRegime(TaxRegime.valueOf(regime)).build());
        }
        buy("1000", "3", TODAY.minusDays(1000));
        var response = partialSuggestion(gross, "1", TODAY.minusDays(1000), TODAY)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalInvested").value(3000))
                .andExpect(jsonPath("$.costBasis").value(1000)).andReturn().getResponse().getContentAsString();
        var json = JsonMapper.builder().build().readTree(response);
        decimal(base, json.get("taxBase").decimalValue());
        decimal(tax, json.get("suggestedTaxAmount").decimalValue());
        assertState("3000", "3000", "0", "3", "-2000", 1, 2);
        postCommand("sell", "\"quantity\":1,\"grossAmount\":" + gross + ",\"taxAmount\":7", TODAY, 41)
                .andExpect(status().isCreated()).andExpect(jsonPath("$[1].amount").value(7));
        reload();
        partialSuggestion(gross, "1", TODAY.minusDays(1000), TODAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.costBasis").value(1000)).andExpect(jsonPath("$.totalInvested").value(3000));
    }

    @Test
    void suppliedSaleDateControlsHoldingPeriodAndAllowsFutureSimulation() throws Exception {
        buy("1000", "3", TODAY.minusDays(1000));
        partialSuggestion("1100", "1", TODAY.minusDays(1000), TODAY.minusDays(900))
                .andExpect(status().isOk()).andExpect(jsonPath("$.daysElapsed").value(100))
                .andExpect(jsonPath("$.suggestedTaxRate").value(0.225));
        partialSuggestion("1100", "1", TODAY.minusDays(1000), TODAY.plusDays(100))
                .andExpect(status().isOk()).andExpect(jsonPath("$.daysElapsed").value(1100));
        partialSuggestion("1100", "1", TODAY, TODAY.minusDays(1)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"0,400", "-1,400", "0.000000001,400", "10000000000,400", "3.00000001,409"})
    void invalidSuggestedQuantityIsRejectedWithoutWrites(String quantity, int statusCode) throws Exception {
        buy("1000", "3", TODAY.minusDays(1000));
        partialSuggestion("1100", quantity, TODAY.minusDays(1000), TODAY).andExpect(status().is(statusCode));
        assertState("3000", "3000", "0", "3", "-2000", 1, 2);
    }

    @Test
    void legacyUnitsAndEarlierYieldAreExplicitLimitations() throws Exception {
        buy("1000", "3", TODAY.minusDays(1000));
        investments.findAllByAssetIdOrderByTransactionDateDesc(asset.getId()).getFirst().setQuantity(null);
        reload();
        partialSuggestion("1100", "1", TODAY.minusDays(1000), TODAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimationStatus").value("LIMITED_ESTIMATE"))
                .andExpect(jsonPath("$.warnings[0]", org.hamcrest.Matchers.containsString("histórico legado")));
        postCommand("yield", "\"amount\":30", TODAY, 40).andExpect(status().isCreated());
        partialSuggestion("1100", "1", TODAY.minusDays(1000), TODAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("YIELD"))));
        var position = positions.findByAssetId(asset.getId()).orElseThrow();
        decimal("3000", position.getRemainingCost());
        decimal("3", position.getQuantity());
    }

    @Test
    void suggestionAfterLegacyPartialSaleReportsUnknownHistoricalCost() throws Exception {
        buy("1000", "3", TODAY.minusDays(1000));
        postCommand("sell", "\"quantity\":1,\"grossAmount\":1100,\"taxAmount\":10", TODAY, 41).andExpect(status().isCreated());
        investments.findAllByAssetIdOrderByTransactionDateDesc(asset.getId()).stream()
                .filter(t -> t.getType() == InvestmentTransactionType.SELL).findFirst().orElseThrow().setCostBasis(null);
        reload();
        partialSuggestion("1100", "1", TODAY.minusDays(1000), TODAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.costBasis").value(1000))
                .andExpect(jsonPath("$.estimationStatus").value("LIMITED_ESTIMATE"))
                .andExpect(jsonPath("$.warnings", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("custos de venda desconhecidos"))));
    }

    @ParameterizedTest
    @CsvSource({"quantity", "saleDate"})
    void newSuggestionParametersAreIndividuallyRequired(String missing) throws Exception {
        buy("1000", "3", TODAY.minusDays(1000));
        var request = get(path() + "/tax-suggestion").contextPath("/api").with(user(owner))
                .param("grossAmount", "1100").param("purchaseDate", TODAY.minusDays(1000).toString());
        if (!missing.equals("quantity")) request.param("quantity", "1");
        if (!missing.equals("saleDate")) request.param("saleDate", TODAY.toString());
        mvc.perform(request).andExpect(status().isBadRequest());
        assertState("3000", "3000", "0", "3", "-2000", 1, 2);
    }

    @Test
    void remainingZeroCostWithUnitsIsAValidEstimateAndFullSaleUsesExactCost() throws Exception {
        buy("1", "1", TODAY.minusDays(1000));
        var position = positions.findByAssetId(asset.getId()).orElseThrow();
        position.setQuantity(new BigDecimal("0.33333334"));
        position.setRemainingCost(new BigDecimal("0.33333334"));
        reload();
        partialSuggestion("1", "0.33333334", TODAY.minusDays(1000), TODAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.costBasis").value(0.33333334));
        positions.findByAssetId(asset.getId()).orElseThrow().setRemainingCost(BigDecimal.ZERO);
        reload();
        partialSuggestion("1", "0.33333334", TODAY.minusDays(1000), TODAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.costBasis").value(0)).andExpect(jsonPath("$.taxBase").value(1));
    }

    @Test
    void yieldAndMultipleSalesAreCountedOnceOnPersonalAndGroupDashboards() throws Exception {
        var group = io.github.ronaldobertolucci.unita.model.group.Group.builder().name("Investment results").responsibleUser(owner).build();
        em.persist(group);
        em.persist(io.github.ronaldobertolucci.unita.model.group.GroupMembership.builder().group(group).user(owner).build());
        var permission = io.github.ronaldobertolucci.unita.model.group.GroupSharePermission.builder().group(group).user(owner)
                .shareType(io.github.ronaldobertolucci.unita.model.group.ShareType.INVESTMENTS).enabled(true).build();
        em.persist(permission);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities());
        buy("1000", "3", TODAY.minusDays(1000));
        mvc.perform(patch(path() + "/position").contextPath("/api").with(user(owner)).contentType("application/json")
                .content("{\"currentValue\":3300,\"lastValuationDate\":\"2026-10-14\"}")).andExpect(status().isOk());
        postCommand("yield", "\"amount\":30", TODAY, 40).andExpect(status().isCreated());
        postCommand("sell", "\"quantity\":1,\"grossAmount\":1100,\"taxAmount\":10", TODAY, 41).andExpect(status().isCreated());
        reload();
        decimal("0", groupDashboard.getGroupNetProfit(group.getId(), auth).members().getFirst().netProfit());
        decimal("0", new BigDecimal(mvc.perform(get("/api/dashboard/net-profit").contextPath("/api").with(user(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()));
        postCommand("sell", "\"quantity\":2,\"grossAmount\":2200,\"taxAmount\":20", TODAY, 41).andExpect(status().isCreated());
        reload();
        for (int i = 0; i < 2; i++) {
            decimal("300", groupDashboard.getGroupNetProfit(group.getId(), auth).members().getFirst().netProfit());
            String personal = mvc.perform(get("/api/dashboard/net-profit").contextPath("/api").with(user(owner)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            decimal("300", new BigDecimal(personal));
        }
        em.find(io.github.ronaldobertolucci.unita.model.group.GroupSharePermission.class, permission.getId()).setEnabled(false);
        em.flush();
        assertNull(groupDashboard.getGroupNetProfit(group.getId(), auth).members().getFirst().netProfit());
        partialSuggestion("1100", "1", TODAY.minusDays(1000), TODAY).andExpect(status().isConflict());
    }
    private void decimal(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    private void assertState(String invested, String current, String redeemed, String quantity, String balance, int movements, int cashRows) {
        reload();
        var position = positions.findByAssetId(asset.getId()).orElseThrow();
        decimal(invested, position.getTotalInvested());
        decimal(current, position.getCurrentValue());
        decimal(redeemed, position.getRedeemedValue());
        decimal(quantity, position.getQuantity());
        decimal(balance, cash.calculateBalanceByPocketId(pocket.getId()));
        assertEquals(movements, investments.findAllByAssetIdOrderByTransactionDateDesc(asset.getId()).size());
        assertEquals(cashRows, cash.findAllByPocketIdAndPeriod(pocket.getId(), null, null).size());
    }

    @ParameterizedTest(name = "S01: gross {0}, tax {1} gives profit {2} and cash {3}")
    @CsvSource({"1100,15,85,1085", "1100,0,100,1100", "900,0,-100,900", "1000,1000,-1000,0"})
    void realBuyAndFullSellReportAlreadyNetProfitWithoutAnotherTaxDeduction(String gross, String tax, String profit, String net) throws Exception {
        buy("100", "10", TODAY.minusDays(1000));
        assertState("1000", "1000", "0", "10", "0", 1, 2);
        postCommand("sell", "\"quantity\":10,\"grossAmount\":" + gross + ",\"taxAmount\":" + tax, TODAY, 41)
                .andExpect(status().isCreated());
        assertState("1000", "0", net, "0", net, 3, 3);
        assertEquals(AssetStatus.REDEEMED, assets.findById(asset.getId()).orElseThrow().getStatus());
        for (int read = 0; read < 2; read++) {
            var response = mvc.perform(get("/api/dashboard/net-profit").contextPath("/api").with(user(owner)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            decimal(profit, new BigDecimal(response));
        }
        var taxMovement = investments.findAllByAssetIdOrderByTransactionDateDesc(asset.getId()).stream()
                .filter(tx -> tx.getType() == InvestmentTransactionType.TAX).findFirst().orElseThrow();
        decimal(tax, taxMovement.getAmount());
        assertNull(taxMovement.getTransaction());
        assertState("1000", "0", net, "0", net, 3, 3);
    }

    @Test
    void legacyYieldRemainsIncludedExactlyOnceInClosedResult() throws Exception {
        buy("100", "10", TODAY.minusDays(1000));
        postCommand("yield", "\"amount\":20", TODAY.minusDays(1), 40).andExpect(status().isCreated());
        assertState("1000", "980", "20", "10", "20", 2, 3);
        postCommand("sell", "\"quantity\":10,\"grossAmount\":1080,\"taxAmount\":15", TODAY, 41).andExpect(status().isCreated());
        String response = mvc.perform(get("/api/dashboard/net-profit").contextPath("/api").with(user(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        decimal("85", new BigDecimal(response));
        assertState("1000", "0", "1085", "0", "1085", 4, 4);
    }

    @ParameterizedTest
    @CsvSource({"null,1", "0,1", "-1,1", "1,null", "1,0", "1,-1", "-1,-1"})
    void invalidBuyDoesNotCreateEitherMovementOrChangePosition(String price, String quantity) throws Exception {
        postCommand("buy", "\"amount\":" + price + ",\"quantity\":" + quantity, TODAY, 39)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        assertState("0", "0", "0", "0", "1000", 0, 1);
    }

    @ParameterizedTest
    @CsvSource({"null,400", "0,400", "-1,400", "1000.01,409"})
    void invalidYieldDoesNotCreateEitherMovementOrChangePosition(String amount, int statusCode) throws Exception {
        buy("100", "10", TODAY.minusDays(1000));
        postCommand("yield", "\"amount\":" + amount, TODAY, 40)
                .andExpect(status().is(statusCode)).andExpect(jsonPath("$.status").value(statusCode));
        assertState("1000", "1000", "0", "10", "0", 1, 2);
    }

    @ParameterizedTest
    @CsvSource({"FIXED,false,1100,100,0.15,15", "FIXED,false,900,0,0.15,0", "FIXED,false,1000,0,0.15,0",
            "FIXED,true,1100,0,0,0", "VGBL,REGRESSIVO,1100,100,0.30,30", "VGBL,REGRESSIVO,900,0,0.30,0",
            "PGBL,REGRESSIVO,900,900,0.30,270", "ENTIDADE_FECHADA,REGRESSIVO,900,900,0.30,270",
            "VGBL,PROGRESSIVO,900,0,0.15,0", "PGBL,PROGRESSIVO,900,900,0.15,135"})
    void taxSuggestionsUseCategorySpecificDescriptionsAndNonNegativeTaxBases(String type, String regime, String gross,
                                                                              String base, String rate, String tax) throws Exception {
        if (type.equals("FIXED")) {
            fixedIncome.findByAssetId(asset.getId()).orElseThrow().setTaxFree(Boolean.parseBoolean(regime));
        } else {
            Asset managed = assets.findById(asset.getId()).orElseThrow();
            managed.setFixedIncomeDetails(null);
            em.flush();
            managed.setCategory(AssetCategory.PREVIDENCIA);
            pensions.save(PensionDetails.builder().asset(managed).pensionType(PensionType.valueOf(type))
                    .taxRegime(TaxRegime.valueOf(regime)).build());
        }
        buy("100", "10", TODAY.minusDays(1000));
        var response = suggestion(gross, TODAY.minusDays(1000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimationStatus").value("ESTIMATE"))
                .andExpect(jsonPath("$.warnings").isEmpty()).andExpect(jsonPath("$.daysElapsed").value(1000))
                .andReturn().getResponse().getContentAsString();
        var json = JsonMapper.builder().build().readTree(response);
        decimal(base, json.get("taxBase").decimalValue());
        decimal(rate, json.get("suggestedTaxRate").decimalValue());
        decimal(tax, json.get("suggestedTaxAmount").decimalValue());
        decimal(new BigDecimal(gross).subtract(new BigDecimal(tax)).toPlainString(), json.get("suggestedNetAmount").decimalValue());
        decimal(new BigDecimal(gross).subtract(new BigDecimal("1000")).toPlainString(), json.get("earnings").decimalValue());
        if (!type.equals("FIXED")) {
            assertTrue(json.get("taxBasis").asString().startsWith("Previdência"));
            assertFalse(json.get("taxBasis").asString().contains("720"));
        }
        assertState("1000", "1000", "0", "10", "0", 1, 2);
    }

    @Test
    void multiplePurchaseDatesAreExplicitlyLimitedWithoutPretendingOneDateRepresentsAllLots() throws Exception {
        buy("100", "5", TODAY.minusDays(1000));
        buy("100", "5", TODAY.minusDays(10));
        suggestion("1100", TODAY.minusDays(1000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimationStatus").value("LIMITED_ESTIMATE"))
                .andExpect(jsonPath("$.warnings[0]", org.hamcrest.Matchers.containsString("datas distintas")))
                .andExpect(jsonPath("$.taxBasis", org.hamcrest.Matchers.containsString("não representa todos os aportes")));
        // A subsequent real redemption must use the informed tax, regardless of the estimate.
        postCommand("sell", "\"quantity\":10,\"grossAmount\":1100,\"taxAmount\":7", TODAY, 41).andExpect(status().isCreated());
        assertState("1000", "0", "1093", "0", "1093", 4, 4);
    }

    @Test
    void sameDayPurchasesDoNotCreateFalseMultiDateWarning() throws Exception {
        buy("100", "5", TODAY.minusDays(1000));
        buy("100", "5", TODAY.minusDays(1000));
        suggestion("1100", TODAY.minusDays(1000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimationStatus").value("ESTIMATE"));
        suggestion("1100", TODAY.minusDays(900)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimationStatus").value("LIMITED_ESTIMATE"))
                .andExpect(jsonPath("$.warnings[0]", org.hamcrest.Matchers.containsString("difere")));
    }

    @Test
    void missingLegacyPurchaseHistoryIsExplicitlyLimited() throws Exception {
        var position = positions.findByAssetId(asset.getId()).orElseThrow();
        position.setTotalInvested(new BigDecimal("1000"));
        position.setRemainingCost(new BigDecimal("1000"));
        position.setQuantity(new BigDecimal("10"));
        suggestion("1100", TODAY.minusDays(1000)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estimationStatus").value("LIMITED_ESTIMATE"))
                .andExpect(jsonPath("$.warnings[0]", org.hamcrest.Matchers.containsString("ausente")));
    }

    @ParameterizedTest
    @CsvSource({"0,2024-01-01", "-1,2024-01-01", "1100,2026-10-15", "1100,invalid", "invalid,2024-01-01"})
    void invalidSuggestionInputReturns400AndPreservesState(String gross, String date) throws Exception {
        buy("100", "10", TODAY.minusDays(1000));
        mvc.perform(get(path() + "/tax-suggestion").contextPath("/api").with(user(owner))
                        .param("grossAmount", gross).param("purchaseDate", date).param("quantity", "10").param("saleDate", TODAY.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertState("1000", "1000", "0", "10", "0", 1, 2);
    }

    @Test
    void emptyPositionIsRejectedByTaxSuggestion() throws Exception {
        positions.findByAssetId(asset.getId()).orElseThrow().setTotalInvested(BigDecimal.ZERO);
        suggestion("1100", TODAY.minusDays(1000)).andExpect(status().isConflict());
    }

    @Test
    void negativeInvestmentBaseCannotBePersistedAfterV26() {
        var position = positions.findByAssetId(asset.getId()).orElseThrow();
        position.setTotalInvested(new BigDecimal("-1"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> positions.saveAndFlush(position));
    }

    @Test
    void taxSuggestionAllowsSameDaySaleAndRequiresQueryFields() throws Exception {
        buy("100", "10", TODAY);
        suggestion("1100", TODAY).andExpect(status().isOk()).andExpect(jsonPath("$.daysElapsed").value(0));
        mvc.perform(get(path() + "/tax-suggestion").contextPath("/api").with(user(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void suggestionsAndCommandsRequireAuthenticationAndHideOtherOwners() throws Exception {
        mvc.perform(get(path() + "/tax-suggestion").contextPath("/api")
                .param("grossAmount", "1100").param("purchaseDate", "2024-01-01").param("quantity", "10").param("saleDate", TODAY.toString())).andExpect(status().isUnauthorized());
        User other = User.builder().id(-1L).email("other@example.invalid").password("encoded")
                .enabled(true).emailVerified(true).roles(owner.getRoles()).build();
        mvc.perform(get(path() + "/tax-suggestion").contextPath("/api").with(user(other))
                .param("grossAmount", "1100").param("purchaseDate", "2024-01-01").param("quantity", "10").param("saleDate", TODAY.toString())).andExpect(status().isNotFound());
        for (String operation : new String[]{"buy", "yield"}) {
            mvc.perform(post(path() + "/transactions/" + operation).contextPath("/api").with(user(other))
                    .contentType("application/json").content("{\"amount\":1,\"quantity\":1,\"transactionDate\":\"2026-10-14\",\"pocketId\":"
                            + pocket.getId() + ",\"categoryId\":39}")).andExpect(status().isNotFound());
        }
        assertState("0", "0", "0", "0", "1000", 0, 1);
    }

    @Test
    void openApiDocumentsValidationEstimateLimitationsAndNetProfitSemantics() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/assets/{id}/tax-suggestion'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/assets/{id}/transactions/buy'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/assets/{id}/transactions/yield'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.InvestmentBuyDto.properties.quantity.exclusiveMinimum").value(0))
                .andExpect(jsonPath("$.components.schemas.InvestmentYieldDto.properties.amount.exclusiveMinimum").value(0))
                .andExpect(jsonPath("$.components.schemas.TaxSuggestionDto.properties.taxBase.type").value("number"))
                .andExpect(jsonPath("$.components.schemas.TaxSuggestionDto.properties.estimationStatus.enum[1]").value("LIMITED_ESTIMATE"))
                .andExpect(jsonPath("$.components.schemas.TaxSuggestionDto.properties.warnings.type").value("array"))
                .andExpect(jsonPath("$.paths['/dashboard/net-profit'].get.description", org.hamcrest.Matchers.containsString("TAX is not deducted again")));
    }
}
