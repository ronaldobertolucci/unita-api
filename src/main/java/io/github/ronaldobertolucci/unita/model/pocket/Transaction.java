package io.github.ronaldobertolucci.unita.model.pocket;

import io.github.ronaldobertolucci.unita.model.finance.Category;
import io.github.ronaldobertolucci.unita.model.finance.Direction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @OneToOne(mappedBy = "sourceTransaction", fetch = FetchType.LAZY)
    private TransferOperation outgoingTransfer;

    @OneToOne(mappedBy = "targetTransaction", fetch = FetchType.LAZY)
    private TransferOperation incomingTransfer;

    @OneToOne(mappedBy = "paymentTransaction", fetch = FetchType.LAZY)
    private io.github.ronaldobertolucci.unita.model.card.CreditCardBill paymentBill;

    public TransferOperation transferOperation() {
        return outgoingTransfer != null ? outgoingTransfer : incomingTransfer;
    }

    public boolean isDeletionProtected() {
        return transferOperation() != null || paymentBill != null;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pocket_id", nullable = false)
    private Pocket pocket;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private Direction direction;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
}