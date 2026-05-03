package io.github.ronaldobertolucci.unita.service.legal;

import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityCreateDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityUpdateDto;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.BankAccountRepository;
import io.github.ronaldobertolucci.unita.repository.BenefitAccountRepository;
import io.github.ronaldobertolucci.unita.repository.CreditCardRepository;
import io.github.ronaldobertolucci.unita.repository.LegalEntityRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LegalEntityService {

    private final LegalEntityRepository legalEntityRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BenefitAccountRepository benefitAccountRepository;
    private final CreditCardRepository creditCardRepository;

    @Transactional
    public LegalEntityDto create(LegalEntityCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        if (legalEntityRepository.existsByCnpjAndUserId(dto.cnpj(), currentUser.getId())) {
            throw new IllegalArgumentException("A legal entity with this CNPJ already exists");
        }

        LegalEntity legalEntity = LegalEntity.builder()
                .user(currentUser)
                .cnpj(dto.cnpj())
                .corporateName(dto.corporateName())
                .tradeName(dto.tradeName())
                .stateRegistration(dto.stateRegistration())
                .build();

        return new LegalEntityDto(legalEntityRepository.save(legalEntity));
    }

    public List<LegalEntityDto> findAll(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return legalEntityRepository.findAllByUserIdOrderByCorporateName(currentUser.getId())
                .stream()
                .map(LegalEntityDto::new)
                .toList();
    }

    public LegalEntityDto findById(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return legalEntityRepository.findByIdAndUserId(id, currentUser.getId())
                .map(LegalEntityDto::new)
                .orElseThrow(() -> new EntityNotFoundException("Legal entity not found"));
    }

    @Transactional
    public LegalEntityDto update(Long id, LegalEntityUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        LegalEntity legalEntity = legalEntityRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Legal entity not found"));

        if (!legalEntity.getCnpj().equals(dto.cnpj()) &&
                legalEntityRepository.existsByCnpjAndUserId(dto.cnpj(), currentUser.getId())) {
            throw new IllegalArgumentException("A legal entity with this CNPJ already exists");
        }

        legalEntity.setCnpj(dto.cnpj());
        legalEntity.setCorporateName(dto.corporateName());
        legalEntity.setTradeName(dto.tradeName());
        legalEntity.setStateRegistration(dto.stateRegistration());

        return new LegalEntityDto(legalEntityRepository.save(legalEntity));
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        if (!legalEntityRepository.existsByIdAndUserId(id, currentUser.getId())) {
            throw new EntityNotFoundException("Legal entity not found");
        }

        if (isLegalEntityInUse(id)) {
            throw new IllegalStateException("Legal entity is in use and cannot be deleted");
        }

        legalEntityRepository.deleteById(id);
    }

    private boolean isLegalEntityInUse(Long id) {
        return bankAccountRepository.existsByLegalEntityId(id)
                || benefitAccountRepository.existsByLegalEntityId(id)
                || creditCardRepository.existsByLegalEntityId(id);
    }
}