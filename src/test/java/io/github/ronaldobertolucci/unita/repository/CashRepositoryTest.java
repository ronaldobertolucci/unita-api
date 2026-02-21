package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CashRepositoryTest extends BaseRepositoryTest {

    @Autowired private CashRepository cashRepository;

    private User user;
    private User otherUser;
    private Cash savedCash;

    @BeforeEach
    void setUp() {
        user = saveUser("user@test.com");
        otherUser = saveUser("other@test.com");

        Cash cash = new Cash();
        cash.setUser(user);
        savedCash = cashRepository.save(cash);
    }

    @Test
    void findByUserId_WhenExists_ShouldReturnCash() {
        Optional<Cash> found = cashRepository.findByUserId(user.getId());

        assertTrue(found.isPresent());
        assertEquals(user.getId(), found.get().getUser().getId());
    }

    @Test
    void findByUserId_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(cashRepository.findByUserId(otherUser.getId()).isEmpty());
    }

    @Test
    void existsByUserId_WhenExists_ShouldReturnTrue() {
        assertTrue(cashRepository.existsByUserId(user.getId()));
    }

    @Test
    void existsByUserId_WhenNotExists_ShouldReturnFalse() {
        assertFalse(cashRepository.existsByUserId(otherUser.getId()));
    }
}