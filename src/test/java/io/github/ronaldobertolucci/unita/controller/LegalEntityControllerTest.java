package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityCreateDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LegalEntityController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class LegalEntityControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LegalEntityService legalEntityService;
    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserRepository userRepository;

    @Test
    void create_WhenValid_ShouldReturn201WithDto() throws Exception {
        LegalEntityCreateDto dto = new LegalEntityCreateDto("12345678000190", "Empresa LTDA", "Fantasia", null);
        LegalEntityDto response = new LegalEntityDto(1L, "12345678000190", "Empresa LTDA", "Fantasia", null);

        when(legalEntityService.create(any())).thenReturn(response);

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
    void findAll_ShouldReturn200WithList() throws Exception {
        when(legalEntityService.findAll()).thenReturn(List.of(
                new LegalEntityDto(1L, "11111111000101", "Empresa A", null, null),
                new LegalEntityDto(2L, "22222222000102", "Empresa B", null, null)));

        mockMvc.perform(get("/legal-entities")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void findAll_WhenEmpty_ShouldReturn200WithEmptyList() throws Exception {
        when(legalEntityService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/legal-entities")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findById_WhenExists_ShouldReturn200() throws Exception {
        LegalEntityDto dto = new LegalEntityDto(1L, "12345678000190", "Empresa LTDA", null, null);
        when(legalEntityService.findById(1L)).thenReturn(dto);

        mockMvc.perform(get("/legal-entities/1")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void findById_WhenNotExists_ShouldReturn404() throws Exception {
        when(legalEntityService.findById(99L)).thenThrow(new EntityNotFoundException("Legal entity not found"));

        mockMvc.perform(get("/legal-entities/99")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_WhenUnauthenticated_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/legal-entities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}