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
                .averagePrice(new BigDecimal("40")).totalInvested(new BigDecimal("80"))
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
        return "{\"grossAmount\":" + gross + ",\"taxAmount\":" + tax
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
    void openApiDocumentsTheRealRedemptionContract() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/assets/{id}/transactions/sell'].post.description")
                        .value("Requires grossAmount > 0 and 0 <= taxAmount <= grossAmount. "
                                + "Tax is supplied by the client. Zero net amount is permitted."))
                .andExpect(jsonPath("$.paths['/assets/{id}/transactions/sell'].post.responses['400']").exists())
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
