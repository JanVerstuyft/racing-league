package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverMappingRepository extends JpaRepository<DriverMapping, Long> {
    List<DriverMapping> findByLeague(League league);
    Optional<DriverMapping> findByLeagueAndTelemetryNameAndRaceNumberAndDriverId(League league, String telemetryName, Integer raceNumber, Integer driverId);
}
