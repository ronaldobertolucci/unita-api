package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.dashboard.*;
import io.github.ronaldobertolucci.unita.model.investment.Indexer;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.dashboard.DashboardService;
import io.github.ronaldobertolucci.unita.service.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private DashboardService dashboardService;

    // -------------------------------------------------------------------------
    // GET /dashboard
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_ShouldReturn200WithData() throws Exception {
        DashboardDto dto = new DashboardDto(
                List.of(new CategorySummaryDto("BankAccount", new BigDecimal("1500.00"))),
                List.of(new CategorySummaryDto("RENDA_FIXA", new BigDecimal("3000.00"))),
                new BigDecimal("800.00")
        );
        when(dashboardService.getDashboard(any())).thenReturn(dto);

        mockMvc.perform(get("/dashboard").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pockets.length()").value(1))
                .andExpect(jsonPath("$.pockets[0].category").value("BankAccount"))
                .andExpect(jsonPath("$.pockets[0].total").value(1500.00))
                .andExpect(jsonPath("$.investments[0].category").value("RENDA_FIXA"))
                .andExpect(jsonPath("$.totalOpenBills").value(800.00));
    }

    @Test
    void getDashboard_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // GET /dashboard/summary
    // -------------------------------------------------------------------------

    @Test
    void getFinancialSummary_ShouldReturn200WithData() throws Exception {
        FinancialSummaryDto dto = new FinancialSummaryDto(
                List.of(new CategorySummaryDto("Salário", new BigDecimal("4000.00"))),
                List.of(new CategorySummaryDto("Alimentação", new BigDecimal("500.00")))
        );
        when(dashboardService.getFinancialSummary(any(), any(), any())).thenReturn(dto);

        mockMvc.perform(get("/dashboard/summary")
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2025-01-31")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomes.length()").value(1))
                .andExpect(jsonPath("$.incomes[0].category").value("Salário"))
                .andExpect(jsonPath("$.expenses[0].category").value("Alimentação"));
    }

    @Test
    void getFinancialSummary_WithoutDates_ShouldReturn200() throws Exception {
        when(dashboardService.getFinancialSummary(any(), any(), any()))
                .thenReturn(new FinancialSummaryDto(List.of(), List.of()));

        mockMvc.perform(get("/dashboard/summary")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk());
    }

    @Test
    void getFinancialSummary_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/dashboard/summary"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // GET /dashboard/monthly
    // -------------------------------------------------------------------------

    @Test
    void getMonthlyFinancialSummary_ShouldReturn200WithData() throws Exception {
        List<MonthlyFinancialSummaryDto> result = List.of(
                new MonthlyFinancialSummaryDto("2025-01", new BigDecimal("4000.00"), new BigDecimal("800.00")),
                new MonthlyFinancialSummaryDto("2025-02", new BigDecimal("3500.00"), new BigDecimal("700.00"))
        );
        when(dashboardService.getMonthlyFinancialSummary(any(), any(), any())).thenReturn(result);

        mockMvc.perform(get("/dashboard/monthly")
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2025-02-28")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].month").value("2025-01"))
                .andExpect(jsonPath("$[0].totalIncome").value(4000.00))
                .andExpect(jsonPath("$[0].totalExpense").value(800.00));
    }

    @Test
    void getMonthlyFinancialSummary_WithoutDates_ShouldReturn200() throws Exception {
        when(dashboardService.getMonthlyFinancialSummary(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/monthly")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk());
    }

    @Test
    void getMonthlyFinancialSummary_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/dashboard/monthly"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
// GET /dashboard/issuer-risk
// -------------------------------------------------------------------------

    @Test
    void getIssuerRiskSummary_ShouldReturn200WithData() throws Exception {
        List<IssuerRiskSummaryDto> dto = List.of(
                new IssuerRiskSummaryDto("Banco Teste", new BigDecimal("1500.00")),
                new IssuerRiskSummaryDto("Corretora XP", new BigDecimal("3000.00"))
        );
        when(dashboardService.getIssuerRiskSummary(any())).thenReturn(dto);

        mockMvc.perform(get("/dashboard/issuer-risk")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].legalEntityName").value("Banco Teste"))
                .andExpect(jsonPath("$[0].totalCurrentValue").value(1500.00))
                .andExpect(jsonPath("$[1].legalEntityName").value("Corretora XP"));
    }

    @Test
    void getIssuerRiskSummary_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/dashboard/issuer-risk"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // GET /dashboard/indexer-summary
    // -------------------------------------------------------------------------

    @Test
    void getIndexerSummary_ShouldReturn200WithData() throws Exception {
        List<IndexerSummaryDto> dto = List.of(
                new IndexerSummaryDto(Indexer.CDI, new BigDecimal("1500.00")),
                new IndexerSummaryDto(Indexer.IPCA, new BigDecimal("2000.00"))
        );
        when(dashboardService.getIndexerSummary(any())).thenReturn(dto);

        mockMvc.perform(get("/dashboard/indexer-summary")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].indexer").value("CDI"))
                .andExpect(jsonPath("$[0].totalCurrentValue").value(1500.00))
                .andExpect(jsonPath("$[1].indexer").value("IPCA"));
    }

    @Test
    void getIndexerSummary_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/dashboard/indexer-summary"))
                .andExpect(status().isForbidden());
    }
}