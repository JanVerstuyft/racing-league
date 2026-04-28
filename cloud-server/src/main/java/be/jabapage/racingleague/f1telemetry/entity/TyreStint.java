package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@Entity
public class TyreStint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "driver_result_id")
    private DriverResult driverResult;

    private Integer stintOrder;
    private Integer tyreCompound; // Visual compound ID
    private Integer endLap;
    private Integer laps;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TyreStint that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
