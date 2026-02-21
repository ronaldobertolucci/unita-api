package io.github.ronaldobertolucci.unita.service.admin;

import io.github.ronaldobertolucci.unita.dto.admin.*;
import io.github.ronaldobertolucci.unita.model.finance.*;
import io.github.ronaldobertolucci.unita.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private BankAccountTypeRepository bankAccountTypeRepository;
    @Mock private BenefitTypeRepository benefitTypeRepository;
    @Mock private CardBrandRepository cardBrandRepository;
    @Mock private RecurrencePeriodicityRepository recurrencePeriodicityRepository;

    @InjectMocks private AdminService adminService;

    @Test
    void findAllBankAccountTypes_ShouldReturnMappedDtos() {
        BankAccountType type = new BankAccountType();
        type.setId(1L);
        type.setName("Corrente");
        when(bankAccountTypeRepository.findAll()).thenReturn(List.of(type));

        List<BankAccountTypeDto> result = adminService.findAllBankAccountTypes();

        assertEquals(1, result.size());
        assertEquals("Corrente", result.get(0).name());
        verify(bankAccountTypeRepository).findAll();
    }

    @Test
    void findAllBankAccountTypes_WhenEmpty_ShouldReturnEmptyList() {
        when(bankAccountTypeRepository.findAll()).thenReturn(List.of());

        assertTrue(adminService.findAllBankAccountTypes().isEmpty());
    }

    @Test
    void findAllBenefitTypes_ShouldReturnMappedDtos() {
        BenefitType type = new BenefitType();
        type.setId(1L);
        type.setName("Vale-Alimentação");
        when(benefitTypeRepository.findAll()).thenReturn(List.of(type));

        List<BenefitTypeDto> result = adminService.findAllBenefitTypes();

        assertEquals(1, result.size());
        assertEquals("Vale-Alimentação", result.get(0).name());
    }

    @Test
    void findAllBenefitTypes_WhenEmpty_ShouldReturnEmptyList() {
        when(benefitTypeRepository.findAll()).thenReturn(List.of());

        assertTrue(adminService.findAllBenefitTypes().isEmpty());
    }

    @Test
    void findAllCardBrands_ShouldReturnMappedDtos() {
        CardBrand brand = new CardBrand();
        brand.setId(1L);
        brand.setName("Visa");
        when(cardBrandRepository.findAll()).thenReturn(List.of(brand));

        List<CardBrandDto> result = adminService.findAllCardBrands();

        assertEquals(1, result.size());
        assertEquals("Visa", result.get(0).name());
    }

    @Test
    void findAllCardBrands_WhenEmpty_ShouldReturnEmptyList() {
        when(cardBrandRepository.findAll()).thenReturn(List.of());

        assertTrue(adminService.findAllCardBrands().isEmpty());
    }

    @Test
    void findAllRecurrencePeriodicities_ShouldReturnMappedDtos() {
        RecurrencePeriodicity p = new RecurrencePeriodicity();
        p.setId(1L);
        p.setName("Mensal");
        p.setType(PeriodicityType.MONTHLY);
        when(recurrencePeriodicityRepository.findAll()).thenReturn(List.of(p));

        List<RecurrencePeriodicityDto> result = adminService.findAllRecurrencePeriodicities();

        assertEquals(1, result.size());
        assertEquals("Mensal", result.get(0).name());
        assertEquals(PeriodicityType.MONTHLY, result.get(0).type());
    }

    @Test
    void findAllRecurrencePeriodicities_WhenEmpty_ShouldReturnEmptyList() {
        when(recurrencePeriodicityRepository.findAll()).thenReturn(List.of());

        assertTrue(adminService.findAllRecurrencePeriodicities().isEmpty());
    }
}