package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "league_logo")
public class LeagueLogo {
    @Id
    @Column(name = "league_id")
    private Long leagueId;

    @Column(name = "logo", columnDefinition = "bytea")
    private byte[] logo;
}
