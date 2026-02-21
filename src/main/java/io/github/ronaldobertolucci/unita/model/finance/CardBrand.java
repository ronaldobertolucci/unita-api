package io.github.ronaldobertolucci.unita.model.finance;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "card_brands")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardBrand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;
}