package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class TeamStanding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "league_id")
    private League league;

    @Column(name = "team_name")
    private String teamName;
    private Integer points;
}
