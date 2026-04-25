package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.LapResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LapResultRepository extends JpaRepository<LapResult, Long> {
    List<LapResult> findBySessionUID(Long sessionUID);
    List<LapResult> findBySessionUIDAndCarIndex(Long sessionUID, Integer carIndex);
}
