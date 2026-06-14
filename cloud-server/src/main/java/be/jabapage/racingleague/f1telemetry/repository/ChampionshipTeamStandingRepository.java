package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.ChampionshipTeamStanding;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChampionshipTeamStandingRepository extends JpaRepository<ChampionshipTeamStanding, Long> {
    List<ChampionshipTeamStanding> findByTier(Tier tier);
    Optional<ChampionshipTeamStanding> findByTierAndChampionshipTeam(Tier tier, String championshipTeam);
}
