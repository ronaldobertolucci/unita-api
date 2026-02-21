package io.github.ronaldobertolucci.unita.model.finance;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "benefit_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenefitType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;
}