package be.jabapage.racingleague.f1telemetry.repository;

import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.EventLineupEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventLineupEntryRepository extends JpaRepository<EventLineupEntry, Long> {
    List<EventLineupEntry> findByEvent(Event event);
    void deleteByEvent(Event event);
}
