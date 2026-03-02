package io.github.ronaldobertolucci.unita.model.investment;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pension_details")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PensionDetails {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "pension_type", nullable = false, length = 5)
    private PensionType pensionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_regime", nullable = false, length = 12)
    private TaxRegime taxRegime;
}