package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionResultRepository extends JpaRepository<SessionResult, Long> {
    Optional<SessionResult> findBySessionUIDAndSessionTypeAndTier(long sessionUID, int sessionType, Tier tier);
    Optional<SessionResult> findBySessionUIDAndSessionType(long sessionUID, int sessionType);
    Optional<SessionResult> findBySessionUIDAndTier(long sessionUID, Tier tier);
    List<SessionResult> findAllBySessionUIDAndTier(long sessionUID, Tier tier);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM SessionResult s WHERE s.sessionUID = :sessionUID AND s.tier = :tier")
    void deleteBySessionUIDAndTier(@org.springframework.data.repository.query.Param("sessionUID") long sessionUID, @org.springframework.data.repository.query.Param("tier") Tier tier);
    Optional<SessionResult> findBySessionUID(long sessionUID);
    List<SessionResult> findByTier(Tier tier);
}
