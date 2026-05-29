package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@Entity
public class DriverMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id")
    private League league;

    @Column(name = "telemetry_name")
    private String telemetryName;

    @Column(name = "race_number")
    private Integer raceNumber;

    @Column(name = "driver_id")
    private Integer driverId;

    @Column(name = "overridden_name")
    private String overriddenName;

    @Column(name = "is_reserve")
    private Boolean reserve = false;

    @Column(name = "country")
    private String country = "Unknown";

    public boolean isReserve() {
        return reserve != null && reserve;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DriverMapping that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
