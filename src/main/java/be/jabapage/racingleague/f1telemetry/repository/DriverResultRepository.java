package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverResultRepository extends JpaRepository<DriverResult, Long> {
    Optional<DriverResult> findBySessionResultAndDriverName(SessionResult sessionResult, String driverName);
}
