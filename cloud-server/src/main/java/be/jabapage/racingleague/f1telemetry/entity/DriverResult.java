package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@Entity
public class DriverResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_result_id")
    private SessionResult sessionResult;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "telemetry_name")
    private String telemetryName;

    @Column(name = "race_number")
    private Integer raceNumber;

    @Column(name = "driver_id")
    private Integer driverId;

    @Column(name = "is_ai")
    private Boolean ai = false;

    public boolean isAi() {
        return ai != null && ai;
    }

    @Column(name = "team_name")
    private String teamName;
    private Integer position;
    @Column(name = "num_laps")
    private Integer numLaps;
    @Column(name = "points_awarded")
    private Integer pointsAwarded;
    @Column(name = "grid_position")
    private Integer gridPosition;
    @Column(name = "best_lap_time")
    private Float bestLapTime;

    @Column(name = "total_time")
    private Double totalTime;

    @Column(name = "gap_to_leader")
    private String gapToLeader;

    @Column(name = "result_status", nullable = false, columnDefinition = "int default 0")
    private Integer resultStatus;

    @Column(name = "penalties", nullable = false, columnDefinition = "int default 0")
    private Integer penalties;

    @OneToMany(mappedBy = "driverResult", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private java.util.Set<TyreStint> tyreStints = new java.util.LinkedHashSet<>();

    @OneToMany(mappedBy = "driverResult", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private java.util.Set<LapResult> lapResults = new java.util.LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DriverResult that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
