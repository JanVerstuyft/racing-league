package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverStandingRepository extends JpaRepository<DriverStanding, Long> {
    Optional<DriverStanding> findByLeagueAndDriverNameAndRaceNumberAndCountry(League league, String driverName, Integer raceNumber, String country);
    List<DriverStanding> findByLeague(League league);
}
