package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.card.*;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.card.CreditCardService;
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

@WebMvcTest(controllers = CreditCardController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class CreditCardControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private CreditCardService creditCardService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CreditCardDto creditCardDto() {
        return new CreditCardDto(1L, "Banco do Brasil", "1234", "Visa",
                new BigDecimal("5000.00"), 10, 15);
    }

    private CreditCardBillDto openBillDto() {
        return new CreditCardBillDto(1L, LocalDate.of(2025, 1, 10),
                LocalDate.of(2025, 2, 5), CreditCardBillStatus.OPEN,
                new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"));
    }

    private CreditCardPurchaseDto purchaseDto() {
        return new CreditCardPurchaseDto(1L, "Supermercado",
                new BigDecimal("300.00"), LocalDate.of(2025, 1, 5), 1);
    }

    private CreditCardInstallmentDto installmentDto() {
        return new CreditCardInstallmentDto(1L, 1, new BigDecimal("300.00"),
                1L, LocalDate.of(2025, 2, 5));
    }

    private CreditCardRefundDto refundDto() {
        return new CreditCardRefundDto(1L, "Estorno",
                new BigDecimal("50.00"), LocalDate.of(2025, 1, 8));
    }

    private RecurringPurchaseDto recurringPurchaseDto() {
        return new RecurringPurchaseDto(1L, "Streaming",
                new BigDecimal("49.90"), "Mensal", LocalDate.of(2025, 1, 1), null);
    }

    // -------------------------------------------------------------------------
    // CreditCard
    // -------------------------------------------------------------------------

    @Test
    void createCreditCard_WhenDataIsValid_ShouldReturn201() throws Exception {
        CreditCardCreateDto dto = new CreditCardCreateDto(
                1L, "1234", 1L, new BigDecimal("5000.00"), 10, 15);
        when(creditCardService.createCreditCard(any(), any())).thenReturn(creditCardDto());

        mockMvc.perform(post("/credit-cards").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.lastFourDigits").value("1234"))
                .andExpect(jsonPath("$.cardBrand").value("Visa"));
    }
    
    @Test
    void createCreditCard_WhenUnauthenticated_ShouldReturn403() throws Exception {
        CreditCardCreateDto dto = new CreditCardCreateDto(
                1L, "1234", 1L, new BigDecimal("5000.00"), 10, 15);
        when(creditCardService.createCreditCard(any(), any())).thenReturn(creditCardDto());

        mockMvc.perform(post("/credit-cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCreditCard_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        CreditCardCreateDto dto = new CreditCardCreateDto(null, "", null, null, null, null);

        mockMvc.perform(post("/credit-cards").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findMyCreditCards_ShouldReturn200WithList() throws Exception {
        when(creditCardService.findMyCreditCards(any())).thenReturn(List.of(creditCardDto()));

        mockMvc.perform(get("/credit-cards/my").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lastFourDigits").value("1234"));
    }

    @Test
    void findMyCreditCards_WhenNoneExist_ShouldReturn200WithEmptyList() throws Exception {
        when(creditCardService.findMyCreditCards(any())).thenReturn(List.of());

        mockMvc.perform(get("/credit-cards/my").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findMyCreditCards_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(creditCardService.findMyCreditCards(any())).thenReturn(List.of());

        mockMvc.perform(get("/credit-cards/my"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findCreditCardById_WhenExists_ShouldReturn200() throws Exception {
        when(creditCardService.findCreditCardById(eq(1L), any())).thenReturn(creditCardDto());

        mockMvc.perform(get("/credit-cards/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.legalEntityCorporateName").value("Banco do Brasil"));
    }

    @Test
    void findCreditCardById_WhenNotFound_ShouldReturn404() throws Exception {
        when(creditCardService.findCreditCardById(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Credit card not found"));

        mockMvc.perform(get("/credit-cards/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void findCreditCardById_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(creditCardService.findCreditCardById(eq(1L), any())).thenReturn(creditCardDto());

        mockMvc.perform(get("/credit-cards/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCreditCard_WhenBothFieldsProvided_ShouldReturn200() throws Exception {
        CreditCardUpdateDto dto = new CreditCardUpdateDto(15, 20);
        CreditCardDto updated = new CreditCardDto(1L, "Banco do Brasil", "1234", "Visa",
                new BigDecimal("5000.00"), 15, 20);
        when(creditCardService.updateCreditCard(eq(1L), any(), any())).thenReturn(updated);

        mockMvc.perform(patch("/credit-cards/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDay").value(15))
                .andExpect(jsonPath("$.dueDay").value(20));
    }

    @Test
    void updateCreditCard_WhenOnlyClosingDayProvided_ShouldReturn200() throws Exception {
        CreditCardUpdateDto dto = new CreditCardUpdateDto(15, null);
        CreditCardDto updated = new CreditCardDto(1L, "Banco do Brasil", "1234", "Visa",
                new BigDecimal("5000.00"), 15, 15);
        when(creditCardService.updateCreditCard(eq(1L), any(), any())).thenReturn(updated);

        mockMvc.perform(patch("/credit-cards/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDay").value(15));
    }

    @Test
    void updateCreditCard_WhenOnlyDueDayProvided_ShouldReturn200() throws Exception {
        CreditCardUpdateDto dto = new CreditCardUpdateDto(null, 20);
        CreditCardDto updated = new CreditCardDto(1L, "Banco do Brasil", "1234", "Visa",
                new BigDecimal("5000.00"), 10, 20);
        when(creditCardService.updateCreditCard(eq(1L), any(), any())).thenReturn(updated);

        mockMvc.perform(patch("/credit-cards/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDay").value(20));
    }

    @Test
    void updateCreditCard_WhenBothFieldsAreNull_ShouldReturn400() throws Exception {
        CreditCardUpdateDto dto = new CreditCardUpdateDto(null, null);
        when(creditCardService.updateCreditCard(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("At least one field must be provided"));

        mockMvc.perform(patch("/credit-cards/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCreditCard_WhenNotFound_ShouldReturn404() throws Exception {
        CreditCardUpdateDto dto = new CreditCardUpdateDto(15, null);
        when(creditCardService.updateCreditCard(eq(99L), any(), any()))
                .thenThrow(new EntityNotFoundException("Credit card not found"));

        mockMvc.perform(patch("/credit-cards/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCreditCard_WhenUnauthenticated_ShouldReturn403() throws Exception {
        CreditCardUpdateDto dto = new CreditCardUpdateDto(15, 20);

        mockMvc.perform(patch("/credit-cards/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCreditCard_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(creditCardService).deleteCreditCard(eq(1L), any());

        mockMvc.perform(delete("/credit-cards/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCreditCard_WhenUnauthenticated_ShouldReturn403() throws Exception {
        doNothing().when(creditCardService).deleteCreditCard(eq(1L), any());

        mockMvc.perform(delete("/credit-cards/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCreditCard_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Credit card not found"))
                .when(creditCardService).deleteCreditCard(eq(99L), any());

        mockMvc.perform(delete("/credit-cards/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // CreditCardBill
    // -------------------------------------------------------------------------

    @Test
    void findBills_ShouldReturn200WithList() throws Exception {
        when(creditCardService.findBills(eq(1L), any())).thenReturn(List.of(openBillDto()));

        mockMvc.perform(get("/credit-cards/1/bills").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }
    
    @Test
    void findBills_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(creditCardService.findBills(eq(1L), any())).thenReturn(List.of(openBillDto()));
        
        mockMvc.perform(get("/credit-cards/1/bills"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findBillById_WhenExists_ShouldReturn200() throws Exception {
        when(creditCardService.findBillById(eq(1L), eq(1L), any())).thenReturn(openBillDto());

        mockMvc.perform(get("/credit-cards/1/bills/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.totalAmount").value(500.00));
    }

    @Test
    void findBillById_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(creditCardService.findBillById(eq(1L), eq(1L), any())).thenReturn(openBillDto());

        mockMvc.perform(get("/credit-cards/1/bills/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void findBillById_WhenNotFound_ShouldReturn404() throws Exception {
        when(creditCardService.findBillById(eq(1L), eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Bill not found"));

        mockMvc.perform(get("/credit-cards/1/bills/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void payBill_WhenDataIsValid_ShouldReturn200WithPaidStatus() throws Exception {
        CreditCardBillPayDto dto = new CreditCardBillPayDto(1L);
        CreditCardBillDto paidBill = new CreditCardBillDto(1L, LocalDate.of(2025, 1, 10),
                LocalDate.of(2025, 2, 5), CreditCardBillStatus.PAID,
                new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"));
        when(creditCardService.payBill(eq(1L), eq(1L), any(), any())).thenReturn(paidBill);

        mockMvc.perform(put("/credit-cards/1/bills/1/pay").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void payBill_WhenUnauthenticated_ShouldReturn403() throws Exception {
        CreditCardBillPayDto dto = new CreditCardBillPayDto(1L);
        CreditCardBillDto paidBill = new CreditCardBillDto(1L, LocalDate.of(2025, 1, 10),
                LocalDate.of(2025, 2, 5), CreditCardBillStatus.PAID,
                new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"));
        when(creditCardService.payBill(eq(1L), eq(1L), any(), any())).thenReturn(paidBill);

        mockMvc.perform(put("/credit-cards/1/bills/1/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void payBill_WhenPocketIdIsNull_ShouldReturn400() throws Exception {
        CreditCardBillPayDto dto = new CreditCardBillPayDto(null);

        mockMvc.perform(put("/credit-cards/1/bills/1/pay").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void payBill_WhenBillIsNotClosed_ShouldReturn409() throws Exception {
        CreditCardBillPayDto dto = new CreditCardBillPayDto(1L);
        when(creditCardService.payBill(eq(1L), eq(1L), any(), any()))
                .thenThrow(new IllegalStateException("Bill must be CLOSED to be paid"));

        mockMvc.perform(put("/credit-cards/1/bills/1/pay").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------------
    // CreditCardPurchase
    // -------------------------------------------------------------------------

    @Test
    void createPurchase_WhenDataIsValid_ShouldReturn201() throws Exception {
        CreditCardPurchaseCreateDto dto = new CreditCardPurchaseCreateDto(
                "Supermercado", new BigDecimal("300.00"), LocalDate.of(2025, 1, 5), 1);
        when(creditCardService.createPurchase(eq(1L), any(), any())).thenReturn(purchaseDto());

        mockMvc.perform(post("/credit-cards/1/purchases").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Supermercado"))
                .andExpect(jsonPath("$.installmentsCount").value(1));
    }

    @Test
    void createPurchase_WhenUnauthenticated_ShouldReturn403() throws Exception {
        CreditCardPurchaseCreateDto dto = new CreditCardPurchaseCreateDto(
                "Supermercado", new BigDecimal("300.00"), LocalDate.of(2025, 1, 5), 1);
        when(creditCardService.createPurchase(eq(1L), any(), any())).thenReturn(purchaseDto());

        mockMvc.perform(post("/credit-cards/1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPurchase_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        CreditCardPurchaseCreateDto dto = new CreditCardPurchaseCreateDto("", null, null, null);

        mockMvc.perform(post("/credit-cards/1/purchases").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findPurchases_ShouldReturn200WithList() throws Exception {
        when(creditCardService.findPurchases(eq(1L), any())).thenReturn(List.of(purchaseDto()));

        mockMvc.perform(get("/credit-cards/1/purchases").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Supermercado"));
    }

    @Test
    void findPurchases_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(creditCardService.findPurchases(eq(1L), any())).thenReturn(List.of(purchaseDto()));

        mockMvc.perform(get("/credit-cards/1/purchases"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletePurchase_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(creditCardService).deletePurchase(eq(1L), eq(1L), any());

        mockMvc.perform(delete("/credit-cards/1/purchases/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletePurchase_WhenUnauthenticated_ShouldReturn403() throws Exception {
        doNothing().when(creditCardService).deletePurchase(eq(1L), eq(1L), any());

        mockMvc.perform(delete("/credit-cards/1/purchases/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletePurchase_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Purchase not found"))
                .when(creditCardService).deletePurchase(eq(1L), eq(99L), any());

        mockMvc.perform(delete("/credit-cards/1/purchases/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // CreditCardInstallment
    // -------------------------------------------------------------------------

    @Test
    void createInstallment_WhenDataIsValid_ShouldReturn201() throws Exception {
        CreditCardInstallmentCreateDto dto = new CreditCardInstallmentCreateDto(
                1, new BigDecimal("300.00"));
        when(creditCardService.createInstallment(eq(1L), eq(1L), any(), any()))
                .thenReturn(installmentDto());

        mockMvc.perform(post("/credit-cards/1/purchases/1/installments").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.installmentNumber").value(1))
                .andExpect(jsonPath("$.creditCardBillId").value(1));
    }

    @Test
    void createInstallment_WhenUnauthenticated_ShouldReturn403() throws Exception {
        CreditCardInstallmentCreateDto dto = new CreditCardInstallmentCreateDto(
                1, new BigDecimal("300.00"));
        when(creditCardService.createInstallment(eq(1L), eq(1L), any(), any()))
                .thenReturn(installmentDto());

        mockMvc.perform(post("/credit-cards/1/purchases/1/installments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createInstallment_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        CreditCardInstallmentCreateDto dto = new CreditCardInstallmentCreateDto(null, null);

        mockMvc.perform(post("/credit-cards/1/purchases/1/installments").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findInstallments_ShouldReturn200WithList() throws Exception {
        when(creditCardService.findInstallments(eq(1L), eq(1L), any()))
                .thenReturn(List.of(installmentDto()));

        mockMvc.perform(get("/credit-cards/1/purchases/1/installments").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void findInstallments_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(creditCardService.findInstallments(eq(1L), eq(1L), any()))
                .thenReturn(List.of(installmentDto()));

        mockMvc.perform(get("/credit-cards/1/purchases/1/installments"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateInstallment_WhenDataIsValid_ShouldReturn200() throws Exception {
        CreditCardInstallmentUpdateDto dto = new CreditCardInstallmentUpdateDto(
                new BigDecimal("300.00"), 2L);
        CreditCardInstallmentDto updated = new CreditCardInstallmentDto(1L, 1,
                new BigDecimal("300.00"), 2L, LocalDate.of(2025, 3, 5));
        when(creditCardService.updateInstallment(eq(1L), eq(1L), eq(1L), any(), any()))
                .thenReturn(updated);

        mockMvc.perform(put("/credit-cards/1/purchases/1/installments/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditCardBillId").value(2));
    }

    @Test
    void updateInstallment_WhenUnauthenticated_ShouldReturn403() throws Exception {
        CreditCardInstallmentUpdateDto dto = new CreditCardInstallmentUpdateDto(
                new BigDecimal("300.00"), 2L);
        CreditCardInstallmentDto updated = new CreditCardInstallmentDto(1L, 1,
                new BigDecimal("300.00"), 2L, LocalDate.of(2025, 3, 5));
        when(creditCardService.updateInstallment(eq(1L), eq(1L), eq(1L), any(), any()))
                .thenReturn(updated);

        mockMvc.perform(put("/credit-cards/1/purchases/1/installments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateInstallment_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        CreditCardInstallmentUpdateDto dto = new CreditCardInstallmentUpdateDto(null, null);

        mockMvc.perform(put("/credit-cards/1/purchases/1/installments/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateInstallment_WhenSourceBillIsPaid_ShouldReturn409() throws Exception {
        CreditCardInstallmentUpdateDto dto = new CreditCardInstallmentUpdateDto(
                new BigDecimal("300.00"), 2L);
        when(creditCardService.updateInstallment(eq(1L), eq(1L), eq(1L), any(), any()))
                .thenThrow(new IllegalStateException("Cannot move installment from a PAID bill"));

        mockMvc.perform(put("/credit-cards/1/purchases/1/installments/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteInstallment_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(creditCardService).deleteInstallment(eq(1L), eq(1L), eq(1L), any());

        mockMvc.perform(delete("/credit-cards/1/purchases/1/installments/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteInstallment_WhenUnauthenticated_ShouldReturn403() throws Exception {
        doNothing().when(creditCardService).deleteInstallment(eq(1L), eq(1L), eq(1L), any());

        mockMvc.perform(delete("/credit-cards/1/purchases/1/installments/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteInstallment_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Installment not found"))
                .when(creditCardService).deleteInstallment(eq(1L), eq(1L), eq(99L), any());

        mockMvc.perform(delete("/credit-cards/1/purchases/1/installments/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // CreditCardRefund
    // -------------------------------------------------------------------------

    @Test
    void createRefund_WhenDataIsValid_ShouldReturn201() throws Exception {
        CreditCardRefundCreateDto dto = new CreditCardRefundCreateDto(
                "Estorno", new BigDecimal("50.00"), LocalDate.of(2025, 1, 8));
        when(creditCardService.createRefund(eq(1L), eq(1L), any(), any()))
                .thenReturn(refundDto());

        mockMvc.perform(post("/credit-cards/1/bills/1/refunds").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Estorno"))
                .andExpect(jsonPath("$.amount").value(50.00));
    }

    @Test
    void createRefund_WhenUnauthenticated_ShouldReturn403() throws Exception {
        CreditCardRefundCreateDto dto = new CreditCardRefundCreateDto(
                "Estorno", new BigDecimal("50.00"), LocalDate.of(2025, 1, 8));
        when(creditCardService.createRefund(eq(1L), eq(1L), any(), any()))
                .thenReturn(refundDto());

        mockMvc.perform(post("/credit-cards/1/bills/1/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRefund_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        CreditCardRefundCreateDto dto = new CreditCardRefundCreateDto("", null, null);

        mockMvc.perform(post("/credit-cards/1/bills/1/refunds").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findRefunds_ShouldReturn200WithList() throws Exception {
        when(creditCardService.findRefunds(eq(1L), eq(1L), any()))
                .thenReturn(List.of(refundDto()));

        mockMvc.perform(get("/credit-cards/1/bills/1/refunds").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Estorno"));
    }

    @Test
    void findRefunds_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(creditCardService.findRefunds(eq(1L), eq(1L), any()))
                .thenReturn(List.of(refundDto()));

        mockMvc.perform(get("/credit-cards/1/bills/1/refunds"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRefund_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(creditCardService).deleteRefund(eq(1L), eq(1L), eq(1L), any());

        mockMvc.perform(delete("/credit-cards/1/bills/1/refunds/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRefund_WhenUnauthenticated_ShouldReturn403() throws Exception {
        doNothing().when(creditCardService).deleteRefund(eq(1L), eq(1L), eq(1L), any());

        mockMvc.perform(delete("/credit-cards/1/bills/1/refunds/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRefund_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Refund not found"))
                .when(creditCardService).deleteRefund(eq(1L), eq(1L), eq(99L), any());

        mockMvc.perform(delete("/credit-cards/1/bills/1/refunds/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // RecurringPurchase
    // -------------------------------------------------------------------------

    @Test
    void createRecurringPurchase_WhenDataIsValid_ShouldReturn201() throws Exception {
        RecurringPurchaseCreateDto dto = new RecurringPurchaseCreateDto(
                "Streaming", new BigDecimal("49.90"), 1L, LocalDate.of(2025, 1, 1), null);
        when(creditCardService.createRecurringPurchase(eq(1L), any(), any()))
                .thenReturn(recurringPurchaseDto());

        mockMvc.perform(post("/credit-cards/1/recurring").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Streaming"))
                .andExpect(jsonPath("$.periodicity").value("Mensal"));
    }

    @Test
    void createRecurringPurchase_WhenUnauthenticated_ShouldReturn403() throws Exception {
        RecurringPurchaseCreateDto dto = new RecurringPurchaseCreateDto(
                "Streaming", new BigDecimal("49.90"), 1L, LocalDate.of(2025, 1, 1), null);
        when(creditCardService.createRecurringPurchase(eq(1L), any(), any()))
                .thenReturn(recurringPurchaseDto());

        mockMvc.perform(post("/credit-cards/1/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRecurringPurchase_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        RecurringPurchaseCreateDto dto = new RecurringPurchaseCreateDto("", null, null, null, null);

        mockMvc.perform(post("/credit-cards/1/recurring").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findRecurringPurchases_ShouldReturn200WithList() throws Exception {
        when(creditCardService.findRecurringPurchases(eq(1L), any()))
                .thenReturn(List.of(recurringPurchaseDto()));

        mockMvc.perform(get("/credit-cards/1/recurring").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Streaming"));
    }

    @Test
    void findRecurringPurchases_WhenUnauthenticated_ShouldReturn403() throws Exception {
        when(creditCardService.findRecurringPurchases(eq(1L), any()))
                .thenReturn(List.of(recurringPurchaseDto()));

        mockMvc.perform(get("/credit-cards/1/recurring"))
                .andExpect(status().isForbidden());
    }


    @Test
    void deleteRecurringPurchase_WhenExists_ShouldReturn204() throws Exception {
        doNothing().when(creditCardService).deleteRecurringPurchase(eq(1L), eq(1L), any());

        mockMvc.perform(delete("/credit-cards/1/recurring/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRecurringPurchase_WhenUnauthenticated_ShouldReturn403() throws Exception {
        doNothing().when(creditCardService).deleteRecurringPurchase(eq(1L), eq(1L), any());

        mockMvc.perform(delete("/credit-cards/1/recurring/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRecurringPurchase_WhenNotFound_ShouldReturn404() throws Exception {
        doThrow(new EntityNotFoundException("Recurring purchase not found"))
                .when(creditCardService).deleteRecurringPurchase(eq(1L), eq(99L), any());

        mockMvc.perform(delete("/credit-cards/1/recurring/99").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }
}