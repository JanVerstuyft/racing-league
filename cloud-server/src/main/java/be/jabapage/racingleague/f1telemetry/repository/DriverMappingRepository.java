package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverMappingRepository extends JpaRepository<DriverMapping, Long> {
    @Query("SELECT dm FROM DriverMapping dm LEFT JOIN FETCH dm.tier WHERE dm.tier = :tier")
    List<DriverMapping> findByTier(@Param("tier") Tier tier);
    
    Optional<DriverMapping> findByTierAndTelemetryNameAndRaceNumberAndDriverId(Tier tier, String telemetryName, Integer raceNumber, Integer driverId);
}
