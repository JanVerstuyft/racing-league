package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.SessionPointConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionPointConfigRepository extends JpaRepository<SessionPointConfig, Long> {
    List<SessionPointConfig> findByLeague(League league);
    void deleteByLeague(League league);
}
