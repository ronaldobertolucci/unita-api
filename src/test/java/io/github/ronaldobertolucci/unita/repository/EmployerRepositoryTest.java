package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmployerRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private EmployerRepository employerRepository;

    @Autowired
    private IndividualEmployerRepository individualEmployerRepository;

    @Test
    void findAll_ShouldReturnAllEmployers() {
        saveIndividualEmployer("11111111111", "Empregador A");
        saveIndividualEmployer("22222222222", "Empregador B");

        List<?> all = employerRepository.findAll();

        assertTrue(all.size() >= 2);
    }

    @Test
    void findById_WhenExists_ShouldReturnEmployer() {
        IndividualEmployer saved = saveIndividualEmployer("12345678901", "João");

        assertTrue(employerRepository.findById(saved.getId()).isPresent());
    }

    @Test
    void findById_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(employerRepository.findById(999L).isEmpty());
    }

    @Test
    void delete_ShouldRemoveEmployer() {
        IndividualEmployer saved = saveIndividualEmployer("12345678901", "João");

        employerRepository.delete(saved);

        assertTrue(employerRepository.findById(saved.getId()).isEmpty());
    }

    private IndividualEmployer saveIndividualEmployer(String cpf, String name) {
        IndividualEmployer employer = new IndividualEmployer();
        employer.setCpf(cpf);
        employer.setName(name);
        return individualEmployerRepository.save(employer);
    }
}