package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.LapTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

@Repository
public interface LapTelemetryRepository extends JpaRepository<LapTelemetry, Long> {
    Optional<LapTelemetry> findByLapResultId(Long lapResultId);

    @Query("SELECT COUNT(lt) > 0 FROM LapTelemetry lt WHERE lt.lapResult.driverResult.sessionResult.id = :sessionResultId")
    boolean existsBySessionResultId(@Param("sessionResultId") Long sessionResultId);
}
