package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.dashboard.*;
import io.github.ronaldobertolucci.unita.model.investment.Indexer;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.group.GroupDashboardService;
import io.github.ronaldobertolucci.unita.service.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GroupDashboardController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class GroupDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private GroupDashboardService groupDashboardService;

    // -------------------------------------------------------------------------
    // GET /groups/{groupId}/dashboard
    // -------------------------------------------------------------------------

    @Test
    void getGroupDashboard_ShouldReturn200WithData() throws Exception {
        GroupMemberUserDto userDto = new GroupMemberUserDto(2L, "João", "Silva", "joao@test.com");
        GroupDashboardDto dto = new GroupDashboardDto(List.of(
                new GroupMemberDashboardDto(
                        userDto,
                        List.of(new CategorySummaryDto("Cash", new BigDecimal("500.00"))),
                        List.of(new CategorySummaryDto("RENDA_FIXA", new BigDecimal("1000.00"))),
                        new BigDecimal("300.00")
                )
        ));
        when(groupDashboardService.getGroupDashboard(eq(1L), any())).thenReturn(dto);

        mockMvc.perform(get("/groups/1/dashboard")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].user.email").value("joao@test.com"))
                .andExpect(jsonPath("$.members[0].pockets[0].category").value("Cash"))
                .andExpect(jsonPath("$.members[0].totalOpenBills").value(300.00));
    }

    @Test
    void getGroupDashboard_WhenNotMember_ShouldReturn403() throws Exception {
        when(groupDashboardService.getGroupDashboard(eq(1L), any()))
                .thenThrow(new AccessDeniedException("You are not a member of this group"));

        mockMvc.perform(get("/groups/1/dashboard")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupDashboard_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/groups/1/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupDashboard_WhenPermissionsDisabled_ShouldReturnNullFields() throws Exception {
        GroupMemberUserDto userDto = new GroupMemberUserDto(2L, "João", "Silva", "joao@test.com");
        GroupDashboardDto dto = new GroupDashboardDto(List.of(
                new GroupMemberDashboardDto(userDto, null, null, null)
        ));
        when(groupDashboardService.getGroupDashboard(eq(1L), any())).thenReturn(dto);

        mockMvc.perform(get("/groups/1/dashboard")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].pockets").doesNotExist())
                .andExpect(jsonPath("$.members[0].investments").doesNotExist())
                .andExpect(jsonPath("$.members[0].totalOpenBills").doesNotExist());
    }

    // -------------------------------------------------------------------------
    // GET /groups/{groupId}/dashboard/summary
    // -------------------------------------------------------------------------

    @Test
    void getGroupFinancialSummary_ShouldReturn200WithData() throws Exception {
        GroupMemberUserDto userDto = new GroupMemberUserDto(2L, "João", "Silva", "joao@test.com");
        GroupFinancialSummaryDto dto = new GroupFinancialSummaryDto(List.of(
                new GroupMemberFinancialSummaryDto(
                        userDto,
                        List.of(new CategorySummaryDto("Salário", new BigDecimal("4000.00"))),
                        List.of(new CategorySummaryDto("Alimentação", new BigDecimal("800.00")))
                )
        ));
        when(groupDashboardService.getGroupFinancialSummary(eq(1L), any(), any(), any())).thenReturn(dto);

        mockMvc.perform(get("/groups/1/dashboard/summary")
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2025-01-31")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].incomes[0].category").value("Salário"))
                .andExpect(jsonPath("$.members[0].expenses[0].category").value("Alimentação"));
    }

    @Test
    void getGroupFinancialSummary_WithoutDates_ShouldReturn200() throws Exception {
        when(groupDashboardService.getGroupFinancialSummary(eq(1L), any(), any(), any()))
                .thenReturn(new GroupFinancialSummaryDto(List.of()));

        mockMvc.perform(get("/groups/1/dashboard/summary")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk());
    }

    @Test
    void getGroupFinancialSummary_WhenNotMember_ShouldReturn403() throws Exception {
        when(groupDashboardService.getGroupFinancialSummary(eq(1L), any(), any(), any()))
                .thenThrow(new AccessDeniedException("You are not a member of this group"));

        mockMvc.perform(get("/groups/1/dashboard/summary")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupFinancialSummary_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/groups/1/dashboard/summary"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // GET /groups/{groupId}/dashboard/monthly
    // -------------------------------------------------------------------------

    @Test
    void getGroupMonthlyFinancialSummary_ShouldReturn200WithData() throws Exception {
        GroupMemberUserDto userDto = new GroupMemberUserDto(2L, "João", "Silva", "joao@test.com");
        GroupMonthlyDto dto = new GroupMonthlyDto(List.of(
                new GroupMemberMonthlyDto(
                        userDto,
                        List.of(new GroupMonthlyFinancialSummaryDto(
                                "2025-01", new BigDecimal("4000.00"), new BigDecimal("800.00")))
                )
        ));
        when(groupDashboardService.getGroupMonthlyFinancialSummary(eq(1L), any(), any(), any())).thenReturn(dto);

        mockMvc.perform(get("/groups/1/dashboard/monthly")
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2025-01-31")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].monthly[0].month").value("2025-01"))
                .andExpect(jsonPath("$.members[0].monthly[0].totalIncome").value(4000.00))
                .andExpect(jsonPath("$.members[0].monthly[0].totalExpense").value(800.00));
    }

    @Test
    void getGroupMonthlyFinancialSummary_WhenNotMember_ShouldReturn403() throws Exception {
        when(groupDashboardService.getGroupMonthlyFinancialSummary(eq(1L), any(), any(), any()))
                .thenThrow(new AccessDeniedException("You are not a member of this group"));

        mockMvc.perform(get("/groups/1/dashboard/monthly")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupMonthlyFinancialSummary_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/groups/1/dashboard/monthly"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
// GET /groups/{groupId}/dashboard/issuer-risk
// -------------------------------------------------------------------------

    @Test
    void getGroupIssuerRiskSummary_ShouldReturn200WithData() throws Exception {
        GroupMemberUserDto userDto = new GroupMemberUserDto(2L, "João", "Silva", "joao@test.com");
        GroupIssuerRiskDto dto = new GroupIssuerRiskDto(List.of(
                new GroupMemberIssuerRiskDto(
                        userDto,
                        List.of(new IssuerRiskSummaryDto("Banco Teste", new BigDecimal("1500.00")))
                )
        ));
        when(groupDashboardService.getGroupIssuerRiskSummary(eq(1L), any())).thenReturn(dto);

        mockMvc.perform(get("/groups/1/dashboard/issuer-risk")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].user.email").value("joao@test.com"))
                .andExpect(jsonPath("$.members[0].issuerRisk[0].legalEntityName").value("Banco Teste"))
                .andExpect(jsonPath("$.members[0].issuerRisk[0].totalCurrentValue").value(1500.00));
    }

    @Test
    void getGroupIssuerRiskSummary_WhenPermissionsDisabled_ShouldReturnNullIssuerRisk() throws Exception {
        GroupMemberUserDto userDto = new GroupMemberUserDto(2L, "João", "Silva", "joao@test.com");
        GroupIssuerRiskDto dto = new GroupIssuerRiskDto(List.of(
                new GroupMemberIssuerRiskDto(userDto, null)
        ));
        when(groupDashboardService.getGroupIssuerRiskSummary(eq(1L), any())).thenReturn(dto);

        mockMvc.perform(get("/groups/1/dashboard/issuer-risk")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].issuerRisk").doesNotExist());
    }

    @Test
    void getGroupIssuerRiskSummary_WhenNotMember_ShouldReturn403() throws Exception {
        when(groupDashboardService.getGroupIssuerRiskSummary(eq(1L), any()))
                .thenThrow(new AccessDeniedException("You are not a member of this group"));

        mockMvc.perform(get("/groups/1/dashboard/issuer-risk")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupIssuerRiskSummary_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/groups/1/dashboard/issuer-risk"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // GET /groups/{groupId}/dashboard/indexer-summary
    // -------------------------------------------------------------------------

    @Test
    void getGroupIndexerSummary_ShouldReturn200WithData() throws Exception {
        GroupMemberUserDto userDto = new GroupMemberUserDto(2L, "João", "Silva", "joao@test.com");
        GroupIndexerSummaryDto dto = new GroupIndexerSummaryDto(List.of(
                new GroupMemberIndexerSummaryDto(
                        userDto,
                        List.of(
                                new IndexerSummaryDto(Indexer.CDI, new BigDecimal("1500.00")),
                                new IndexerSummaryDto(Indexer.IPCA, new BigDecimal("2000.00"))
                        )
                )
        ));
        when(groupDashboardService.getGroupIndexerSummary(eq(1L), any())).thenReturn(dto);

        mockMvc.perform(get("/groups/1/dashboard/indexer-summary")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].user.email").value("joao@test.com"))
                .andExpect(jsonPath("$.members[0].indexerSummary[0].indexer").value("CDI"))
                .andExpect(jsonPath("$.members[0].indexerSummary[0].totalCurrentValue").value(1500.00))
                .andExpect(jsonPath("$.members[0].indexerSummary[1].indexer").value("IPCA"));
    }

    @Test
    void getGroupIndexerSummary_WhenPermissionsDisabled_ShouldReturnNullIndexerSummary() throws Exception {
        GroupMemberUserDto userDto = new GroupMemberUserDto(2L, "João", "Silva", "joao@test.com");
        GroupIndexerSummaryDto dto = new GroupIndexerSummaryDto(List.of(
                new GroupMemberIndexerSummaryDto(userDto, null)
        ));
        when(groupDashboardService.getGroupIndexerSummary(eq(1L), any())).thenReturn(dto);

        mockMvc.perform(get("/groups/1/dashboard/indexer-summary")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].indexerSummary").doesNotExist());
    }

    @Test
    void getGroupIndexerSummary_WhenNotMember_ShouldReturn403() throws Exception {
        when(groupDashboardService.getGroupIndexerSummary(eq(1L), any()))
                .thenThrow(new AccessDeniedException("You are not a member of this group"));

        mockMvc.perform(get("/groups/1/dashboard/indexer-summary")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupIndexerSummary_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/groups/1/dashboard/indexer-summary"))
                .andExpect(status().isForbidden());
    }
}