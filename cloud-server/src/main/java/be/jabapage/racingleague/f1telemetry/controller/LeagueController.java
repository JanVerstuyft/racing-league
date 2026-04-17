package be.jabapage.racingleague.f1telemetry.controller;

import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.TeamStanding;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leagues")
public class LeagueController {

    @Autowired
    private LeagueRepository leagueRepository;

    @Autowired
    private TelemetryProcessingService telemetryProcessingService;

    @PostMapping
    public League createLeague(@RequestBody String name) {
        League league = new League();
        league.setName(name);
        return leagueRepository.save(league);
    }

    @GetMapping
    public List<League> getAllLeagues() {
        return leagueRepository.findAll();
    }

    @PostMapping("/{id}/activate")
    public void activateLeague(@PathVariable Long id) {
        telemetryProcessingService.setActiveLeague(id);
    }

    @PostMapping("/{id}/recalculate")
    public void recalculateStandings(@PathVariable Long id) {
        telemetryProcessingService.recalculateStandings(id);
    }

    @GetMapping("/{id}/standings/drivers")
    public List<DriverStanding> getDriverStandings(@PathVariable Long id) {
        return leagueRepository.findById(id)
                .map(league -> league.getDriverStandings().stream()
                        .sorted(Comparator.comparingInt(DriverStanding::getPoints).reversed())
                        .collect(Collectors.toList()))
                .orElseThrow();
    }

    @GetMapping("/{id}/standings/teams")
    public List<TeamStanding> getTeamStandings(@PathVariable Long id) {
        return leagueRepository.findById(id)
                .map(league -> league.getTeamStandings().stream()
                        .sorted(Comparator.comparingInt(TeamStanding::getPoints).reversed())
                        .collect(Collectors.toList()))
                .orElseThrow();
    }
}
