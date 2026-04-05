package io.github.ronaldobertolucci.unita.service.card;

import io.github.ronaldobertolucci.unita.model.card.CreditCard;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBill;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import io.github.ronaldobertolucci.unita.repository.CreditCardBillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CreditCardBillResolverService {

    private final CreditCardBillRepository creditCardBillRepository;

    @Transactional
    public CreditCardBill findOrCreateForDate(CreditCard creditCard, LocalDate purchaseDate) {
        LocalDate expectedClosingDate = calculateExpectedClosingDate(creditCard, purchaseDate);

        return creditCardBillRepository
                .findByCreditCardIdAndClosingDate(creditCard.getId(), expectedClosingDate)
                .orElseGet(() -> createForDate(creditCard, expectedClosingDate));
    }

    // Mantido para compatibilidade com o CreditCardService,
    // mas o limitDate não é mais necessário com essa nova abordagem.
    @Transactional
    public CreditCardBill findOrCreateForDate(CreditCard creditCard, LocalDate purchaseDate, LocalDate limitDate) {
        return findOrCreateForDate(creditCard, purchaseDate);
    }

    /**
     * Calcula a data exata de fechamento com base na data da compra e no dia de fechamento do cartão.
     */
    private LocalDate calculateExpectedClosingDate(CreditCard creditCard, LocalDate purchaseDate) {
        int closingDay = creditCard.getClosingDay();
        LocalDate closingDate = purchaseDate.withDayOfMonth(
                Math.min(closingDay, purchaseDate.lengthOfMonth()));

        if (!closingDate.isAfter(purchaseDate)) {
            LocalDate next = closingDate.plusMonths(1);
            return next.withDayOfMonth(Math.min(closingDay, next.lengthOfMonth()));
        }

        return closingDate;
    }

    /**
     * Cria a fatura baseada em uma data de fechamento já pré-calculada.
     */
    private CreditCardBill createForDate(CreditCard creditCard, LocalDate closingDate) {
        int closingDay = creditCard.getClosingDay();
        int dueDay = creditCard.getDueDay();

        LocalDate dueDateMonth = dueDay > closingDay ? closingDate : closingDate.plusMonths(1);
        LocalDate dueDate = dueDateMonth.withDayOfMonth(
                Math.min(dueDay, dueDateMonth.lengthOfMonth()));

        CreditCardBill bill = CreditCardBill.builder()
                .creditCard(creditCard)
                .closingDate(closingDate)
                .dueDate(dueDate)
                .status(CreditCardBillStatus.OPEN)
                .build();

        return creditCardBillRepository.save(bill);
    }
}