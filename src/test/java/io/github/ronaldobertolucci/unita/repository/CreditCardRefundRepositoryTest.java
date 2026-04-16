package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.*;
import io.github.ronaldobertolucci.unita.model.finance.CardBrand;
import io.github.ronaldobertolucci.unita.model.finance.Category;
import io.github.ronaldobertolucci.unita.model.finance.CategoryType;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreditCardRefundRepositoryTest extends BaseRepositoryTest {

    @Autowired private CreditCardRefundRepository refundRepository;
    @Autowired private CreditCardBillRepository billRepository;
    @Autowired private CreditCardRepository creditCardRepository;
    @Autowired private LegalEntityRepository legalEntityRepository;
    @Autowired private CardBrandRepository cardBrandRepository;
    @Autowired private CategoryRepository categoryRepository;

    private CreditCardBill bill;
    private CreditCardBill otherBill;
    private Category category;

    @BeforeEach
    void setUp() {
        User user = saveUser("user@test.com");

        LegalEntity le = new LegalEntity();
        le.setCnpj("12345678000190");
        le.setCorporateName("Banco");
        le.setUser(user);
        legalEntityRepository.save(le);

        CardBrand brand = cardBrandRepository.findAll().get(0);

        CreditCard card = creditCardRepository.save(CreditCard.builder()
                .user(user).legalEntity(le).lastFourDigits("1234")
                .cardBrand(brand).creditLimit(new BigDecimal("5000")).closingDay(10).dueDay(20).build());

        bill = billRepository.save(CreditCardBill.builder()
                .periodStart(LocalDate.of(2023, 12, 10))
                .creditCard(card).closingDate(LocalDate.of(2024, 1, 10))
                .closingDay(10).dueDate(LocalDate.of(2024, 2, 10))
                .dueDay(10).status(CreditCardBillStatus.OPEN).build());
        otherBill = billRepository.save(CreditCardBill.builder()
                .periodStart(LocalDate.of(2024, 1, 10))
                .creditCard(card).closingDate(LocalDate.of(2024, 2, 10))
                .closingDay(10).dueDate(LocalDate.of(2024, 3, 10))
                .dueDay(10).status(CreditCardBillStatus.OPEN).build());

        category = categoryRepository.save(Category.builder()
                .user(user)
                .name("Categoria Teste")
                .type(CategoryType.EXPENSE)
                .system(false)
                .build());
    }

    @Test
    void findAllByBillId_ShouldReturnOnlyBillRefunds() {
        saveRefund(bill, new BigDecimal("50.00"), LocalDate.of(2024, 1, 5));
        saveRefund(bill, new BigDecimal("30.00"), LocalDate.of(2024, 1, 3));
        saveRefund(otherBill, new BigDecimal("20.00"), LocalDate.of(2024, 1, 1));

        List<CreditCardRefund> result = refundRepository.findAllByBillId(bill.getId());

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.getCreditCardBill().getId().equals(bill.getId())));
    }

    @Test
    void findAllByBillId_ShouldReturnOrderedByRefundDateDesc() {
        saveRefund(bill, new BigDecimal("10.00"), LocalDate.of(2024, 1, 1));
        saveRefund(bill, new BigDecimal("20.00"), LocalDate.of(2024, 1, 5));
        saveRefund(bill, new BigDecimal("30.00"), LocalDate.of(2024, 1, 3));

        List<CreditCardRefund> result = refundRepository.findAllByBillId(bill.getId());

        assertEquals(LocalDate.of(2024, 1, 5), result.get(0).getRefundDate());
        assertEquals(LocalDate.of(2024, 1, 1), result.get(2).getRefundDate());
    }

    @Test
    void findAllByBillId_WhenNone_ShouldReturnEmpty() {
        assertTrue(refundRepository.findAllByBillId(bill.getId()).isEmpty());
    }

    @Test
    void sumAmountByBillId_ShouldSumAllRefundsOfBill() {
        saveRefund(bill, new BigDecimal("50.00"), LocalDate.now());
        saveRefund(bill, new BigDecimal("30.00"), LocalDate.now());
        saveRefund(otherBill, new BigDecimal("999.00"), LocalDate.now()); // não deve contar

        BigDecimal sum = refundRepository.sumAmountByBillId(bill.getId());

        assertEquals(0, new BigDecimal("80.00").compareTo(sum));
    }

    @Test
    void sumAmountByBillId_WhenNoRefunds_ShouldReturnZero() {
        BigDecimal sum = refundRepository.sumAmountByBillId(bill.getId());

        assertEquals(0, BigDecimal.ZERO.compareTo(sum));
    }

    @Test
    void findByIdAndBillId_WhenMatch_ShouldReturnRefund() {
        CreditCardRefund saved = saveRefund(bill, new BigDecimal("50.00"), LocalDate.now());

        Optional<CreditCardRefund> found = refundRepository.findByIdAndBillId(saved.getId(), bill.getId());

        assertTrue(found.isPresent());
    }

    @Test
    void findByIdAndBillId_WhenWrongBill_ShouldReturnEmpty() {
        CreditCardRefund saved = saveRefund(bill, new BigDecimal("50.00"), LocalDate.now());

        assertTrue(refundRepository.findByIdAndBillId(saved.getId(), otherBill.getId()).isEmpty());
    }

    @Test
    void findByIdAndBillId_WhenNotExists_ShouldReturnEmpty() {
        assertTrue(refundRepository.findByIdAndBillId(999L, bill.getId()).isEmpty());
    }

    private CreditCardRefund saveRefund(CreditCardBill bill, BigDecimal amount, LocalDate date) {
        return refundRepository.save(CreditCardRefund.builder()
                .creditCardBill(bill).description("Estorno teste")
                .amount(amount).refundDate(date).category(category).build());
    }

    @Test
    void sumRefundsByUserIdAndOpenBills_ShouldSumOnlyOpenBills() {
        CreditCardBill paidBill = billRepository.save(CreditCardBill.builder()
                .creditCard(bill.getCreditCard())
                .periodStart(LocalDate.of(2023, 11, 10))
                .closingDate(LocalDate.of(2023, 12, 10))
                .closingDay(10)
                .dueDate(LocalDate.of(2024, 1, 10))
                .dueDay(10)
                .status(CreditCardBillStatus.PAID)
                .build());

        saveRefund(bill, new BigDecimal("50.00"), LocalDate.now());
        saveRefund(paidBill, new BigDecimal("999.00"), LocalDate.now());

        BigDecimal result = refundRepository.sumRefundsByUserIdAndOpenBills(
                bill.getCreditCard().getUser().getId());

        assertEquals(0, new BigDecimal("50.00").compareTo(result));
    }

    @Test
    void sumRefundsByUserIdAndOpenBills_WhenNoRefunds_ShouldReturnZero() {
        BigDecimal result = refundRepository.sumRefundsByUserIdAndOpenBills(
                bill.getCreditCard().getUser().getId());

        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }
}