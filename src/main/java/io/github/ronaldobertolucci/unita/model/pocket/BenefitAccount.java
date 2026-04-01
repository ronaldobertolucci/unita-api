package io.github.ronaldobertolucci.unita.model.pocket;

import io.github.ronaldobertolucci.unita.model.finance.BenefitType;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "benefit_accounts")
@DiscriminatorValue("BENEFIT_ACCOUNT")
@PrimaryKeyJoinColumn(name = "pocket_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BenefitAccount extends Pocket {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benefit_type_id", nullable = false)
    private BenefitType benefitType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private BenefitAccountStatus status;

    @Override
    public String getLabel() {
        return this.getLegalEntity().getCorporateName() + " - " + this.getBenefitType().getName();
    }
}