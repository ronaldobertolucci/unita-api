package io.github.ronaldobertolucci.unita.service.admin;

import io.github.ronaldobertolucci.unita.dto.admin.*;
import io.github.ronaldobertolucci.unita.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final BankAccountTypeRepository bankAccountTypeRepository;
    private final BenefitTypeRepository benefitTypeRepository;
    private final CardBrandRepository cardBrandRepository;
    private final RecurrencePeriodicityRepository recurrencePeriodicityRepository;

    public List<BankAccountTypeDto> findAllBankAccountTypes() {
        return bankAccountTypeRepository.findAll()
                .stream()
                .map(BankAccountTypeDto::new)
                .toList();
    }

    public List<BenefitTypeDto> findAllBenefitTypes() {
        return benefitTypeRepository.findAll()
                .stream()
                .map(BenefitTypeDto::new)
                .toList();
    }

    public List<CardBrandDto> findAllCardBrands() {
        return cardBrandRepository.findAll()
                .stream()
                .map(CardBrandDto::new)
                .toList();
    }

    public List<RecurrencePeriodicityDto> findAllRecurrencePeriodicities() {
        return recurrencePeriodicityRepository.findAll()
                .stream()
                .map(RecurrencePeriodicityDto::new)
                .toList();
    }
}