package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverResultRepository extends JpaRepository<DriverResult, Long> {
    Optional<DriverResult> findBySessionResultAndDriverName(SessionResult sessionResult, String driverName);

    @Query("SELECT DISTINCT dr FROM DriverResult dr WHERE dr.sessionResult.id = :sessionResultId AND EXISTS (" +
           "SELECT 1 FROM LapTelemetry lt WHERE lt.lapResult.driverResult = dr" +
           ")")
    List<DriverResult> findDriversWithTelemetryBySessionResultId(@Param("sessionResultId") Long sessionResultId);
}
