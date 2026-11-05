package io.github.ronaldobertolucci.unita.model.investment;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** Immutable command/result snapshot, committed atomically with SELL, TAX and cash. */
@Entity
@Table(name = "investment_sale_operations", uniqueConstraints =
        @UniqueConstraint(name = "uk_investment_sale_user_operation", columnNames = {"user_id", "operation_id"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class InvestmentSaleOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private Long assetId;

    @Column(name = "request_payload", nullable = false, updatable = false, columnDefinition = "text")
    private String requestPayload;

    @Column(name = "response_payload", nullable = false, updatable = false, columnDefinition = "text")
    private String responsePayload;
}
