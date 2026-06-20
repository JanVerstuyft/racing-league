package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@Entity
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tier_id")
    private Tier tier;

    private String trackId;
    private String eventName; // e.g., "British Grand Prix"

    @Column(name = "finalized", nullable = false)
    private Boolean finalized = false;

    @Column(name = "status", nullable = false)
    private String status = "PROVISIONAL";

    @Column(name = "fallback", nullable = false)
    private Boolean fallback = false;

    public void setFinalized(Boolean finalized) {
        this.finalized = finalized;
        if (Boolean.TRUE.equals(finalized)) {
            this.status = "FINAL";
        } else {
            this.status = Boolean.TRUE.equals(this.fallback) ? "PROVISIONAL_WARNING" : "PROVISIONAL";
        }
    }

    public void setFallback(Boolean fallback) {
        this.fallback = fallback;
        if (!"FINAL".equals(this.status)) {
            this.status = Boolean.TRUE.equals(fallback) ? "PROVISIONAL_WARNING" : "PROVISIONAL";
        }
    }

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "planned_date")
    private LocalDate plannedDate;


    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @ToString.Exclude
    private Set<SessionResult> sessionResults = new LinkedHashSet<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<EventLineupEntry> lineupEntries = new LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Event that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
