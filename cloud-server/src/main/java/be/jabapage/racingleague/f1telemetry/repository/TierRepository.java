package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TierRepository extends JpaRepository<Tier, Long> {
    Optional<Tier> findByToken(String token);
    java.util.List<Tier> findByLeagueId(Long leagueId);

    @org.springframework.data.jpa.repository.Query("SELECT t FROM Tier t LEFT JOIN FETCH t.league WHERE t.id = :id")
    Optional<Tier> findByIdWithLeague(@org.springframework.data.repository.query.Param("id") Long id);
}
