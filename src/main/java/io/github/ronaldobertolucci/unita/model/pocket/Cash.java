package io.github.ronaldobertolucci.unita.model.pocket;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cash")
@DiscriminatorValue("CASH")
@PrimaryKeyJoinColumn(name = "pocket_id")
@Getter
@Setter
@NoArgsConstructor
public class Cash extends Pocket {

    @Override
    public String getLabel() {
        return "Dinheiro em espécie";
    }
}