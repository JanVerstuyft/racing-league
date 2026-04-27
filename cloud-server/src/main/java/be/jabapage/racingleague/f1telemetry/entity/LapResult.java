package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class LapResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne
    @JoinColumn(name = "driver_result_id")
    private DriverResult driverResult;

    private Long sessionUID;
    private Integer carIndex;
    private Integer lapNumber;
    private Long lapTimeInMS;
    private Long s1InMS;
    private Long s2InMS;
    private Long s3InMS;
    private Integer tyreCompound;
    private Boolean isValid;
    private Integer pitStopCount;
}
