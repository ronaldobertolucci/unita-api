package io.github.ronaldobertolucci.unita.service.employer;

import io.github.ronaldobertolucci.unita.dto.employer.EmployerDto;
import io.github.ronaldobertolucci.unita.dto.employer.IndividualEmployerCreateDto;
import io.github.ronaldobertolucci.unita.dto.employer.LegalEntityEmployerCreateDto;
import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.repository.EmployerRepository;
import io.github.ronaldobertolucci.unita.repository.IndividualEmployerRepository;
import io.github.ronaldobertolucci.unita.repository.LegalEntityEmployerRepository;
import io.github.ronaldobertolucci.unita.repository.LegalEntityRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployerService {

    private final EmployerRepository employerRepository;
    private final IndividualEmployerRepository individualEmployerRepository;
    private final LegalEntityEmployerRepository legalEntityEmployerRepository;
    private final LegalEntityRepository legalEntityRepository;

    @Transactional
    public EmployerDto createIndividual(IndividualEmployerCreateDto dto) {
        if (individualEmployerRepository.existsByCpf(dto.cpf())) {
            throw new IllegalStateException("An employer with this CPF already exists");
        }

        IndividualEmployer employer = IndividualEmployer.builder()
                .cpf(dto.cpf())
                .name(dto.name())
                .build();

        return EmployerDto.from(individualEmployerRepository.save(employer));
    }

    @Transactional
    public EmployerDto createLegalEntity(LegalEntityEmployerCreateDto dto) {
        if (legalEntityEmployerRepository.existsByLegalEntityId(dto.legalEntityId())) {
            throw new IllegalStateException("An employer for this legal entity already exists");
        }

        LegalEntity legalEntity = legalEntityRepository.findById(dto.legalEntityId())
                .orElseThrow(() -> new EntityNotFoundException("Legal entity not found"));

        LegalEntityEmployer employer = LegalEntityEmployer.builder()
                .legalEntity(legalEntity)
                .build();

        return EmployerDto.from(legalEntityEmployerRepository.save(employer));
    }

    public List<EmployerDto> findAll() {
        return employerRepository.findAll()
                .stream()
                .map(EmployerDto::from)
                .toList();
    }

    public EmployerDto findById(Long id) {
        return employerRepository.findById(id)
                .map(EmployerDto::from)
                .orElseThrow(() -> new EntityNotFoundException("Employer not found"));
    }
}