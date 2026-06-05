package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.ManualPenalty;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import java.util.Collection;

@Repository
public interface ManualPenaltyRepository extends JpaRepository<ManualPenalty, Long> {
    List<ManualPenalty> findBySessionResult(SessionResult sessionResult);
    List<ManualPenalty> findBySessionResultIn(Collection<SessionResult> sessionResults);
    List<ManualPenalty> findBySessionResultTier(Tier tier);
    List<ManualPenalty> findBySessionResultTierLeague(League league);
}
