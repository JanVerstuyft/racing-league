package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class LiveState {
    @Id
    private Long leagueId;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String jsonState;
}
