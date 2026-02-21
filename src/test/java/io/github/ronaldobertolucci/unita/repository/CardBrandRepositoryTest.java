package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.CardBrand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CardBrandRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private CardBrandRepository cardBrandRepository;

    @Test
    void findAll_ShouldReturnAllSeededBrands() {
        List<CardBrand> brands = cardBrandRepository.findAll();

        assertEquals(5, brands.size());
        assertTrue(brands.stream().anyMatch(b -> b.getName().equals("Visa")));
        assertTrue(brands.stream().anyMatch(b -> b.getName().equals("Mastercard")));
        assertTrue(brands.stream().anyMatch(b -> b.getName().equals("Elo")));
        assertTrue(brands.stream().anyMatch(b -> b.getName().equals("American Express")));
        assertTrue(brands.stream().anyMatch(b -> b.getName().equals("Hipercard")));
    }

    @Test
    void findById_WhenExists_ShouldReturnBrand() {
        Long id = cardBrandRepository.findAll().get(0).getId();

        assertTrue(cardBrandRepository.findById(id).isPresent());
    }

    @Test
    void findById_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(cardBrandRepository.findById(999L).isEmpty());
    }
}