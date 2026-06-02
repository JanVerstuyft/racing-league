package be.jabapage.racingleague.f1telemetry.controller;

import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.TeamStanding;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
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

    @PostMapping("/{id}/recalculate")
    public void recalculateStandings(@PathVariable Long id) {
        leagueRepository.findById(id).ifPresent(league -> {
            league.getTiers().forEach(tier -> telemetryProcessingService.recalculateStandings(tier.getId()));
        });
    }

    @GetMapping("/{id}/standings/drivers")
    public List<DriverStanding> getDriverStandings(@PathVariable Long id) {
        return leagueRepository.findById(id)
                .map(league -> league.getTiers().stream()
                        .sorted(Comparator.comparing(Tier::getName))
                        .findFirst()
                        .map(tier -> tier.getDriverStandings().stream()
                                .sorted(Comparator.comparingInt(DriverStanding::getPoints).reversed())
                                .collect(Collectors.toList()))
                        .orElse(List.of()))
                .orElseThrow();
    }

    @GetMapping("/{id}/standings/teams")
    public List<TeamStanding> getTeamStandings(@PathVariable Long id) {
        return leagueRepository.findById(id)
                .map(league -> league.getTiers().stream()
                        .sorted(Comparator.comparing(Tier::getName))
                        .findFirst()
                        .map(tier -> tier.getTeamStandings().stream()
                                .sorted(Comparator.comparingInt(TeamStanding::getPoints).reversed())
                                .collect(Collectors.toList()))
                        .orElse(List.of()))
                .orElseThrow();
    }
}
