package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.entity.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TierRepository extends JpaRepository<Tier, Long> {
    
    Optional<Tier> findByToken(String token);

    List<Tier> findByLeague(League league);

    List<Tier> findByLeagueOrderByNameAsc(League league);

    @Query("SELECT DISTINCT t FROM Tier t " +
           "LEFT JOIN FETCH t.events e " +
           "LEFT JOIN FETCH e.sessionResults " +
           "WHERE t.id = :id")
    Optional<Tier> findByIdWithEvents(@Param("id") Long id);
}
