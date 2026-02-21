package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IndividualEmployerRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private IndividualEmployerRepository individualEmployerRepository;

    private IndividualEmployer savedEmployer;

    @BeforeEach
    void setUp() {
        IndividualEmployer employer = new IndividualEmployer();
        employer.setCpf("12345678901");
        employer.setName("João da Silva");
        savedEmployer = individualEmployerRepository.save(employer);
    }

    @Test
    void existsByCpf_WhenCpfExists_ShouldReturnTrue() {
        assertTrue(individualEmployerRepository.existsByCpf("12345678901"));
    }

    @Test
    void existsByCpf_WhenCpfNotExists_ShouldReturnFalse() {
        assertFalse(individualEmployerRepository.existsByCpf("99999999999"));
    }

    @Test
    void save_ShouldPersistEmployer() {
        assertNotNull(savedEmployer.getId());
        assertEquals("12345678901", savedEmployer.getCpf());
        assertEquals("João da Silva", savedEmployer.getName());
    }

    @Test
    void findById_WhenExists_ShouldReturnEmployer() {
        assertTrue(individualEmployerRepository.findById(savedEmployer.getId()).isPresent());
    }

    @Test
    void findById_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(individualEmployerRepository.findById(999L).isEmpty());
    }
}