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

    private String driverName;
    private String teamName;
    private int position;
    private int pointsAwarded;
    private int gridPosition;
    private float bestLapTime;
}
