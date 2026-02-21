package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.BenefitType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BenefitTypeRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private BenefitTypeRepository benefitTypeRepository;

    @Test
    void findAll_ShouldReturnAllSeededTypes() {
        List<BenefitType> types = benefitTypeRepository.findAll();

        assertEquals(2, types.size());
        assertTrue(types.stream().anyMatch(t -> t.getName().equals("Vale-Alimentação")));
        assertTrue(types.stream().anyMatch(t -> t.getName().equals("Vale-Refeição")));
    }

    @Test
    void findById_WhenExists_ShouldReturnType() {
        Long id = benefitTypeRepository.findAll().get(0).getId();

        assertTrue(benefitTypeRepository.findById(id).isPresent());
    }

    @Test
    void findById_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(benefitTypeRepository.findById(999L).isEmpty());
    }
}