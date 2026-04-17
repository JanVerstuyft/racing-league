package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class DriverStanding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "league_id")
    private League league;

    @Column(name = "driver_name")
    private String driverName;
    private Integer points;
    private Integer wins;
    private Integer podiums;
}
