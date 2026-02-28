package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityCreateDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityUpdateDto;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.legal.LegalEntityService;
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

@WebMvcTest(controllers = LegalEntityController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class LegalEntityControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private LegalEntityService legalEntityService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private UserRepository userRepository;

    private LegalEntityDto legalEntityDto() {
        return new LegalEntityDto(1L, "12345678000190", "Empresa LTDA", "Fantasia", null);
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    void create_WhenValid_ShouldReturn201() throws Exception {
        LegalEntityCreateDto dto = new LegalEntityCreateDto("12345678000190", "Empresa LTDA", "Fantasia", null);
        when(legalEntityService.create(any(), any())).thenReturn(legalEntityDto());

        mockMvc.perform(post("/legal-entities")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.cnpj").value("12345678000190"))
                .andExpect(jsonPath("$.corporateName").value("Empresa LTDA"));
    }

    @Test
    void create_WhenMissingRequiredFields_ShouldReturn400() throws Exception {
        LegalEntityCreateDto invalid = new LegalEntityCreateDto(null, null, null, null);

        mockMvc.perform(post("/legal-entities")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_WhenDuplicateCnpj_ShouldReturn400() throws Exception {
        LegalEntityCreateDto dto = new LegalEntityCreateDto("12345678000190", "Empresa LTDA", null, null);
        when(legalEntityService.create(any(), any()))
                .thenThrow(new IllegalArgumentException("A legal entity with this CNPJ already exists"));

        mockMvc.perform(post("/legal-entities")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/legal-entities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Test
    void findAll_ShouldReturn200WithList() throws Exception {
        when(legalEntityService.findAll(any())).thenReturn(List.of(legalEntityDto()));

        mockMvc.perform(get("/legal-entities")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cnpj").value("12345678000190"));
    }

    @Test
    void findAll_WhenEmpty_ShouldReturn200WithEmptyList() throws Exception {
        when(legalEntityService.findAll(any())).thenReturn(List.of());

        mockMvc.perform(get("/legal-entities")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findAll_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/legal-entities"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    void findById_WhenExists_ShouldReturn200() throws Exception {
        when(legalEntityService.findById(eq(1L), any())).thenReturn(legalEntityDto());

        mockMvc.perform(get("/legal-entities/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void findById_WhenNotFound_ShouldReturn404() throws Exception {
        when(legalEntityService.findById(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Legal entity not found"));

        mockMvc.perform(get("/legal-entities/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void findById_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/legal-entities/1"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_WhenValid_ShouldReturn200() throws Exception {
        LegalEntityUpdateDto dto = new LegalEntityUpdateDto("12345678000190", "Empresa Atualizada", null, null);
        LegalEntityDto updated = new LegalEntityDto(1L, "12345678000190", "Empresa Atualizada", null, null);
        when(legalEntityService.update(eq(1L), any(), any())).thenReturn(updated);

        mockMvc.perform(patch("/legal-entities/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corporateName").value("Empresa Atualizada"));
    }

    @Test
    void update_WhenNotFound_ShouldReturn404() throws Exception {
        LegalEntityUpdateDto dto = new LegalEntityUpdateDto("12345678000190", "Empresa", null, null);
        when(legalEntityService.update(eq(99L), any(), any()))
                .thenThrow(new EntityNotFoundException("Legal entity not found"));

        mockMvc.perform(patch("/legal-entities/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_WhenDuplicateCnpj_ShouldReturn400() throws Exception {
        LegalEntityUpdateDto dto = new LegalEntityUpdateDto("99999999000199", "Empresa", null, null);
        when(legalEntityService.update(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("A legal entity with this CNPJ already exists"));

        mockMvc.perform(patch("/legal-entities/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_WhenUnauthenticated_ShouldReturn403() throws Exception {
        LegalEntityUpdateDto dto = new LegalEntityUpdateDto("12345678000190", "Empresa", null, null);

        mockMvc.perform(patch("/legal-entities/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(legalEntityService).delete(eq(1L), any());

        mockMvc.perform(delete("/legal-entities/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Legal entity not found"))
                .when(legalEntityService).delete(eq(99L), any());

        mockMvc.perform(delete("/legal-entities/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_WhenInUse_ShouldReturn409() throws Exception {
        doThrow(new IllegalStateException("Legal entity is in use and cannot be deleted"))
                .when(legalEntityService).delete(eq(1L), any());

        mockMvc.perform(delete("/legal-entities/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(delete("/legal-entities/1"))
                .andExpect(status().isForbidden());
    }
}