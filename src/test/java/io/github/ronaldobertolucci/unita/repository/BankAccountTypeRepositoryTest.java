package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.BankAccountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BankAccountTypeRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private BankAccountTypeRepository bankAccountTypeRepository;

    @Test
    void findAll_ShouldReturnAllSeededTypes() {
        List<BankAccountType> types = bankAccountTypeRepository.findAll();

        assertEquals(4, types.size());
        assertTrue(types.stream().anyMatch(t -> t.getName().equals("Corrente")));
        assertTrue(types.stream().anyMatch(t -> t.getName().equals("Poupança")));
        assertTrue(types.stream().anyMatch(t -> t.getName().equals("Salário")));
        assertTrue(types.stream().anyMatch(t -> t.getName().equals("Investimento")));
    }

    @Test
    void findById_WhenExists_ShouldReturnType() {
        Long id = bankAccountTypeRepository.findAll().get(0).getId();

        assertTrue(bankAccountTypeRepository.findById(id).isPresent());
    }

    @Test
    void findById_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(bankAccountTypeRepository.findById(999L).isEmpty());
    }
}