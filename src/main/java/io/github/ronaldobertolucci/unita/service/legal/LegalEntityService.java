package io.github.ronaldobertolucci.unita.service.legal;

import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityCreateDto;
import io.github.ronaldobertolucci.unita.dto.legal.LegalEntityDto;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.repository.LegalEntityRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LegalEntityService {

    private final LegalEntityRepository legalEntityRepository;

    @Transactional
    public LegalEntityDto create(LegalEntityCreateDto dto) {
        LegalEntity legalEntity = LegalEntity.builder()
                .cnpj(dto.cnpj())
                .corporateName(dto.corporateName())
                .tradeName(dto.tradeName())
                .stateRegistration(dto.stateRegistration())
                .build();
        return new LegalEntityDto(legalEntityRepository.save(legalEntity));
    }

    public List<LegalEntityDto> findAll() {
        return legalEntityRepository.findAll()
                .stream()
                .map(LegalEntityDto::new)
                .toList();
    }

    public LegalEntityDto findById(Long id) {
        return legalEntityRepository.findById(id)
                .map(LegalEntityDto::new)
                .orElseThrow(() -> new EntityNotFoundException("Legal entity not found"));
    }
}