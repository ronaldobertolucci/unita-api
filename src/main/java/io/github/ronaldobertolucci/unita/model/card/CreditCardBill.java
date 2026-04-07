package io.github.ronaldobertolucci.unita.model.card;

import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "credit_card_bills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditCardBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_card_id", nullable = false)
    private CreditCard creditCard;

    @Column(name = "closing_date", nullable = false)
    private LocalDate closingDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "closing_day", nullable = false)
    private Integer closingDay;

    @Column(name = "due_day", nullable = false)
    private Integer dueDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private CreditCardBillStatus status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id", unique = true)
    private Transaction paymentTransaction;
}