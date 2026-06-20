package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.LapResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface LapResultRepository extends JpaRepository<LapResult, Long> {
    List<LapResult> findBySessionUID(Long sessionUID);
    List<LapResult> findBySessionUIDAndCarIndex(Long sessionUID, Integer carIndex);
    List<LapResult> findByDriverResult(DriverResult driverResult);

    @Query("SELECT lr FROM LapResult lr " +
           "WHERE lr.driverResult = :driverResult " +
           "AND EXISTS (SELECT 1 FROM LapTelemetry lt WHERE lt.lapResult = lr) " +
           "ORDER BY lr.lapTimeInMS ASC")
    List<LapResult> findLapsWithTelemetryOrderedByTime(@Param("driverResult") DriverResult driverResult);
}
