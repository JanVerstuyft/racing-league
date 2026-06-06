package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.TeamMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMappingRepository extends JpaRepository<TeamMapping, Long> {
    List<TeamMapping> findByCarType(String carType);
    Optional<TeamMapping> findByTeamNameAndCarType(String teamName, String carType);
    Optional<TeamMapping> findByTeamIdAndCarType(Integer teamId, String carType);
}
