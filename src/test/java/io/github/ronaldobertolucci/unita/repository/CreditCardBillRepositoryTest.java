package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.CreditCard;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBill;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import io.github.ronaldobertolucci.unita.model.finance.CardBrand;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreditCardBillRepositoryTest extends BaseRepositoryTest {

    @Autowired private CreditCardBillRepository billRepository;
    @Autowired private CreditCardRepository creditCardRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired private CardBrandRepository cardBrandRepository;

    private CreditCard card;
    private CreditCard otherCard;

    @BeforeEach
    void setUp() {
        User user = saveUser("user@test.com");
        User other = saveUser("other@test.com");

        LegalEntity le = new LegalEntity();
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco");
        le.setUser(user);
        legalEntityRepository.save(le);

        CardBrand brand = cardBrandRepository.findAll().get(0);

        card = creditCardRepository.save(CreditCard.builder()
                .user(user).legalEntity(le).lastFourDigits("1234")
                .cardBrand(brand).creditLimit(new BigDecimal("5000")).closingDay(10).dueDay(20).build());
        otherCard = creditCardRepository.save(CreditCard.builder()
                .user(other).legalEntity(le).lastFourDigits("5678")
                .cardBrand(brand).creditLimit(new BigDecimal("3000")).closingDay(15).dueDay(25).build());
    }

    @Test
    void findAllByCreditCardId_ShouldReturnOnlyCardBills() {
        saveBill(card, LocalDate.of(2024, 1, 10), CreditCardBillStatus.PAID);
        saveBill(card, LocalDate.of(2024, 2, 10), CreditCardBillStatus.CLOSED);
        saveBill(otherCard, LocalDate.of(2024, 1, 10), CreditCardBillStatus.OPEN);

        List<CreditCardBill> bills = billRepository.findAllByCreditCardId(card.getId());

        assertEquals(2, bills.size());
        assertTrue(bills.stream().allMatch(b -> b.getCreditCard().getId().equals(card.getId())));
    }

    @Test
    void findAllByCreditCardId_ShouldReturnOrderedByClosingDateAsc() {
        saveBill(card, LocalDate.of(2024, 3, 10), CreditCardBillStatus.OPEN);
        saveBill(card, LocalDate.of(2024, 1, 10), CreditCardBillStatus.PAID);
        saveBill(card, LocalDate.of(2024, 2, 10), CreditCardBillStatus.CLOSED);

        List<CreditCardBill> bills = billRepository.findAllByCreditCardId(card.getId());

        assertEquals(LocalDate.of(2024, 1, 10), bills.get(0).getClosingDate());
        assertEquals(LocalDate.of(2024, 3, 10), bills.get(2).getClosingDate());
    }

    @Test
    void findAllByCreditCardIdAndStatus_ShouldFilterByStatus() {
        saveBill(card, LocalDate.of(2024, 1, 10), CreditCardBillStatus.PAID);
        saveBill(card, LocalDate.of(2024, 2, 10), CreditCardBillStatus.CLOSED);
        saveBill(card, LocalDate.of(2024, 3, 10), CreditCardBillStatus.OPEN);

        List<CreditCardBill> closed = billRepository.findAllByCreditCardIdAndStatus(card.getId(), CreditCardBillStatus.CLOSED);

        assertEquals(1, closed.size());
        assertEquals(CreditCardBillStatus.CLOSED, closed.get(0).getStatus());
    }

    @Test
    void findFirstByCreditCardIdAndClosingDateAfterPurchaseDate_WhenExists_ShouldReturnEarliestFutureBill() {
        saveBill(card, LocalDate.of(2024, 3, 10), CreditCardBillStatus.OPEN);
        saveBill(card, LocalDate.of(2024, 1, 10), CreditCardBillStatus.OPEN);
        saveBill(card, LocalDate.of(2024, 2, 10), CreditCardBillStatus.OPEN);

        Optional<CreditCardBill> found = billRepository
                .findFirstByCreditCardIdAndClosingDateAfterPurchaseDate(card.getId(), LocalDate.of(2024, 1, 15), PageRequest.of(0, 1))
                .stream().findFirst();

        assertTrue(found.isPresent());
        assertEquals(LocalDate.of(2024, 2, 10), found.get().getClosingDate());
    }

    @Test
    void findFirstByCreditCardIdAndClosingDateAfterPurchaseDate_WhenNoBillAfterDate_ShouldReturnEmpty() {
        saveBill(card, LocalDate.of(2024, 1, 10), CreditCardBillStatus.OPEN);

        Optional<CreditCardBill> found = billRepository
                .findFirstByCreditCardIdAndClosingDateAfterPurchaseDate(card.getId(), LocalDate.of(2024, 1, 15), PageRequest.of(0, 1))
                .stream().findFirst();

        assertTrue(found.isEmpty());
    }

    @Test
    void findByIdAndCreditCardId_WhenMatch_ShouldReturnBill() {
        CreditCardBill saved = saveBill(card, LocalDate.of(2024, 1, 10), CreditCardBillStatus.OPEN);

        Optional<CreditCardBill> found = billRepository.findByIdAndCreditCardId(saved.getId(), card.getId());

        assertTrue(found.isPresent());
    }

    @Test
    void findByIdAndCreditCardId_WhenWrongCard_ShouldReturnEmpty() {
        CreditCardBill saved = saveBill(card, LocalDate.of(2024, 1, 10), CreditCardBillStatus.OPEN);

        assertTrue(billRepository.findByIdAndCreditCardId(saved.getId(), otherCard.getId()).isEmpty());
    }

    @Test
    void closeAllOverdue_ShouldCloseOnlyOpenBillsBeforeToday() {
        LocalDate today = LocalDate.now();
        saveBill(card, today.minusDays(1), CreditCardBillStatus.OPEN);   // deve fechar
        saveBill(card, today.minusDays(5), CreditCardBillStatus.OPEN);   // deve fechar
        saveBill(card, today.plusDays(5), CreditCardBillStatus.OPEN);    // não deve fechar
        saveBill(card, today.minusDays(1), CreditCardBillStatus.PAID);   // não deve alterar (já PAID)

        int closed = billRepository.closeAllOverdue(today, CreditCardBillStatus.OPEN, CreditCardBillStatus.CLOSED);

        assertEquals(2, closed);
        List<CreditCardBill> allBills = billRepository.findAllByCreditCardId(card.getId());
        assertEquals(2, allBills.stream().filter(b -> b.getStatus() == CreditCardBillStatus.CLOSED).count());
        assertEquals(1, allBills.stream().filter(b -> b.getStatus() == CreditCardBillStatus.OPEN).count());
        assertEquals(1, allBills.stream().filter(b -> b.getStatus() == CreditCardBillStatus.PAID).count());
    }

    @Test
    void closeAllOverdue_WhenNoOverdueBills_ShouldReturnZero() {
        saveBill(card, LocalDate.now().plusDays(10), CreditCardBillStatus.OPEN);

        int closed = billRepository.closeAllOverdue(LocalDate.now(), CreditCardBillStatus.OPEN, CreditCardBillStatus.CLOSED);

        assertEquals(0, closed);
    }

    @Test
    void findAllByUserId_ShouldReturnOnlyBillsOfUserCards() {
        saveBill(card, LocalDate.of(2025, 1, 10), CreditCardBillStatus.OPEN);
        saveBill(card, LocalDate.of(2025, 2, 10), CreditCardBillStatus.CLOSED);
        saveBill(otherCard, LocalDate.of(2025, 1, 10), CreditCardBillStatus.OPEN);

        List<CreditCardBill> result = billRepository.findAllByUserId(card.getUser().getId());

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(b -> b.getCreditCard().getId().equals(card.getId())));
    }

    @Test
    void findAllByUserId_WhenNoBills_ShouldReturnEmpty() {
        assertTrue(billRepository.findAllByUserId(card.getUser().getId()).isEmpty());
    }

    @Test
    void findAllByUserId_ShouldReturnOrderedByClosingDateAsc() {
        saveBill(card, LocalDate.of(2025, 3, 10), CreditCardBillStatus.OPEN);
        saveBill(card, LocalDate.of(2025, 1, 10), CreditCardBillStatus.OPEN);
        saveBill(card, LocalDate.of(2025, 2, 10), CreditCardBillStatus.OPEN);

        List<CreditCardBill> result = billRepository.findAllByUserId(card.getUser().getId());

        assertEquals(LocalDate.of(2025, 1, 10), result.get(0).getClosingDate());
        assertEquals(LocalDate.of(2025, 3, 10), result.get(2).getClosingDate());
    }

    @Test
    void findOpenBillsFromToday_ShouldReturnOnlyOpenBillsFromTodayOnwards() {
        LocalDate today = LocalDate.now();
        saveBill(card, today.minusDays(1), CreditCardBillStatus.OPEN);   // passado → não retorna
        saveBill(card, today, CreditCardBillStatus.OPEN);                 // hoje → retorna
        saveBill(card, today.plusMonths(1), CreditCardBillStatus.OPEN);   // futuro → retorna
        saveBill(card, today.plusMonths(2), CreditCardBillStatus.CLOSED); // CLOSED → não retorna
        saveBill(card, today.plusMonths(3), CreditCardBillStatus.PAID);   // PAID → não retorna
        saveBill(otherCard, today, CreditCardBillStatus.OPEN);            // outro cartão → não retorna

        List<CreditCardBill> result = billRepository.findOpenBillsFromToday(card.getId(), today);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(b -> b.getStatus() == CreditCardBillStatus.OPEN));
        assertTrue(result.stream().allMatch(b -> !b.getClosingDate().isBefore(today)));
        assertTrue(result.stream().allMatch(b -> b.getCreditCard().getId().equals(card.getId())));
    }

    @Test
    void findOpenBillsFromToday_ShouldReturnOrderedByClosingDateAsc() {
        LocalDate today = LocalDate.now();
        saveBill(card, today.plusMonths(2), CreditCardBillStatus.OPEN);
        saveBill(card, today, CreditCardBillStatus.OPEN);
        saveBill(card, today.plusMonths(1), CreditCardBillStatus.OPEN);

        List<CreditCardBill> result = billRepository.findOpenBillsFromToday(card.getId(), today);

        assertEquals(3, result.size());
        assertEquals(today, result.get(0).getClosingDate());
        assertEquals(today.plusMonths(1), result.get(1).getClosingDate());
        assertEquals(today.plusMonths(2), result.get(2).getClosingDate());
    }

    @Test
    void findOpenBillsFromToday_WhenNoOpenBills_ShouldReturnEmpty() {
        LocalDate today = LocalDate.now();
        saveBill(card, today, CreditCardBillStatus.CLOSED);
        saveBill(card, today.plusMonths(1), CreditCardBillStatus.PAID);

        List<CreditCardBill> result = billRepository.findOpenBillsFromToday(card.getId(), today);

        assertTrue(result.isEmpty());
    }

    private CreditCardBill saveBill(CreditCard card, LocalDate closingDate, CreditCardBillStatus status) {
        return billRepository.save(CreditCardBill.builder()
                .creditCard(card)
                .closingDate(closingDate)
                .dueDate(closingDate.plusMonths(1))
                .status(status)
                .build());
    }
}