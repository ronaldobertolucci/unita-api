package io.github.ronaldobertolucci.unita.model.employer;

import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "legal_entity_employers")
@DiscriminatorValue("LEGAL_ENTITY")
@PrimaryKeyJoinColumn(name = "employer_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalEntityEmployer extends Employer {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_entity_id", nullable = false)
    private LegalEntity legalEntity;
}