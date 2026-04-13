package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
public class SessionResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "league_id")
    private League league;

    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;

    private String trackId;
    private long sessionUID;
    private int sessionType;

    @OneToMany(mappedBy = "sessionResult", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DriverResult> driverResults = new ArrayList<>();
}
