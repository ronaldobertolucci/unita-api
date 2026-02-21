package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.admin.BankAccountTypeDto;
import io.github.ronaldobertolucci.unita.dto.admin.BenefitTypeDto;
import io.github.ronaldobertolucci.unita.dto.admin.CardBrandDto;
import io.github.ronaldobertolucci.unita.dto.admin.RecurrencePeriodicityDto;
import io.github.ronaldobertolucci.unita.model.finance.PeriodicityType;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.admin.AdminService;
import io.github.ronaldobertolucci.unita.service.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;
    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserRepository userRepository;

    @Test
    void findAllBankAccountTypes_ShouldReturn200WithList() throws Exception {
        when(adminService.findAllBankAccountTypes())
                .thenReturn(List.of(new BankAccountTypeDto(1L, "Corrente"), new BankAccountTypeDto(2L, "Poupança")));

        mockMvc.perform(get("/admin/bank-account-types").with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Corrente"));
    }

    @Test
    void findAllBankAccountTypes_WhenEmpty_ShouldReturn200WithEmptyList() throws Exception {
        when(adminService.findAllBankAccountTypes()).thenReturn(List.of());

        mockMvc.perform(get("/admin/bank-account-types").with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findAllBenefitTypes_ShouldReturn200WithList() throws Exception {
        when(adminService.findAllBenefitTypes())
                .thenReturn(List.of(new BenefitTypeDto(1L, "Vale-Alimentação")));

        mockMvc.perform(get("/admin/benefit-types").with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Vale-Alimentação"));
    }

    @Test
    void findAllCardBrands_ShouldReturn200WithList() throws Exception {
        when(adminService.findAllCardBrands())
                .thenReturn(List.of(new CardBrandDto(1L, "Visa"), new CardBrandDto(2L, "Mastercard")));

        mockMvc.perform(get("/admin/card-brands").with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Visa"));
    }

    @Test
    void findAllRecurrencePeriodicities_ShouldReturn200WithList() throws Exception {
        when(adminService.findAllRecurrencePeriodicities())
                .thenReturn(List.of(new RecurrencePeriodicityDto(1L, "Mensal", PeriodicityType.MONTHLY)));

        mockMvc.perform(get("/admin/recurrence-periodicities").with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Mensal"))
                .andExpect(jsonPath("$[0].type").value("MONTHLY"));
    }

    @Test
    void findAllBankAccountTypes_WhenNotAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/admin/bank-account-types").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void findAllBankAccountTypes_WhenUnauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/admin/bank-account-types"))
                .andExpect(status().isForbidden());
    }
}