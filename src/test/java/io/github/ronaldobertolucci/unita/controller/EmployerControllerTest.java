package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.employer.*;
import io.github.ronaldobertolucci.unita.model.employer.EmployerType;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.employer.EmployerService;
import io.github.ronaldobertolucci.unita.service.security.TokenService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = EmployerController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class EmployerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EmployerService employerService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private UserRepository userRepository;

    @Test
    void createIndividual_WhenValid_ShouldReturn201() throws Exception {
        IndividualEmployerCreateDto dto = new IndividualEmployerCreateDto("12345678901", "João Silva");
        EmployerDto response = new EmployerDto(1L, EmployerType.INDIVIDUAL, "12345678901", "João Silva");

        when(employerService.createIndividual(any())).thenReturn(response);

        mockMvc.perform(post("/employers/individual")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.type").value("INDIVIDUAL"));
    }

    @Test
    void createIndividual_WhenCpfAlreadyExists_ShouldReturn409() throws Exception {
        IndividualEmployerCreateDto dto = new IndividualEmployerCreateDto("12345678901", "João Silva");
        when(employerService.createIndividual(any())).thenThrow(new IllegalStateException("CPF already exists"));

        mockMvc.perform(post("/employers/individual")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void createIndividual_WhenMissingFields_ShouldReturn400() throws Exception {
        IndividualEmployerCreateDto invalid = new IndividualEmployerCreateDto(null, null);

        mockMvc.perform(post("/employers/individual")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLegalEntity_WhenValid_ShouldReturn201() throws Exception {
        LegalEntityEmployerCreateDto dto = new LegalEntityEmployerCreateDto(10L);
        EmployerDto response = new EmployerDto(2L, EmployerType.LEGAL_ENTITY, null, null);

        when(employerService.createLegalEntity(any())).thenReturn(response);

        mockMvc.perform(post("/employers/legal-entity")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LEGAL_ENTITY"));
    }

    @Test
    void createLegalEntity_WhenLegalEntityNotFound_ShouldReturn404() throws Exception {
        LegalEntityEmployerCreateDto dto = new LegalEntityEmployerCreateDto(99L);
        when(employerService.createLegalEntity(any())).thenThrow(new EntityNotFoundException("Not found"));

        mockMvc.perform(post("/employers/legal-entity")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAll_ShouldReturn200WithList() throws Exception {
        when(employerService.findAll()).thenReturn(List.of(
                new EmployerDto(1L, EmployerType.INDIVIDUAL, "12345678901", "João"),
                new EmployerDto(2L, EmployerType.LEGAL_ENTITY, null, null)));

        mockMvc.perform(get("/employers")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void findById_WhenExists_ShouldReturn200() throws Exception {
        EmployerDto dto = new EmployerDto(1L, EmployerType.INDIVIDUAL, "12345678901", "João");
        when(employerService.findById(1L)).thenReturn(dto);

        mockMvc.perform(get("/employers/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void findById_WhenNotExists_ShouldReturn404() throws Exception {
        when(employerService.findById(99L)).thenThrow(new EntityNotFoundException("Not found"));

        mockMvc.perform(get("/employers/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAll_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/employers"))
                .andExpect(status().isForbidden());
    }
}