package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.LapTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LapTelemetryRepository extends JpaRepository<LapTelemetry, Long> {
    Optional<LapTelemetry> findByLapResultId(Long lapResultId);
}
