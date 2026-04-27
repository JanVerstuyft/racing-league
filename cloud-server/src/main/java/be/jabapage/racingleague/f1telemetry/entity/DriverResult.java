package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
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
    @Column(name = "team_name")
    private String teamName;
    private Integer position;
    @Column(name = "points_awarded")
    private Integer pointsAwarded;
    @Column(name = "grid_position")
    private Integer gridPosition;
    @Column(name = "best_lap_time")
    private Float bestLapTime;

    @Column(name = "result_status", nullable = false, columnDefinition = "int default 0")
    private Integer resultStatus;

    @Column(name = "penalties", nullable = false, columnDefinition = "int default 0")
    private Integer penalties;

    @OneToMany(mappedBy = "driverResult", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private java.util.List<TyreStint> tyreStints = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "driverResult", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private java.util.List<LapResult> lapResults = new java.util.ArrayList<>();
}
