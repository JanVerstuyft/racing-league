package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@Entity
public class TeamStanding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tier_id")
    private Tier tier;

    @Column(name = "team_id")
    private Integer teamId;

    @Column(name = "car_type")
    private String carType;

    @Transient
    private String transientTeamName;

    public void setTeamName(String teamName) {
        this.transientTeamName = teamName;
    }

    public String getTeamName() {
        if (transientTeamName != null) {
            return transientTeamName;
        }
        return be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService.getTeamNameStatic(teamId, carType);
    }

    private Integer points;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamStanding that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
