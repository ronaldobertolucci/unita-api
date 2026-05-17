package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.investment.*;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.investment.AssetService;
import io.github.ronaldobertolucci.unita.service.investment.InvestmentTransactionService;
import io.github.ronaldobertolucci.unita.service.security.TokenService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AssetController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean
    private AssetService assetService;
    @MockitoBean private InvestmentTransactionService investmentTransactionService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AssetSummaryDto assetSummaryDto() {
        return new AssetSummaryDto(1L, "CDB Banco X", AssetCategory.RENDA_FIXA,
                AssetStatus.ACTIVE, "Banco Teste", null,
                new BigDecimal("1100.00000000"), new BigDecimal("1000.00000000"), BigDecimal.ZERO);
    }

    private AssetDetailDto assetDetailDto() {
        LegalEntityDto leDto = new LegalEntityDto(10L, "12345678000190", "Banco Teste", null, null);
        InvestmentPositionDto positionDto = new InvestmentPositionDto(
                new BigDecimal("1.00000000"), new BigDecimal("1000.00000000"),
                new BigDecimal("1000.00000000"), new BigDecimal("1100.00000000"),
                BigDecimal.ZERO, LocalDate.of(2025, 1, 1));
        FixedIncomeDetailsDto detailsDto = new FixedIncomeDetailsDto(
                Indexer.CDI, new BigDecimal("0.12000000"),
                LocalDate.of(2027, 1, 1), false);
        return new AssetDetailDto(1L, "CDB Banco X", AssetCategory.RENDA_FIXA,
                AssetStatus.ACTIVE, LiquidityType.DIARIA, leDto, null, positionDto, detailsDto, null);
    }

    private InvestmentTransactionDto investmentTransactionDto() {
        return new InvestmentTransactionDto(1L, InvestmentTransactionType.BUY,
                new BigDecimal("1000.00000000"), LocalDate.now(), null, 10L);
    }

    private TaxSuggestionDto taxSuggestionDto() {
        return new TaxSuggestionDto(
                new BigDecimal("1100.00"),       // grossAmount
                new BigDecimal("1000.00"),       // totalInvested
                new BigDecimal("100.00"),        // earnings
                new BigDecimal("0.150"),         // suggestedTaxRate
                new BigDecimal("15.00000000"),   // suggestedTaxAmount (15% sobre 100)
                new BigDecimal("1085.00000000"), // suggestedNetAmount (1100 - 15)
                800,                             // daysElapsed
                "Tabela regressiva IR - acima de 720 dias (15,0%)" // taxBasis
        );
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Test
    void findAll_ShouldReturn200WithList() throws Exception {
        when(assetService.findAll(any())).thenReturn(List.of(assetSummaryDto()));

        mockMvc.perform(get("/assets")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("CDB Banco X"))
                .andExpect(jsonPath("$[0].category").value("RENDA_FIXA"));
    }

    @Test
    void findAll_WhenEmpty_ShouldReturn200WithEmptyList() throws Exception {
        when(assetService.findAll(any())).thenReturn(List.of());

        mockMvc.perform(get("/assets")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findAll_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/assets"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    void findById_WhenExists_ShouldReturn200() throws Exception {
        when(assetService.findById(eq(1L), any())).thenReturn(assetDetailDto());

        mockMvc.perform(get("/assets/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("CDB Banco X"))
                .andExpect(jsonPath("$.fixedIncomeDetails.indexer").value("CDI"));
    }

    @Test
    void findById_WhenNotFound_ShouldReturn404() throws Exception {
        when(assetService.findById(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Asset not found"));

        mockMvc.perform(get("/assets/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void findById_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/assets/1"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // createFixedIncome
    // -------------------------------------------------------------------------

    @Test
    void createFixedIncome_WhenValid_ShouldReturn201() throws Exception {
        FixedIncomeAssetCreateDto dto = new FixedIncomeAssetCreateDto(
                "CDB Banco X", 10L, Indexer.CDI,
                new BigDecimal("0.12000000"), LocalDate.of(2027, 1, 1), false,
                LiquidityType.DIARIA, 10L);
        when(assetService.createFixedIncome(any(), any())).thenReturn(assetDetailDto());

        mockMvc.perform(post("/assets/fixed-income")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.category").value("RENDA_FIXA"));
    }

    @Test
    void createFixedIncome_WhenMissingFields_ShouldReturn400() throws Exception {
        FixedIncomeAssetCreateDto invalid = new FixedIncomeAssetCreateDto(
                null, null, null, null, null, null, null, null);

        mockMvc.perform(post("/assets/fixed-income")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFixedIncome_WhenDuplicateName_ShouldReturn400() throws Exception {
        FixedIncomeAssetCreateDto dto = new FixedIncomeAssetCreateDto(
                "CDB Banco X", 10L, Indexer.CDI,
                new BigDecimal("0.12000000"), LocalDate.of(2027, 1, 1), false,
                LiquidityType.DIARIA, 10L);
        when(assetService.createFixedIncome(any(), any()))
                .thenThrow(new IllegalArgumentException("An asset with this name already exists"));

        mockMvc.perform(post("/assets/fixed-income")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFixedIncome_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/assets/fixed-income")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // createPension
    // -------------------------------------------------------------------------

    @Test
    void createPension_WhenValid_ShouldReturn201() throws Exception {
        PensionAssetCreateDto dto = new PensionAssetCreateDto(
                "PGBL Banco X", 10L, PensionType.PGBL, TaxRegime.REGRESSIVO, 10L);

        LegalEntityDto leDto = new LegalEntityDto(10L, "12345678000190", "Banco Teste", null, null);
        InvestmentPositionDto positionDto = new InvestmentPositionDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        PensionDetailsDto detailsDto = new PensionDetailsDto(PensionType.PGBL, TaxRegime.REGRESSIVO);
        AssetDetailDto pensionDto = new AssetDetailDto(2L, "PGBL Banco X", AssetCategory.PREVIDENCIA,
                AssetStatus.ACTIVE, LiquidityType.DIARIA, leDto, null, positionDto, null, detailsDto);

        when(assetService.createPension(any(), any())).thenReturn(pensionDto);

        mockMvc.perform(post("/assets/pension")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.category").value("PREVIDENCIA"))
                .andExpect(jsonPath("$.pensionDetails.pensionType").value("PGBL"));
    }

    @Test
    void createPension_WhenMissingFields_ShouldReturn400() throws Exception {
        PensionAssetCreateDto invalid = new PensionAssetCreateDto(null, null, null, null, null);

        mockMvc.perform(post("/assets/pension")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPension_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/assets/pension")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_WhenValid_ShouldReturn200() throws Exception {
        AssetUpdateDto dto = new AssetUpdateDto("CDB Atualizado", 10L, LiquidityType.DIARIA, 10L);
        when(assetService.update(eq(1L), any(), any())).thenReturn(assetDetailDto());

        mockMvc.perform(put("/assets/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void update_WhenNotFound_ShouldReturn404() throws Exception {
        AssetUpdateDto dto = new AssetUpdateDto("CDB Atualizado", 10L, LiquidityType.DIARIA, 10L);
        when(assetService.update(eq(99L), any(), any()))
                .thenThrow(new EntityNotFoundException("Asset not found"));

        mockMvc.perform(put("/assets/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_WhenMissingFields_ShouldReturn400() throws Exception {
        AssetUpdateDto invalid = new AssetUpdateDto(null, null, null, null);

        mockMvc.perform(put("/assets/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_WhenUnauthenticated_ShouldReturn403() throws Exception {
        AssetUpdateDto dto = new AssetUpdateDto("CDB Atualizado", 10L, LiquidityType.DIARIA, 10L);

        mockMvc.perform(patch("/assets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePosition_WhenValid_ShouldReturn200() throws Exception {
        InvestmentPositionUpdateDto dto = new InvestmentPositionUpdateDto(
                new BigDecimal("1250.00"), LocalDate.of(2025, 3, 1));
        when(assetService.updatePosition(eq(1L), any(), any())).thenReturn(assetDetailDto());

        mockMvc.perform(patch("/assets/1/position")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updatePosition_WhenMissingFields_ShouldReturn400() throws Exception {
        InvestmentPositionUpdateDto invalid = new InvestmentPositionUpdateDto(null, null);

        mockMvc.perform(patch("/assets/1/position")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePosition_WhenAssetNotActive_ShouldReturn409() throws Exception {
        InvestmentPositionUpdateDto dto = new InvestmentPositionUpdateDto(
                new BigDecimal("1250.00"), LocalDate.of(2025, 3, 1));
        when(assetService.updatePosition(eq(1L), any(), any()))
                .thenThrow(new IllegalStateException("Cannot update position of a non-active asset"));

        mockMvc.perform(patch("/assets/1/position")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void updatePosition_WhenUnauthenticated_ShouldReturn403() throws Exception {
        InvestmentPositionUpdateDto dto = new InvestmentPositionUpdateDto(
                new BigDecimal("1250.00"), LocalDate.of(2025, 3, 1));

        mockMvc.perform(patch("/assets/1/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(assetService).delete(eq(1L), any());

        mockMvc.perform(delete("/assets/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Asset not found"))
                .when(assetService).delete(eq(99L), any());

        mockMvc.perform(delete("/assets/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_WhenHasTransactions_ShouldReturn409() throws Exception {
        doThrow(new IllegalStateException("Asset has transactions and cannot be deleted"))
                .when(assetService).delete(eq(1L), any());

        mockMvc.perform(delete("/assets/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(delete("/assets/1"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // getTaxSuggestion
    // -------------------------------------------------------------------------

    @Test
    void getTaxSuggestion_WhenValid_ShouldReturn200() throws Exception {
        when(assetService.getTaxSuggestion(eq(1L), any(), any(), any())).thenReturn(taxSuggestionDto());

        mockMvc.perform(get("/assets/1/tax-suggestion")
                        .param("grossAmount", "1100.00")
                        .param("purchaseDate", "2023-01-01")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedTaxRate").value(0.150))
                .andExpect(jsonPath("$.daysElapsed").value(800));
    }

    @Test
    void getTaxSuggestion_WhenAssetNotFound_ShouldReturn404() throws Exception {
        when(assetService.getTaxSuggestion(eq(99L), any(), any(), any()))
                .thenThrow(new EntityNotFoundException("Asset not found"));

        mockMvc.perform(get("/assets/99/tax-suggestion")
                        .param("grossAmount", "1100.00")
                        .param("purchaseDate", "2023-01-01")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTaxSuggestion_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/assets/1/tax-suggestion")
                        .param("grossAmount", "1100.00"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // transactions — findAll
    // -------------------------------------------------------------------------

    @Test
    void findTransactions_WhenOwned_ShouldReturn200WithList() throws Exception {
        when(investmentTransactionService.findAllByAsset(eq(1L), any()))
                .thenReturn(List.of(investmentTransactionDto()));

        mockMvc.perform(get("/assets/1/transactions")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("BUY"));
    }

    @Test
    void findTransactions_WhenAssetNotFound_ShouldReturn404() throws Exception {
        when(investmentTransactionService.findAllByAsset(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Asset not found"));

        mockMvc.perform(get("/assets/99/transactions")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void findTransactions_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/assets/1/transactions"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // transactions — buy
    // -------------------------------------------------------------------------

    @Test
    void buy_WhenValid_ShouldReturn201() throws Exception {
        InvestmentBuyDto dto = new InvestmentBuyDto(
                new BigDecimal("1000.00"), new BigDecimal("1.00000000"),
                LocalDate.now(), 5L, 1L, null);
        when(investmentTransactionService.buy(eq(1L), any(), any()))
                .thenReturn(investmentTransactionDto());

        mockMvc.perform(post("/assets/1/transactions/buy")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("BUY"));
    }

    @Test
    void buy_WhenMissingFields_ShouldReturn400() throws Exception {
        InvestmentBuyDto invalid = new InvestmentBuyDto(null, null, null, null, null, null);

        mockMvc.perform(post("/assets/1/transactions/buy")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buy_WhenAssetNotActive_ShouldReturn409() throws Exception {
        InvestmentBuyDto dto = new InvestmentBuyDto(
                new BigDecimal("1000.00"), new BigDecimal("1.00000000"),
                LocalDate.now(), 5L, 1L, null);
        when(investmentTransactionService.buy(eq(1L), any(), any()))
                .thenThrow(new IllegalStateException("Cannot buy into a non-active asset"));

        mockMvc.perform(post("/assets/1/transactions/buy")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void buy_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/assets/1/transactions/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // transactions — yield
    // -------------------------------------------------------------------------

    @Test
    void yield_WhenValid_ShouldReturn201() throws Exception {
        InvestmentYieldDto dto = new InvestmentYieldDto(
                new BigDecimal("50.00"), LocalDate.now(), 5L, 1L, "Rendimento mensal");
        InvestmentTransactionDto yieldDto = new InvestmentTransactionDto(
                2L, InvestmentTransactionType.YIELD,
                new BigDecimal("50.00"), LocalDate.now(), "Rendimento mensal", 10L);
        when(investmentTransactionService.yield(eq(1L), any(), any())).thenReturn(yieldDto);

        mockMvc.perform(post("/assets/1/transactions/yield")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("YIELD"));
    }

    @Test
    void yield_WhenMissingFields_ShouldReturn400() throws Exception {
        InvestmentYieldDto invalid = new InvestmentYieldDto(null, null, null, null, null);

        mockMvc.perform(post("/assets/1/transactions/yield")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void yield_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/assets/1/transactions/yield")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // transactions — sell
    // -------------------------------------------------------------------------

    @Test
    void sell_WhenValid_ShouldReturn201WithTwoTransactions() throws Exception {
        InvestmentSellDto dto = new InvestmentSellDto(
                new BigDecimal("1100.00"), new BigDecimal("165.00"),
                LocalDate.now(), 5L, 1L, "Resgate total");

        InvestmentTransactionDto sellDto = new InvestmentTransactionDto(
                3L, InvestmentTransactionType.SELL,
                new BigDecimal("1100.00"), LocalDate.now(), "Resgate total", 10L);
        InvestmentTransactionDto taxDto = new InvestmentTransactionDto(
                4L, InvestmentTransactionType.TAX,
                new BigDecimal("165.00"), LocalDate.now(), null, null);

        when(investmentTransactionService.sell(eq(1L), any(), any()))
                .thenReturn(List.of(sellDto, taxDto));

        mockMvc.perform(post("/assets/1/transactions/sell")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("SELL"))
                .andExpect(jsonPath("$[1].type").value("TAX"));
    }

    @Test
    void sell_WhenMissingFields_ShouldReturn400() throws Exception {
        InvestmentSellDto invalid = new InvestmentSellDto(null, null, null, null, null, null);

        mockMvc.perform(post("/assets/1/transactions/sell")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sell_WhenAlreadyRedeemed_ShouldReturn409() throws Exception {
        InvestmentSellDto dto = new InvestmentSellDto(
                new BigDecimal("1100.00"), new BigDecimal("165.00"),
                LocalDate.now(), 5L, 1L, null);
        when(investmentTransactionService.sell(eq(1L), any(), any()))
                .thenThrow(new IllegalStateException("Asset has already been fully redeemed"));

        mockMvc.perform(post("/assets/1/transactions/sell")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void sell_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/assets/1/transactions/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}