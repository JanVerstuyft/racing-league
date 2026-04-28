package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@Entity
public class LapResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LapResult that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
