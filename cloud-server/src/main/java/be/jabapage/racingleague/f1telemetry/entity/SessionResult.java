package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "session_result", uniqueConstraints = {
    @UniqueConstraint(name = "uk_session_type_tier", columnNames = {"session_uid", "session_type", "tier_id"})
})
public class SessionResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tier_id")
    private Tier tier;

    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "track_id")
    private String trackId;
    @Column(name = "session_uid")
    private Long sessionUID;
    @Column(name = "session_type")
    private Integer sessionType;

    @OneToMany(mappedBy = "sessionResult", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @ToString.Exclude
    private Set<DriverResult> driverResults = new LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionResult that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
