package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "team_mapping", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"team_id", "car_type"})
})
public class TeamMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Integer teamId;

    @Column(name = "car_type", nullable = false)
    private String carType;

    @Column(name = "team_name", nullable = false)
    private String teamName;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamMapping that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
