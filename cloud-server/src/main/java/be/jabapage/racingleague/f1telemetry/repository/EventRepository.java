package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByLeague(League league);
    Optional<Event> findByLeagueAndTrackId(League league, String trackId);

    @Query("SELECT DISTINCT e FROM Event e " +
           "LEFT JOIN FETCH e.league l " +
           "LEFT JOIN FETCH l.tiers " +
           "LEFT JOIN FETCH e.sessionResults sr " +
           "LEFT JOIN FETCH sr.driverResults " +
           "WHERE e.id = :id")
    Optional<Event> findByIdWithResults(@Param("id") Long id);
}
