package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "session_point_config")
public class SessionPointConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id")
    private League league;

    /**
     * Session type as defined by F1 UDP spec.
     * @see be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService#SESSION_TYPE_NAMES
     */
    @Column(name = "session_type")
    private Integer sessionType;

    /**
     * Finishing position (1-indexed).
     */
    private Integer position;

    /**
     * Points to award for this position in this session type.
     */
    private Integer points;

    @Column(name = "fastest_lap_points")
    private Integer fastestLapPoints = 0;

    @Column(name = "no_penalty_points")
    private Integer noPenaltyPoints = 0;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionPointConfig that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
