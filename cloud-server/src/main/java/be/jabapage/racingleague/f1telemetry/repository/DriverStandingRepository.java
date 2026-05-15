package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverStandingRepository extends JpaRepository<DriverStanding, Long> {
    Optional<DriverStanding> findByTierAndDriverName(Tier tier, String driverName);
    List<DriverStanding> findByTier(Tier tier);
}
