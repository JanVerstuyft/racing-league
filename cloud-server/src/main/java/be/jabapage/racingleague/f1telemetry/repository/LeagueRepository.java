package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeagueRepository extends JpaRepository<League, Long> {
    @Query("SELECT DISTINCT l FROM League l " +
           "LEFT JOIN FETCH l.events e " +
           "LEFT JOIN FETCH e.sessionResults " +
           "WHERE l.id = :id")
    Optional<League> findByIdWithEvents(@Param("id") Long id);

    @Query("SELECT DISTINCT l FROM League l " +
           "LEFT JOIN FETCH l.tiers " +
           "WHERE l.id = :id")
    Optional<League> findByIdWithTiers(@Param("id") Long id);

    java.util.List<be.jabapage.racingleague.f1telemetry.entity.League> findByUser(be.jabapage.racingleague.f1telemetry.entity.User user);
}
