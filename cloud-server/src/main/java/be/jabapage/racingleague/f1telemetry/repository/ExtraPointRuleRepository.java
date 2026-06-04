package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.ExtraPointRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExtraPointRuleRepository extends JpaRepository<ExtraPointRule, Long> {
    List<ExtraPointRule> findByLeague(League league);
    List<ExtraPointRule> findByLeagueAndSessionType(League league, Integer sessionType);
    void deleteByLeague(League league);
}
