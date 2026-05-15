package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@Entity
public class DriverStanding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tier_id")
    private Tier tier;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "race_number")
    private Integer raceNumber;

    @Column(name = "is_ai")
    private Boolean ai = false;

    @Column(name = "is_reserve")
    private Boolean reserve = false;

    public boolean isAi() {
        return ai != null && ai;
    }

    public boolean isReserve() {
        return reserve != null && reserve;
    }


    @Column(name = "team_name")
    private String teamName;

    private Integer points;
    private Integer wins;
    private Integer podiums;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DriverStanding that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
