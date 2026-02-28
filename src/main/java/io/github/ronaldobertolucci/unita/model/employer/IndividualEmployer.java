package io.github.ronaldobertolucci.unita.model.employer;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "individual_employers")
@DiscriminatorValue("INDIVIDUAL")
@PrimaryKeyJoinColumn(name = "employer_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class IndividualEmployer extends Employer {

    @Column(name = "cpf", nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "name", nullable = false, length = 255)
    private String name;
}