package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.category.CategoryDto;
import io.github.ronaldobertolucci.unita.dto.pocket.*;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import io.github.ronaldobertolucci.unita.model.finance.Direction;
import io.github.ronaldobertolucci.unita.model.pocket.BankAccountStatus;
import io.github.ronaldobertolucci.unita.model.pocket.BenefitAccountStatus;
import io.github.ronaldobertolucci.unita.model.pocket.FgtsEmployerAccountStatus;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.pocket.PocketService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PocketController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class PocketControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private PocketService pocketService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PocketSummaryDto pocketSummaryDto() {
        return new PocketSummaryDto(1L, "BankAccount", "Banco do Brasil - 12345", true,
                new BigDecimal("1500.00"));
    }

    private BankAccountDto bankAccountDto() {
        return new BankAccountDto(1L, "Banco do Brasil", "12345",
                "0001", "Corrente", BankAccountStatus.ACTIVE);
    }

    private BenefitAccountDto benefitAccountDto() {
        return new BenefitAccountDto(1L, "Ticket Refeição",
                "Vale-Refeição", BenefitAccountStatus.ACTIVE);
    }

    private FgtsEmployerAccountDto fgtsAccountDto() {
        return new FgtsEmployerAccountDto(1L, "Empresa XYZ",
                LocalDate.of(2020, 3, 1), null, FgtsEmployerAccountStatus.ACTIVE);
    }

    private CashDto cashDto() {
        return new CashDto(1L, new BigDecimal("250.00"));
    }

    private CategoryDto categoryDto() {
        return new CategoryDto(1L, "Salário", CategoryType.INCOME, true);
    }

    private TransactionDto transactionDto() {
        return new TransactionDto(1L, new BigDecimal("100.00"), Direction.INCOME,
                LocalDate.of(2025, 1, 10), "Salário", categoryDto());
    }

    // -------------------------------------------------------------------------
    // Pocket (geral)
    // -------------------------------------------------------------------------

    @Test
    void findMyPockets_ShouldReturn200WithList() throws Exception {
        when(pocketService.findMyPockets(any())).thenReturn(List.of(pocketSummaryDto()));

        mockMvc.perform(get("/pockets/my").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("BankAccount"))
                .andExpect(jsonPath("$[0].balance").value(1500.00));
    }

    @Test
    void findMyPockets_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(pocketService.findMyPockets(any())).thenReturn(List.of(pocketSummaryDto()));

        mockMvc.perform(get("/pockets/my"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findMyPockets_WhenNoneExist_ShouldReturn200WithEmptyList() throws Exception {
        when(pocketService.findMyPockets(any())).thenReturn(List.of());

        mockMvc.perform(get("/pockets/my").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // -------------------------------------------------------------------------
    // BankAccount
    // -------------------------------------------------------------------------

    @Test
    void createBankAccount_WhenDataIsValid_ShouldReturn201() throws Exception {
        BankAccountCreateDto dto = new BankAccountCreateDto(1L, "12345", "0001", 1L);
        when(pocketService.createBankAccount(any(), any())).thenReturn(bankAccountDto());

        mockMvc.perform(post("/pockets/bank-accounts").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("12345"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createBankAccount_WhenUnauthenticated_ShouldReturn403() throws Exception {
        BankAccountCreateDto dto = new BankAccountCreateDto(1L, "12345", "0001", 1L);
        when(pocketService.createBankAccount(any(), any())).thenReturn(bankAccountDto());

        mockMvc.perform(post("/pockets/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createBankAccount_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        BankAccountCreateDto dto = new BankAccountCreateDto(null, "", "", null);

        mockMvc.perform(post("/pockets/bank-accounts").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findBankAccountById_WhenExists_ShouldReturn200() throws Exception {
        when(pocketService.findBankAccountById(eq(1L), any())).thenReturn(bankAccountDto());

        mockMvc.perform(get("/pockets/bank-accounts/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.bankAccountType").value("Corrente"));
    }

    @Test
    void findBankAccountById_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(pocketService.findBankAccountById(eq(1L), any())).thenReturn(bankAccountDto());

        mockMvc.perform(get("/pockets/bank-accounts/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findBankAccountById_WhenNotFound_ShouldReturn404() throws Exception {
        when(pocketService.findBankAccountById(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Bank account not found"));

        mockMvc.perform(get("/pockets/bank-accounts/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateBankAccount_WhenDataIsValid_ShouldReturn200() throws Exception {
        BankAccountUpdateDto dto = new BankAccountUpdateDto(BankAccountStatus.INACTIVE);
        BankAccountDto updated = new BankAccountDto(1L, "Banco do Brasil", "12345",
                "0001", "Corrente", BankAccountStatus.INACTIVE);
        when(pocketService.updateBankAccount(eq(1L), any(), any())).thenReturn(updated);

        mockMvc.perform(put("/pockets/bank-accounts/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void updateBankAccount_WhenUnauthenticated_ShouldReturn403() throws Exception {
        BankAccountUpdateDto dto = new BankAccountUpdateDto(BankAccountStatus.INACTIVE);
        BankAccountDto updated = new BankAccountDto(1L, "Banco do Brasil", "12345",
                "0001", "Corrente", BankAccountStatus.INACTIVE);
        when(pocketService.updateBankAccount(eq(1L), any(), any())).thenReturn(updated);

        mockMvc.perform(put("/pockets/bank-accounts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateBankAccount_WhenStatusIsNull_ShouldReturn400() throws Exception {
        BankAccountUpdateDto dto = new BankAccountUpdateDto(null);

        mockMvc.perform(put("/pockets/bank-accounts/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteBankAccount_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(pocketService).deleteBankAccount(eq(1L), any());

        mockMvc.perform(delete("/pockets/bank-accounts/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteBankAccount_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Bank account not found"))
                .when(pocketService).deleteBankAccount(eq(99L), any());

        mockMvc.perform(delete("/pockets/bank-accounts/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // BenefitAccount
    // -------------------------------------------------------------------------

    @Test
    void createBenefitAccount_WhenDataIsValid_ShouldReturn201() throws Exception {
        BenefitAccountCreateDto dto = new BenefitAccountCreateDto(1L, 1L);
        when(pocketService.createBenefitAccount(any(), any())).thenReturn(benefitAccountDto());

        mockMvc.perform(post("/pockets/benefit-accounts").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.benefitType").value("Vale-Refeição"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createBenefitAccount_WhenUnauthenticated_ShouldReturn403() throws Exception {
        BenefitAccountCreateDto dto = new BenefitAccountCreateDto(1L, 1L);
        when(pocketService.createBenefitAccount(any(), any())).thenReturn(benefitAccountDto());

        mockMvc.perform(post("/pockets/benefit-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }


    @Test
    void createBenefitAccount_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        BenefitAccountCreateDto dto = new BenefitAccountCreateDto(null, null);

        mockMvc.perform(post("/pockets/benefit-accounts").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findBenefitAccountById_WhenExists_ShouldReturn200() throws Exception {
        when(pocketService.findBenefitAccountById(eq(1L), any())).thenReturn(benefitAccountDto());

        mockMvc.perform(get("/pockets/benefit-accounts/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void findBenefitAccountById_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(pocketService.findBenefitAccountById(eq(1L), any())).thenReturn(benefitAccountDto());

        mockMvc.perform(get("/pockets/benefit-accounts/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findBenefitAccountById_WhenNotFound_ShouldReturn404() throws Exception {
        when(pocketService.findBenefitAccountById(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Benefit account not found"));

        mockMvc.perform(get("/pockets/benefit-accounts/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateBenefitAccount_WhenDataIsValid_ShouldReturn200() throws Exception {
        BenefitAccountUpdateDto dto = new BenefitAccountUpdateDto(BenefitAccountStatus.INACTIVE);
        BenefitAccountDto updated = new BenefitAccountDto(1L, "Ticket Refeição",
                "Vale-Refeição", BenefitAccountStatus.INACTIVE);
        when(pocketService.updateBenefitAccount(eq(1L), any(), any())).thenReturn(updated);

        mockMvc.perform(put("/pockets/benefit-accounts/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void updateBenefitAccount_WhenUnauthenticated_ShouldReturn403() throws Exception {
        BenefitAccountUpdateDto dto = new BenefitAccountUpdateDto(BenefitAccountStatus.INACTIVE);
        BenefitAccountDto updated = new BenefitAccountDto(1L, "Ticket Refeição",
                "Vale-Refeição", BenefitAccountStatus.INACTIVE);
        when(pocketService.updateBenefitAccount(eq(1L), any(), any())).thenReturn(updated);

        mockMvc.perform(put("/pockets/benefit-accounts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateBenefitAccount_WhenStatusIsNull_ShouldReturn400() throws Exception {
        BenefitAccountUpdateDto dto = new BenefitAccountUpdateDto(null);

        mockMvc.perform(put("/pockets/benefit-accounts/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteBenefitAccount_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(pocketService).deleteBenefitAccount(eq(1L), any());

        mockMvc.perform(delete("/pockets/benefit-accounts/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteBenefitAccount_WhenUnauthenticated_ShouldReturn403() throws Exception {
        doNothing().when(pocketService).deleteBenefitAccount(eq(1L), any());

        mockMvc.perform(delete("/pockets/benefit-accounts/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteBenefitAccount_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Benefit account not found"))
                .when(pocketService).deleteBenefitAccount(eq(99L), any());

        mockMvc.perform(delete("/pockets/benefit-accounts/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // FgtsEmployerAccount
    // -------------------------------------------------------------------------

    @Test
    void createFgtsEmployerAccount_WhenDataIsValid_ShouldReturn201() throws Exception {
        FgtsEmployerAccountCreateDto dto = new FgtsEmployerAccountCreateDto(
                1L, LocalDate.of(2020, 3, 1), null);
        when(pocketService.createFgtsEmployerAccount(any(), any())).thenReturn(fgtsAccountDto());

        mockMvc.perform(post("/pockets/fgts").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employerName").value("Empresa XYZ"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createFgtsEmployerAccount_WhenUnauthenticated_ShouldReturn403() throws Exception {
        FgtsEmployerAccountCreateDto dto = new FgtsEmployerAccountCreateDto(
                1L, LocalDate.of(2020, 3, 1), null);
        when(pocketService.createFgtsEmployerAccount(any(), any())).thenReturn(fgtsAccountDto());

        mockMvc.perform(post("/pockets/fgts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createFgtsEmployerAccount_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        FgtsEmployerAccountCreateDto dto = new FgtsEmployerAccountCreateDto(null, null, null);

        mockMvc.perform(post("/pockets/fgts").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findFgtsEmployerAccountById_WhenExists_ShouldReturn200() throws Exception {
        when(pocketService.findFgtsEmployerAccountById(eq(1L), any())).thenReturn(fgtsAccountDto());

        mockMvc.perform(get("/pockets/fgts/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.employerName").value("Empresa XYZ"));
    }

    @Test
    void findFgtsEmployerAccountById_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(pocketService.findFgtsEmployerAccountById(eq(1L), any())).thenReturn(fgtsAccountDto());

        mockMvc.perform(get("/pockets/fgts/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findFgtsEmployerAccountById_WhenNotFound_ShouldReturn404() throws Exception {
        when(pocketService.findFgtsEmployerAccountById(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("FGTS account not found"));

        mockMvc.perform(get("/pockets/fgts/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateFgtsEmployerAccount_WhenDataIsValid_ShouldReturn200() throws Exception {
        FgtsEmployerAccountUpdateDto dto = new FgtsEmployerAccountUpdateDto(
                FgtsEmployerAccountStatus.INACTIVE, LocalDate.of(2025, 1, 31));
        FgtsEmployerAccountDto updated = new FgtsEmployerAccountDto(1L, "Empresa XYZ",
                LocalDate.of(2020, 3, 1), LocalDate.of(2025, 1, 31),
                FgtsEmployerAccountStatus.INACTIVE);
        when(pocketService.updateFgtsEmployerAccount(eq(1L), any(), any())).thenReturn(updated);

        mockMvc.perform(put("/pockets/fgts/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.dismissalDate").value("2025-01-31"));
    }

    @Test
    void updateFgtsEmployerAccount_WhenUnauthenticated_ShouldReturn403() throws Exception {
        FgtsEmployerAccountUpdateDto dto = new FgtsEmployerAccountUpdateDto(
                FgtsEmployerAccountStatus.INACTIVE, LocalDate.of(2025, 1, 31));
        FgtsEmployerAccountDto updated = new FgtsEmployerAccountDto(1L, "Empresa XYZ",
                LocalDate.of(2020, 3, 1), LocalDate.of(2025, 1, 31),
                FgtsEmployerAccountStatus.INACTIVE);
        when(pocketService.updateFgtsEmployerAccount(eq(1L), any(), any())).thenReturn(updated);

        mockMvc.perform(put("/pockets/fgts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateFgtsEmployerAccount_WhenStatusIsNull_ShouldReturn400() throws Exception {
        FgtsEmployerAccountUpdateDto dto = new FgtsEmployerAccountUpdateDto(null, null);

        mockMvc.perform(put("/pockets/fgts/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteFgtsEmployerAccount_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(pocketService).deleteFgtsEmployerAccount(eq(1L), any());

        mockMvc.perform(delete("/pockets/fgts/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteFgtsEmployerAccount_WhenUnauthenticated_ShouldReturn403() throws Exception {
        doNothing().when(pocketService).deleteFgtsEmployerAccount(eq(1L), any());

        mockMvc.perform(delete("/pockets/fgts/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteFgtsEmployerAccount_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("FGTS account not found"))
                .when(pocketService).deleteFgtsEmployerAccount(eq(99L), any());

        mockMvc.perform(delete("/pockets/fgts/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Cash
    // -------------------------------------------------------------------------

    @Test
    void createCash_WhenNoneExists_ShouldReturn201() throws Exception {
        when(pocketService.createCash(any())).thenReturn(cashDto());

        mockMvc.perform(post("/pockets/cash").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.balance").value(250.00));
    }

    @Test
    void createCash_WhenUnauthenticated_ShouldReturn4031() throws Exception {
        when(pocketService.createCash(any())).thenReturn(cashDto());

        mockMvc.perform(post("/pockets/cash"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCash_WhenAlreadyExists_ShouldReturn409() throws Exception {
        when(pocketService.createCash(any()))
                .thenThrow(new IllegalStateException("User already has a Cash pocket"));

        mockMvc.perform(post("/pockets/cash").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isConflict());
    }

    @Test
    void findCash_WhenExists_ShouldReturn200() throws Exception {
        when(pocketService.findCash(any())).thenReturn(cashDto());

        mockMvc.perform(get("/pockets/cash").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.balance").value(250.00));
    }

    @Test
    void findCash_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(pocketService.findCash(any())).thenReturn(cashDto());

        mockMvc.perform(get("/pockets/cash"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findCash_WhenNotFound_ShouldReturn404() throws Exception {
        when(pocketService.findCash(any()))
                .thenThrow(new EntityNotFoundException("Cash not found"));

        mockMvc.perform(get("/pockets/cash").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Transaction
    // -------------------------------------------------------------------------

    @Test
    void createTransaction_WhenDataIsValid_ShouldReturn201() throws Exception {
        TransactionCreateDto dto = new TransactionCreateDto(
                new BigDecimal("100.00"), Direction.INCOME,
                LocalDate.of(2025, 1, 10), "Salário", 1L);
        when(pocketService.createTransaction(eq(1L), any(), any())).thenReturn(transactionDto());

        mockMvc.perform(post("/pockets/1/transactions").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Salário"))
                .andExpect(jsonPath("$.direction").value("INCOME"));
    }

    @Test
    void createTransaction_WhenUnauthenticated_ShouldReturn403() throws Exception {
        TransactionCreateDto dto = new TransactionCreateDto(
                new BigDecimal("100.00"), Direction.INCOME,
                LocalDate.of(2025, 1, 10), "Salário", 1L);
        when(pocketService.createTransaction(eq(1L), any(), any())).thenReturn(transactionDto());

        mockMvc.perform(post("/pockets/1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTransaction_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        TransactionCreateDto dto = new TransactionCreateDto(null, null, null, "", null);

        mockMvc.perform(post("/pockets/1/transactions").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findTransactions_WithoutParams_ShouldReturn200WithList() throws Exception {
        when(pocketService.findTransactions(eq(1L), isNull(), isNull(), any()))
                .thenReturn(List.of(transactionDto()));

        mockMvc.perform(get("/pockets/1/transactions")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Salário"));
    }

    @Test
    void findTransactions_WithStartAndEndDate_ShouldReturn200WithFilteredList() throws Exception {
        when(pocketService.findTransactions(eq(1L), eq(LocalDate.of(2025, 1, 1)),
                eq(LocalDate.of(2025, 1, 31)), any()))
                .thenReturn(List.of(transactionDto()));

        mockMvc.perform(get("/pockets/1/transactions")
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2025-01-31")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void findTransactions_WithOnlyStartDate_ShouldReturn200() throws Exception {
        when(pocketService.findTransactions(eq(1L), eq(LocalDate.of(2025, 1, 1)), isNull(), any()))
                .thenReturn(List.of(transactionDto()));

        mockMvc.perform(get("/pockets/1/transactions")
                        .param("startDate", "2025-01-01")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void findTransactions_WithOnlyEndDate_ShouldReturn200() throws Exception {
        when(pocketService.findTransactions(eq(1L), isNull(), eq(LocalDate.of(2025, 1, 31)), any()))
                .thenReturn(List.of(transactionDto()));

        mockMvc.perform(get("/pockets/1/transactions")
                        .param("endDate", "2025-01-31")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void findTransactions_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/pockets/1/transactions"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findTransactions_WhenPocketNotFound_ShouldReturn404() throws Exception {
        when(pocketService.findTransactions(eq(99L), isNull(), isNull(), any()))
                .thenThrow(new EntityNotFoundException("Pocket not found"));

        mockMvc.perform(get("/pockets/99/transactions")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void findBalance_ShouldReturn200WithValue() throws Exception {
        when(pocketService.findBalance(eq(1L), any())).thenReturn(new BigDecimal("1500.00"));

        mockMvc.perform(get("/pockets/1/balance").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(1500.00));
    }

    @Test
    void findBalance_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(pocketService.findBalance(eq(1L), any())).thenReturn(new BigDecimal("1500.00"));

        mockMvc.perform(get("/pockets/1/balance"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findBalance_WhenPocketNotFound_ShouldReturn404() throws Exception {
        when(pocketService.findBalance(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Pocket not found"));

        mockMvc.perform(get("/pockets/99/balance").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTransaction_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(pocketService).deleteTransaction(eq(1L), eq(1L), any());

        mockMvc.perform(delete("/pockets/1/transactions/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTransaction_WhenUnauthenticated_ShouldReturn403() throws Exception {
        doNothing().when(pocketService).deleteTransaction(eq(1L), eq(1L), any());

        mockMvc.perform(delete("/pockets/1/transactions/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteTransaction_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Transaction not found"))
                .when(pocketService).deleteTransaction(eq(1L), eq(99L), any());

        mockMvc.perform(delete("/pockets/1/transactions/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }
}