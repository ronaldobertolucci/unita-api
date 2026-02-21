package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.PeriodicityType;
import io.github.ronaldobertolucci.unita.model.finance.RecurrencePeriodicity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecurrencePeriodicityRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private RecurrencePeriodicityRepository recurrencePeriodicityRepository;

    @Test
    void findAll_ShouldReturnAllSeededPeriodicities() {
        List<RecurrencePeriodicity> periodicities = recurrencePeriodicityRepository.findAll();

        assertEquals(4, periodicities.size());
        assertTrue(periodicities.stream().anyMatch(p -> p.getType() == PeriodicityType.DAILY));
        assertTrue(periodicities.stream().anyMatch(p -> p.getType() == PeriodicityType.WEEKLY));
        assertTrue(periodicities.stream().anyMatch(p -> p.getType() == PeriodicityType.MONTHLY));
        assertTrue(periodicities.stream().anyMatch(p -> p.getType() == PeriodicityType.YEARLY));
    }

    @Test
    void findById_WhenExists_ShouldReturnPeriodicity() {
        Long id = recurrencePeriodicityRepository.findAll().get(0).getId();

        assertTrue(recurrencePeriodicityRepository.findById(id).isPresent());
    }
}