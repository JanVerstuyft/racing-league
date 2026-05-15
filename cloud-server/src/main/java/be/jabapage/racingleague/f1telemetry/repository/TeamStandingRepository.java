package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.TeamStanding;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamStandingRepository extends JpaRepository<TeamStanding, Long> {
    Optional<TeamStanding> findByLeagueAndTierIsNullAndTeamName(League league, String teamName);
    List<TeamStanding> findByLeagueAndTierIsNull(League league);
    Optional<TeamStanding> findByTierAndTeamName(Tier tier, String teamName);
    List<TeamStanding> findByTier(Tier tier);
}
