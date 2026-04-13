package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.category.CategoryDto;
import io.github.ronaldobertolucci.unita.dto.pocket.TransactionDto;
import io.github.ronaldobertolucci.unita.dto.pocket.TransferCreateDto;
import io.github.ronaldobertolucci.unita.dto.pocket.TransferDto;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.pocket.TransferService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransferController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private TransferService transferService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CategoryDto categoryDto() {
        return new CategoryDto(1L, "Transferência", CategoryType.NEUTRAL, true);
    }

    private TransferDto transferDto() {
        TransactionDto source = new TransactionDto(1L, new BigDecimal("200.00"),
                Direction.EXPENSE, LocalDate.now(), "Transferência", categoryDto());
        TransactionDto target = new TransactionDto(2L, new BigDecimal("200.00"),
                Direction.INCOME, LocalDate.now(), "Transferência", categoryDto());
        return new TransferDto(source, target);
    }

    // -------------------------------------------------------------------------
    // Transfer
    // -------------------------------------------------------------------------

    @Test
    void transfer_WhenDataIsValid_ShouldReturn201() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 2L,
                new BigDecimal("200.00"), "Transferência");
        when(transferService.transfer(any(), any())).thenReturn(transferDto());

        mockMvc.perform(post("/transfers")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceTransaction.direction").value("EXPENSE"))
                .andExpect(jsonPath("$.targetTransaction.direction").value("INCOME"))
                .andExpect(jsonPath("$.sourceTransaction.amount").value(200.00));
    }

    @Test
    void transfer_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(null, null, null, "");

        mockMvc.perform(post("/transfers")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_WhenSourcePocketNotFound_ShouldReturn404() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(99L, 2L,
                new BigDecimal("200.00"), "Transferência");
        when(transferService.transfer(any(), any()))
                .thenThrow(new EntityNotFoundException("Source pocket not found"));

        mockMvc.perform(post("/transfers")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_WhenTargetPocketNotFound_ShouldReturn404() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 99L,
                new BigDecimal("200.00"), "Transferência");
        when(transferService.transfer(any(), any()))
                .thenThrow(new EntityNotFoundException("Target pocket not found"));

        mockMvc.perform(post("/transfers")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_WhenInvalidPocketType_ShouldReturn400() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 2L,
                new BigDecimal("200.00"), "Transferência");
        when(transferService.transfer(any(), any()))
                .thenThrow(new IllegalArgumentException("Source pocket must be a BankAccount or Cash"));

        mockMvc.perform(post("/transfers")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_WhenSamePocket_ShouldReturn400() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 1L,
                new BigDecimal("200.00"), "Transferência");
        when(transferService.transfer(any(), any()))
                .thenThrow(new IllegalArgumentException("Source and target pockets must be different"));

        mockMvc.perform(post("/transfers")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_WhenNoSharedGroup_ShouldReturn400() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 2L,
                new BigDecimal("200.00"), "Transferência");
        when(transferService.transfer(any(), any()))
                .thenThrow(new IllegalArgumentException("Source and target pocket owners must share a group"));

        mockMvc.perform(post("/transfers")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_WhenInsufficientBalance_ShouldReturn400() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 2L,
                new BigDecimal("9999.00"), "Transferência");
        when(transferService.transfer(any(), any()))
                .thenThrow(new IllegalArgumentException("Insufficient balance in source pocket"));

        mockMvc.perform(post("/transfers")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_WhenUnauthenticated_ShouldReturn403() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 2L,
                new BigDecimal("200.00"), "Transferência");

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // Fgts
    // -------------------------------------------------------------------------

    @Test
    void fgts_WhenDataIsValid_ShouldReturn201() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 2L,
                new BigDecimal("200.00"), "Transferência");
        when(transferService.fgtsWithdrawal(any(), any())).thenReturn(transferDto());

        mockMvc.perform(post("/transfers/fgts/withdrawal")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceTransaction.direction").value("EXPENSE"))
                .andExpect(jsonPath("$.targetTransaction.direction").value("INCOME"))
                .andExpect(jsonPath("$.sourceTransaction.amount").value(200.00));
    }

    @Test
    void fgts_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(null, null, null, "");

        mockMvc.perform(post("/transfers/fgts/withdrawal")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fgts_WhenSourcePocketNotFound_ShouldReturn404() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(99L, 2L,
                new BigDecimal("200.00"), "Transferência");
        when(transferService.fgtsWithdrawal(any(), any()))
                .thenThrow(new EntityNotFoundException("Source pocket not found"));

        mockMvc.perform(post("/transfers/fgts/withdrawal")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void fgts_WhenTargetPocketNotFound_ShouldReturn404() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 99L,
                new BigDecimal("200.00"), "Transferência");
        when(transferService.fgtsWithdrawal(any(), any()))
                .thenThrow(new EntityNotFoundException("Target pocket not found"));

        mockMvc.perform(post("/transfers/fgts/withdrawal")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void fgts_WhenInvalidPocketType_ShouldReturn400() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 2L,
                new BigDecimal("200.00"), "Transferência");
        when(transferService.fgtsWithdrawal(any(), any()))
                .thenThrow(new IllegalArgumentException("Source pocket must be a BankAccount or Cash"));

        mockMvc.perform(post("/transfers/fgts/withdrawal")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fgts_WhenSamePocket_ShouldReturn400() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 1L,
                new BigDecimal("200.00"), "Transferência");
        when(transferService.fgtsWithdrawal(any(), any()))
                .thenThrow(new IllegalArgumentException("Source and target pockets must be different"));

        mockMvc.perform(post("/transfers/fgts/withdrawal")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fgts_WhenNoSharedGroup_ShouldReturn400() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 2L,
                new BigDecimal("200.00"), "Transferência");
        when(transferService.fgtsWithdrawal(any(), any()))
                .thenThrow(new IllegalArgumentException("Source and target pocket owners must share a group"));

        mockMvc.perform(post("/transfers/fgts/withdrawal")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fgts_WhenInsufficientBalance_ShouldReturn400() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 2L,
                new BigDecimal("9999.00"), "Transferência");
        when(transferService.fgtsWithdrawal(any(), any()))
                .thenThrow(new IllegalArgumentException("Insufficient balance in source pocket"));

        mockMvc.perform(post("/transfers/fgts/withdrawal")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fgts_WhenUnauthenticated_ShouldReturn403() throws Exception {
        TransferCreateDto dto = new TransferCreateDto(1L, 2L,
                new BigDecimal("200.00"), "Transferência");

        mockMvc.perform(post("/transfers/fgts/withdrawal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }
}