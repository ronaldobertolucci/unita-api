package io.github.ronaldobertolucci.unita.service.card;

import io.github.ronaldobertolucci.unita.model.card.CreditCard;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBill;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import io.github.ronaldobertolucci.unita.repository.CreditCardBillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CreditCardBillResolverService {

    private final CreditCardBillRepository creditCardBillRepository;

    @Transactional
    public CreditCardBill findOrCreateForDate(CreditCard creditCard, LocalDate purchaseDate) {
        return creditCardBillRepository
                .findFirstByCreditCardIdAndClosingDateAfterPurchaseDate(creditCard.getId(), purchaseDate, PageRequest.of(0, 1))
                .stream().findFirst()
                .orElseGet(() -> createForDate(creditCard, purchaseDate));
    }

    private CreditCardBill createForDate(CreditCard creditCard, LocalDate purchaseDate) {
        int closingDay = creditCard.getClosingDay();
        int dueDay    = creditCard.getDueDay();

        LocalDate closingDate = purchaseDate.withDayOfMonth(
                Math.min(closingDay, purchaseDate.lengthOfMonth()));

        if (!closingDate.isAfter(purchaseDate)) {
            LocalDate next = closingDate.plusMonths(1);
            closingDate = next.withDayOfMonth(Math.min(closingDay, next.lengthOfMonth()));
        }

        LocalDate dueDateMonth = closingDate.plusMonths(1);
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