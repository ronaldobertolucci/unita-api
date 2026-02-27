package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.category.CategoryCreateDto;
import io.github.ronaldobertolucci.unita.dto.category.CategoryDto;
import io.github.ronaldobertolucci.unita.dto.category.CategoryUpdateDto;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
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

@WebMvcTest(controllers = CategoryController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private CategoryService categoryService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CategoryDto expenseDto() {
        return new CategoryDto(1L, "Alimentação", CategoryType.EXPENSE, false);
    }

    private CategoryDto incomeDto() {
        return new CategoryDto(2L, "Salário", CategoryType.INCOME, true);
    }

    // -------------------------------------------------------------------------
    // findAllAvailableForUser
    // -------------------------------------------------------------------------

    @Test
    void findAllAvailableForUser_ShouldReturn200WithList() throws Exception {
        when(categoryService.findAllAvailableForUser(any()))
                .thenReturn(List.of(expenseDto(), incomeDto()));

        mockMvc.perform(get("/categories")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alimentação"))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$[1].global").value(true));
    }

    @Test
    void findAllAvailableForUser_WhenEmpty_ShouldReturn200WithEmptyList() throws Exception {
        when(categoryService.findAllAvailableForUser(any())).thenReturn(List.of());

        mockMvc.perform(get("/categories")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findAllAvailableForUser_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // createCategory
    // -------------------------------------------------------------------------

    @Test
    void createCategory_WhenDataIsValid_ShouldReturn201() throws Exception {
        CategoryCreateDto dto = new CategoryCreateDto("Alimentação", CategoryType.EXPENSE);
        when(categoryService.createCategory(any(), any())).thenReturn(expenseDto());

        mockMvc.perform(post("/categories")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alimentação"))
                .andExpect(jsonPath("$.type").value("EXPENSE"));
    }

    @Test
    void createCategory_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        CategoryCreateDto dto = new CategoryCreateDto("", null);

        mockMvc.perform(post("/categories")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCategory_WhenDuplicate_ShouldReturn400() throws Exception {
        CategoryCreateDto dto = new CategoryCreateDto("Alimentação", CategoryType.EXPENSE);
        when(categoryService.createCategory(any(), any()))
                .thenThrow(new IllegalArgumentException("Category with this name and type already exists"));

        mockMvc.perform(post("/categories")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCategory_WhenUnauthenticated_ShouldReturn403() throws Exception {
        CategoryCreateDto dto = new CategoryCreateDto("Alimentação", CategoryType.EXPENSE);

        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // updateCategory
    // -------------------------------------------------------------------------

    @Test
    void updateCategory_WhenDataIsValid_ShouldReturn200() throws Exception {
        CategoryUpdateDto dto = new CategoryUpdateDto("Alimentação e Bebidas", CategoryType.EXPENSE);
        CategoryDto updated = new CategoryDto(1L, "Alimentação e Bebidas", CategoryType.EXPENSE, false);
        when(categoryService.updateCategory(eq(1L), any(), any())).thenReturn(updated);

        mockMvc.perform(patch("/categories/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alimentação e Bebidas"));
    }

    @Test
    void updateCategory_WhenNotFound_ShouldReturn404() throws Exception {
        CategoryUpdateDto dto = new CategoryUpdateDto("Alimentação", CategoryType.EXPENSE);
        when(categoryService.updateCategory(eq(99L), any(), any()))
                .thenThrow(new EntityNotFoundException("Category not found"));

        mockMvc.perform(patch("/categories/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCategory_WhenSystem_ShouldReturn409() throws Exception {
        CategoryUpdateDto dto = new CategoryUpdateDto("Outro Nome", CategoryType.NEUTRAL);
        when(categoryService.updateCategory(eq(1L), any(), any()))
                .thenThrow(new IllegalStateException("System categories cannot be edited"));

        mockMvc.perform(patch("/categories/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateCategory_WhenUnauthenticated_ShouldReturn403() throws Exception {
        CategoryUpdateDto dto = new CategoryUpdateDto("Alimentação", CategoryType.EXPENSE);

        mockMvc.perform(patch("/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // deleteCategory
    // -------------------------------------------------------------------------

    @Test
    void deleteCategory_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(categoryService).deleteCategory(eq(1L), any());

        mockMvc.perform(delete("/categories/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCategory_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Category not found"))
                .when(categoryService).deleteCategory(eq(99L), any());

        mockMvc.perform(delete("/categories/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCategory_WhenInUse_ShouldReturn409() throws Exception {
        doThrow(new IllegalStateException("Category is in use and cannot be deleted"))
                .when(categoryService).deleteCategory(eq(1L), any());

        mockMvc.perform(delete("/categories/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteCategory_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(delete("/categories/1"))
                .andExpect(status().isForbidden());
    }
}