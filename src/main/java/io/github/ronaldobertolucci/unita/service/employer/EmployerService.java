package io.github.ronaldobertolucci.unita.service.employer;

import io.github.ronaldobertolucci.unita.dto.employer.*;
import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.EmployerRepository;
import io.github.ronaldobertolucci.unita.repository.IndividualEmployerRepository;
import io.github.ronaldobertolucci.unita.repository.LegalEntityEmployerRepository;
import io.github.ronaldobertolucci.unita.repository.LegalEntityRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployerService {

    private final EmployerRepository employerRepository;
    private final IndividualEmployerRepository individualEmployerRepository;
    private final LegalEntityEmployerRepository legalEntityEmployerRepository;
    private final LegalEntityRepository legalEntityRepository;

    // -------------------------------------------------------------------------
    // IndividualEmployer
    // -------------------------------------------------------------------------

    @Transactional
    public IndividualEmployerDto createIndividual(IndividualEmployerCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        if (individualEmployerRepository.existsByCpfAndUserId(dto.cpf(), currentUser.getId())) {
            throw new IllegalArgumentException("An employer with this CPF already exists");
        }

        IndividualEmployer employer = IndividualEmployer.builder()
                .user(currentUser)
                .cpf(dto.cpf())
                .name(dto.name())
                .build();

        return new IndividualEmployerDto(individualEmployerRepository.save(employer));
    }

    public List<IndividualEmployerDto> findAllIndividual(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return individualEmployerRepository.findAllByUserIdOrderByName(currentUser.getId())
                .stream()
                .map(IndividualEmployerDto::new)
                .toList();
    }

    public IndividualEmployerDto findIndividualById(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return individualEmployerRepository.findByIdAndUserId(id, currentUser.getId())
                .map(IndividualEmployerDto::new)
                .orElseThrow(() -> new EntityNotFoundException("Employer not found"));
    }

    @Transactional
    public IndividualEmployerDto updateIndividual(Long id, IndividualEmployerUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        IndividualEmployer employer = individualEmployerRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Employer not found"));

        if (!employer.getCpf().equals(dto.cpf()) &&
                individualEmployerRepository.existsByCpfAndUserId(dto.cpf(), currentUser.getId())) {
            throw new IllegalArgumentException("An employer with this CPF already exists");
        }

        employer.setCpf(dto.cpf());
        employer.setName(dto.name());

        return new IndividualEmployerDto(individualEmployerRepository.save(employer));
    }

    @Transactional
    public void deleteIndividual(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        IndividualEmployer employer = individualEmployerRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Employer not found"));

        if (individualEmployerRepository.existsFgtsAccountByEmployerId(employer.getId())) {
            throw new IllegalStateException("Employer is in use and cannot be deleted");
        }

        individualEmployerRepository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // LegalEntityEmployer
    // -------------------------------------------------------------------------

    @Transactional
    public LegalEntityEmployerDto createLegalEntity(LegalEntityEmployerCreateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        LegalEntity legalEntity = legalEntityRepository.findByIdAndUserId(dto.legalEntityId(), currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Legal entity not found"));

        if (legalEntityEmployerRepository.existsByLegalEntityIdAndUserId(dto.legalEntityId(), currentUser.getId())) {
            throw new IllegalArgumentException("An employer for this legal entity already exists");
        }

        LegalEntityEmployer employer = LegalEntityEmployer.builder()
                .user(currentUser)
                .legalEntity(legalEntity)
                .build();

        return new LegalEntityEmployerDto(legalEntityEmployerRepository.save(employer));
    }

    public List<LegalEntityEmployerDto> findAllLegalEntity(Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return legalEntityEmployerRepository.findAllByUserId(currentUser.getId())
                .stream()
                .sorted(Comparator.comparing(LegalEntityEmployer::getName))
                .map(LegalEntityEmployerDto::new)
                .toList();
    }

    public LegalEntityEmployerDto findLegalEntityById(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return legalEntityEmployerRepository.findByIdAndUserId(id, currentUser.getId())
                .map(LegalEntityEmployerDto::new)
                .orElseThrow(() -> new EntityNotFoundException("Employer not found"));
    }

    @Transactional
    public LegalEntityEmployerDto updateLegalEntity(Long id, LegalEntityEmployerUpdateDto dto, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        LegalEntityEmployer employer = legalEntityEmployerRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Employer not found"));

        LegalEntity legalEntity = legalEntityRepository.findByIdAndUserId(dto.legalEntityId(), currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Legal entity not found"));

        if (!employer.getLegalEntity().getId().equals(dto.legalEntityId()) &&
                legalEntityEmployerRepository.existsByLegalEntityIdAndUserId(dto.legalEntityId(), currentUser.getId())) {
            throw new IllegalArgumentException("An employer for this legal entity already exists");
        }

        employer.setLegalEntity(legalEntity);

        return new LegalEntityEmployerDto(legalEntityEmployerRepository.save(employer));
    }

    @Transactional
    public void deleteLegalEntity(Long id, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        LegalEntityEmployer employer = legalEntityEmployerRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Employer not found"));

        if (legalEntityEmployerRepository.existsFgtsAccountByEmployerId(employer.getId())) {
            throw new IllegalStateException("Employer is in use and cannot be deleted");
        }

        legalEntityEmployerRepository.deleteById(id);
    }
}