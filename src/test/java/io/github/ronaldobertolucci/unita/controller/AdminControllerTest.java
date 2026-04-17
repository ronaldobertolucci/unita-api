package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.admin.BankAccountTypeDto;
import io.github.ronaldobertolucci.unita.dto.admin.BenefitTypeDto;
import io.github.ronaldobertolucci.unita.dto.admin.CardBrandDto;
import io.github.ronaldobertolucci.unita.dto.category.CategoryAdminCreateDto;
import io.github.ronaldobertolucci.unita.dto.category.CategoryDto;
import io.github.ronaldobertolucci.unita.dto.category.CategoryUpdateDto;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.admin.AdminService;
import io.github.ronaldobertolucci.unita.service.category.CategoryService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    @MockitoBean
    private CategoryService categoryService;
    @Autowired
    private ObjectMapper objectMapper;

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
    void findAllBankAccountTypes_WhenNotAdmin_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/admin/bank-account-types").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void findAllBankAccountTypes_WhenUnauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/admin/bank-account-types"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // Categories (admin)
    // -------------------------------------------------------------------------

    @Test
    void createGlobalCategory_WhenDataIsValid_ShouldReturn201() throws Exception {
        CategoryAdminCreateDto dto = new CategoryAdminCreateDto("Investimentos", CategoryType.INCOME);
        CategoryDto result = new CategoryDto(10L, "Investimentos", CategoryType.INCOME, true);
        when(categoryService.createGlobalCategory(any())).thenReturn(result);

        mockMvc.perform(post("/admin/categories")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Investimentos"))
                .andExpect(jsonPath("$.global").value(true));
    }

    @Test
    void createGlobalCategory_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        CategoryAdminCreateDto dto = new CategoryAdminCreateDto("", null);

        mockMvc.perform(post("/admin/categories")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGlobalCategory_WhenDuplicate_ShouldReturn400() throws Exception {
        CategoryAdminCreateDto dto = new CategoryAdminCreateDto("Alimentação", CategoryType.EXPENSE);
        when(categoryService.createGlobalCategory(any()))
                .thenThrow(new IllegalArgumentException("Global category with this name and type already exists"));

        mockMvc.perform(post("/admin/categories")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGlobalCategory_WhenNotAdmin_ShouldReturn403() throws Exception {
        CategoryAdminCreateDto dto = new CategoryAdminCreateDto("Investimentos", CategoryType.INCOME);

        mockMvc.perform(post("/admin/categories")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateGlobalCategory_WhenDataIsValid_ShouldReturn200() throws Exception {
        CategoryUpdateDto dto = new CategoryUpdateDto("Investimentos e Renda", CategoryType.INCOME);
        CategoryDto updated = new CategoryDto(10L, "Investimentos e Renda", CategoryType.INCOME, true);
        when(categoryService.updateGlobalCategory(eq(10L), any())).thenReturn(updated);

        mockMvc.perform(patch("/admin/categories/10")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Investimentos e Renda"));
    }

    @Test
    void updateGlobalCategory_WhenNotFound_ShouldReturn404() throws Exception {
        CategoryUpdateDto dto = new CategoryUpdateDto("Alimentação", CategoryType.EXPENSE);
        when(categoryService.updateGlobalCategory(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Category not found"));

        mockMvc.perform(patch("/admin/categories/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateGlobalCategory_WhenSystem_ShouldReturn409() throws Exception {
        CategoryUpdateDto dto = new CategoryUpdateDto("Outro Nome", CategoryType.NEUTRAL);
        when(categoryService.updateGlobalCategory(eq(1L), any()))
                .thenThrow(new IllegalStateException("System categories cannot be edited"));

        mockMvc.perform(patch("/admin/categories/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteGlobalCategory_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(categoryService).deleteGlobalCategory(eq(10L));

        mockMvc.perform(delete("/admin/categories/10")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteGlobalCategory_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Category not found"))
                .when(categoryService).deleteGlobalCategory(eq(99L));

        mockMvc.perform(delete("/admin/categories/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteGlobalCategory_WhenInUse_ShouldReturn409() throws Exception {
        doThrow(new IllegalStateException("Category is in use and cannot be deleted"))
                .when(categoryService).deleteGlobalCategory(eq(10L));

        mockMvc.perform(delete("/admin/categories/10")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN")))))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteGlobalCategory_WhenSystem_ShouldReturn409() throws Exception {
        doThrow(new IllegalStateException("System categories cannot be deleted"))
                .when(categoryService).deleteGlobalCategory(eq(1L));

        mockMvc.perform(delete("/admin/categories/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("ADMIN")))))
                .andExpect(status().isConflict());
    }
}