package io.github.ronaldobertolucci.unita.model.pocket;

import jakarta.persistence.*;
import lombok.*;

/** Explicit pair, immutable through the public API. No cascade deletes. */
@Entity
@Table(name = "transfer_operations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferOperation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String type;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_transaction_id", nullable = false, unique = true)
    private Transaction sourceTransaction;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_transaction_id", nullable = false, unique = true)
    private Transaction targetTransaction;
}
