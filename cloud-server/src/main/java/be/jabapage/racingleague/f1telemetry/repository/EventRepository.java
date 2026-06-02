package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByTier(Tier tier);
    Optional<Event> findByTierAndTrackId(Tier tier, String trackId);

    @Query("SELECT DISTINCT e FROM Event e " +
           "LEFT JOIN FETCH e.sessionResults sr " +
           "LEFT JOIN FETCH sr.driverResults " +
           "LEFT JOIN FETCH e.tier t " +
           "LEFT JOIN FETCH t.league " +
           "WHERE e.id = :id")
    Optional<Event> findByIdWithResults(@Param("id") Long id);
}
