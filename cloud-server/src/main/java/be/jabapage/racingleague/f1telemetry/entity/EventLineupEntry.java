package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "event_lineup")
public class EventLineupEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "driver_id", nullable = false)
    private DriverMapping driver;

    @Column(name = "team_id", nullable = false)
    private Integer teamId;

    @Column(name = "car_type", nullable = false)
    private String carType;

    @Column(name = "championship_team")
    private String championshipTeam;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventLineupEntry that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
