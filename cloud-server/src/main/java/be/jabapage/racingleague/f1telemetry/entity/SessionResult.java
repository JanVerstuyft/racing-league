package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SessionResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne
    @JoinColumn(name = "league_id")
    private League league;

    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "track_id")
    private String trackId;
    @Column(name = "session_uid")
    private Long sessionUID;
    @Column(name = "session_type")
    private Integer sessionType;

    @OneToMany(mappedBy = "sessionResult", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private Set<DriverResult> driverResults = new LinkedHashSet<>();
}
