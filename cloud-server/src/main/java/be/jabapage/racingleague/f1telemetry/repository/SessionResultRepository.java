package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionResultRepository extends JpaRepository<SessionResult, Long> {
    Optional<SessionResult> findBySessionUIDAndSessionType(long sessionUID, int sessionType);
    Optional<SessionResult> findBySessionUIDAndTier(long sessionUID, Tier tier);
    Optional<SessionResult> findBySessionUID(long sessionUID);
    List<SessionResult> findByTier(Tier tier);
}
