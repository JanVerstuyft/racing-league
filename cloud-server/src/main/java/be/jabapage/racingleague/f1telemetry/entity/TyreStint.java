package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TyreStint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne
    @JoinColumn(name = "driver_result_id")
    private DriverResult driverResult;

    private Integer stintOrder;
    private Integer tyreCompound; // Visual compound ID
    private Integer endLap;
    private Integer laps;
}
