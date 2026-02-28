package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.employer.*;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmployerController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class EmployerControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmployerService employerService;
    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserRepository userRepository;

    private IndividualEmployerDto individualDto() {
        return new IndividualEmployerDto(1L, "12345678901", "João Silva");
    }

    private LegalEntityEmployerDto legalEntityEmployerDto() {
        return new LegalEntityEmployerDto(2L,
                new LegalEntityDto(10L, "12345678000190", "Empresa LTDA", null, null));
    }

    // -------------------------------------------------------------------------
    // IndividualEmployer — create
    // -------------------------------------------------------------------------

    @Test
    void createIndividual_WhenValid_ShouldReturn201() throws Exception {
        IndividualEmployerCreateDto dto = new IndividualEmployerCreateDto("12345678901", "João Silva");
        when(employerService.createIndividual(any(), any())).thenReturn(individualDto());

        mockMvc.perform(post("/employers/individual")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.cpf").value("12345678901"));
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
    void createIndividual_WhenCpfAlreadyExists_ShouldReturn400() throws Exception {
        IndividualEmployerCreateDto dto = new IndividualEmployerCreateDto("12345678901", "João Silva");
        when(employerService.createIndividual(any(), any()))
                .thenThrow(new IllegalArgumentException("An employer with this CPF already exists"));

        mockMvc.perform(post("/employers/individual")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createIndividual_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/employers/individual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // IndividualEmployer — findAll
    // -------------------------------------------------------------------------

    @Test
    void findAllIndividual_ShouldReturn200WithList() throws Exception {
        when(employerService.findAllIndividual(any())).thenReturn(List.of(individualDto()));

        mockMvc.perform(get("/employers/individual")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cpf").value("12345678901"));
    }

    @Test
    void findAllIndividual_WhenEmpty_ShouldReturn200WithEmptyList() throws Exception {
        when(employerService.findAllIndividual(any())).thenReturn(List.of());

        mockMvc.perform(get("/employers/individual")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findAllIndividual_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/employers/individual"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // IndividualEmployer — findById
    // -------------------------------------------------------------------------

    @Test
    void findIndividualById_WhenExists_ShouldReturn200() throws Exception {
        when(employerService.findIndividualById(eq(1L), any())).thenReturn(individualDto());

        mockMvc.perform(get("/employers/individual/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void findIndividualById_WhenNotFound_ShouldReturn404() throws Exception {
        when(employerService.findIndividualById(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Employer not found"));

        mockMvc.perform(get("/employers/individual/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // IndividualEmployer — update
    // -------------------------------------------------------------------------

    @Test
    void updateIndividual_WhenValid_ShouldReturn200() throws Exception {
        IndividualEmployerUpdateDto dto = new IndividualEmployerUpdateDto("12345678901", "João Atualizado");
        IndividualEmployerDto updated = new IndividualEmployerDto(1L, "12345678901", "João Atualizado");
        when(employerService.updateIndividual(eq(1L), any(), any())).thenReturn(updated);

        mockMvc.perform(patch("/employers/individual/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("João Atualizado"));
    }

    @Test
    void updateIndividual_WhenNotFound_ShouldReturn404() throws Exception {
        IndividualEmployerUpdateDto dto = new IndividualEmployerUpdateDto("12345678901", "João");
        when(employerService.updateIndividual(eq(99L), any(), any()))
                .thenThrow(new EntityNotFoundException("Employer not found"));

        mockMvc.perform(patch("/employers/individual/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateIndividual_WhenDuplicateCpf_ShouldReturn400() throws Exception {
        IndividualEmployerUpdateDto dto = new IndividualEmployerUpdateDto("99999999999", "João");
        when(employerService.updateIndividual(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("An employer with this CPF already exists"));

        mockMvc.perform(patch("/employers/individual/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateIndividual_WhenUnauthenticated_ShouldReturn403() throws Exception {
        IndividualEmployerUpdateDto dto = new IndividualEmployerUpdateDto("12345678901", "João");

        mockMvc.perform(patch("/employers/individual/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // IndividualEmployer — delete
    // -------------------------------------------------------------------------

    @Test
    void deleteIndividual_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(employerService).deleteIndividual(eq(1L), any());

        mockMvc.perform(delete("/employers/individual/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteIndividual_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Employer not found"))
                .when(employerService).deleteIndividual(eq(99L), any());

        mockMvc.perform(delete("/employers/individual/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteIndividual_WhenInUse_ShouldReturn409() throws Exception {
        doThrow(new IllegalStateException("Employer is in use and cannot be deleted"))
                .when(employerService).deleteIndividual(eq(1L), any());

        mockMvc.perform(delete("/employers/individual/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteIndividual_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(delete("/employers/individual/1"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // LegalEntityEmployer — create
    // -------------------------------------------------------------------------

    @Test
    void createLegalEntity_WhenValid_ShouldReturn201() throws Exception {
        LegalEntityEmployerCreateDto dto = new LegalEntityEmployerCreateDto(10L);
        when(employerService.createLegalEntity(any(), any())).thenReturn(legalEntityEmployerDto());

        mockMvc.perform(post("/employers/legal-entity")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.legalEntity.cnpj").value("12345678000190"));
    }

    @Test
    void createLegalEntity_WhenMissingFields_ShouldReturn400() throws Exception {
        LegalEntityEmployerCreateDto invalid = new LegalEntityEmployerCreateDto(null);

        mockMvc.perform(post("/employers/legal-entity")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLegalEntity_WhenLegalEntityNotFound_ShouldReturn404() throws Exception {
        LegalEntityEmployerCreateDto dto = new LegalEntityEmployerCreateDto(99L);
        when(employerService.createLegalEntity(any(), any()))
                .thenThrow(new EntityNotFoundException("Legal entity not found"));

        mockMvc.perform(post("/employers/legal-entity")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createLegalEntity_WhenAlreadyExists_ShouldReturn400() throws Exception {
        LegalEntityEmployerCreateDto dto = new LegalEntityEmployerCreateDto(10L);
        when(employerService.createLegalEntity(any(), any()))
                .thenThrow(new IllegalArgumentException("An employer for this legal entity already exists"));

        mockMvc.perform(post("/employers/legal-entity")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLegalEntity_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/employers/legal-entity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // LegalEntityEmployer — findAll
    // -------------------------------------------------------------------------

    @Test
    void findAllLegalEntity_ShouldReturn200WithList() throws Exception {
        when(employerService.findAllLegalEntity(any())).thenReturn(List.of(legalEntityEmployerDto()));

        mockMvc.perform(get("/employers/legal-entity")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].legalEntity.corporateName").value("Empresa LTDA"));
    }

    @Test
    void findAllLegalEntity_WhenEmpty_ShouldReturn200WithEmptyList() throws Exception {
        when(employerService.findAllLegalEntity(any())).thenReturn(List.of());

        mockMvc.perform(get("/employers/legal-entity")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findAllLegalEntity_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/employers/legal-entity"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // LegalEntityEmployer — findById
    // -------------------------------------------------------------------------

    @Test
    void findLegalEntityById_WhenExists_ShouldReturn200() throws Exception {
        when(employerService.findLegalEntityById(eq(2L), any())).thenReturn(legalEntityEmployerDto());

        mockMvc.perform(get("/employers/legal-entity/2")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void findLegalEntityById_WhenNotFound_ShouldReturn404() throws Exception {
        when(employerService.findLegalEntityById(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Employer not found"));

        mockMvc.perform(get("/employers/legal-entity/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // LegalEntityEmployer — update
    // -------------------------------------------------------------------------

    @Test
    void updateLegalEntity_WhenValid_ShouldReturn200() throws Exception {
        LegalEntityEmployerUpdateDto dto = new LegalEntityEmployerUpdateDto(20L);
        when(employerService.updateLegalEntity(eq(2L), any(), any())).thenReturn(legalEntityEmployerDto());

        mockMvc.perform(patch("/employers/legal-entity/2")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void updateLegalEntity_WhenNotFound_ShouldReturn404() throws Exception {
        LegalEntityEmployerUpdateDto dto = new LegalEntityEmployerUpdateDto(10L);
        when(employerService.updateLegalEntity(eq(99L), any(), any()))
                .thenThrow(new EntityNotFoundException("Employer not found"));

        mockMvc.perform(patch("/employers/legal-entity/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateLegalEntity_WhenAlreadyExists_ShouldReturn400() throws Exception {
        LegalEntityEmployerUpdateDto dto = new LegalEntityEmployerUpdateDto(20L);
        when(employerService.updateLegalEntity(eq(2L), any(), any()))
                .thenThrow(new IllegalArgumentException("An employer for this legal entity already exists"));

        mockMvc.perform(patch("/employers/legal-entity/2")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateLegalEntity_WhenUnauthenticated_ShouldReturn403() throws Exception {
        LegalEntityEmployerUpdateDto dto = new LegalEntityEmployerUpdateDto(10L);

        mockMvc.perform(patch("/employers/legal-entity/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // LegalEntityEmployer — delete
    // -------------------------------------------------------------------------

    @Test
    void deleteLegalEntity_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(employerService).deleteLegalEntity(eq(2L), any());

        mockMvc.perform(delete("/employers/legal-entity/2")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteLegalEntity_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Employer not found"))
                .when(employerService).deleteLegalEntity(eq(99L), any());

        mockMvc.perform(delete("/employers/legal-entity/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteLegalEntity_WhenInUse_ShouldReturn409() throws Exception {
        doThrow(new IllegalStateException("Employer is in use and cannot be deleted"))
                .when(employerService).deleteLegalEntity(eq(2L), any());

        mockMvc.perform(delete("/employers/legal-entity/2")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteLegalEntity_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(delete("/employers/legal-entity/2"))
                .andExpect(status().isForbidden());
    }
}