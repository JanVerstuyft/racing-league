package be.jabapage.racingleague.f1telemetry.ui.season;

import be.jabapage.racingleague.f1telemetry.entity.ChampionshipTeamStanding;
import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.ManualPenalty;
import be.jabapage.racingleague.f1telemetry.entity.TeamStanding;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.repository.ChampionshipTeamStandingRepository;
import be.jabapage.racingleague.f1telemetry.repository.DriverStandingRepository;
import be.jabapage.racingleague.f1telemetry.repository.ManualPenaltyRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamStandingRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.ui.SeasonDetailsView.PenaltyStandingRow;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StandingsTab extends VerticalLayout {

    private final DriverStandingRepository driverStandingRepository;
    private final TeamStandingRepository teamStandingRepository;
    private final ChampionshipTeamStandingRepository championshipTeamStandingRepository;
    private final ManualPenaltyRepository manualPenaltyRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;
    private final Runnable onDataChanged;

    private League league;
    private Tier selectedTier;
    private List<Tier> leagueTiers = Collections.emptyList();

    private final Grid<DriverStanding> driverGrid = new Grid<>(DriverStanding.class, false);
    private final Grid<TeamStanding> teamGrid = new Grid<>(TeamStanding.class, false);
    private final Grid<TeamStanding> leagueTeamGrid = new Grid<>(TeamStanding.class, false);
    private final Grid<ChampionshipTeamStanding> champTeamGrid = new Grid<>(ChampionshipTeamStanding.class, false);
    private final Grid<ChampionshipTeamStanding> leagueChampTeamGrid = new Grid<>(ChampionshipTeamStanding.class, false);
    private final Grid<PenaltyStandingRow> penaltiesGrid = new Grid<>();
    private final Grid<PenaltyStandingRow> leaguePenaltiesGrid = new Grid<>();

    private final Tab champTeamsTab = new Tab("Championship Teams");
    private final Tab leagueChampTeamsTab = new Tab("League Championship Teams");
    private final Tabs standingsTabs = new Tabs();

    private final VerticalLayout driverStandingsContent = new VerticalLayout();
    private final VerticalLayout teamStandingsContent = new VerticalLayout();
    private final VerticalLayout leagueTeamStandingsContent = new VerticalLayout();
    private final VerticalLayout champTeamStandingsContent = new VerticalLayout();
    private final VerticalLayout leagueChampTeamStandingsContent = new VerticalLayout();
    private final VerticalLayout penaltiesContent = new VerticalLayout();
    private final VerticalLayout leaguePenaltiesContent = new VerticalLayout();

    private final Button recalculateBtn = new Button("Recalculate Standings");

    public StandingsTab(DriverStandingRepository driverStandingRepository,
                        TeamStandingRepository teamStandingRepository,
                        ChampionshipTeamStandingRepository championshipTeamStandingRepository,
                        ManualPenaltyRepository manualPenaltyRepository,
                        TelemetryProcessingService telemetryProcessingService,
                        SecurityService securityService,
                        Runnable onDataChanged) {
        this.driverStandingRepository = driverStandingRepository;
        this.teamStandingRepository = teamStandingRepository;
        this.championshipTeamStandingRepository = championshipTeamStandingRepository;
        this.manualPenaltyRepository = manualPenaltyRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;
        this.onDataChanged = onDataChanged;

        setSizeFull();
        setPadding(false);

        recalculateBtn.addClickListener(e -> {
            if (selectedTier != null) {
                telemetryProcessingService.recalculateStandings(selectedTier.getId());
                onDataChanged.run();
                Notification.show("Standings recalculated!", 3000, Notification.Position.TOP_CENTER);
            }
        });

        // Inner tabs for Standings
        Tab penaltiesTab = new Tab("Penalties");
        Tab leaguePenaltiesTab = new Tab("League Penalties");
        standingsTabs.add(new Tab("Drivers"), new Tab("Teams"), new Tab("League Teams"), champTeamsTab, leagueChampTeamsTab, penaltiesTab, leaguePenaltiesTab);
        standingsTabs.addSelectedChangeListener(e -> {
            if (e.getSelectedTab() == null) return;
            String label = e.getSelectedTab().getLabel();
            driverStandingsContent.setVisible(label.equals("Drivers"));
            teamStandingsContent.setVisible(label.equals("Teams"));
            leagueTeamStandingsContent.setVisible(label.equals("League Teams"));
            champTeamStandingsContent.setVisible(label.equals("Championship Teams"));
            leagueChampTeamStandingsContent.setVisible(label.equals("League Championship Teams"));
            penaltiesContent.setVisible(label.equals("Penalties"));
            leaguePenaltiesContent.setVisible(label.equals("League Penalties"));
        });

        driverGrid.setSelectionMode(Grid.SelectionMode.NONE);
        configureGrids();

        driverStandingsContent.add(new HorizontalLayout(new H3("Driver Standings"), recalculateBtn), driverGrid);
        driverStandingsContent.setPadding(false);

        teamStandingsContent.add(new H3("Team Standings"), teamGrid);
        teamStandingsContent.setVisible(false);
        teamStandingsContent.setPadding(false);

        leagueTeamStandingsContent.add(new H3("League Team Standings (Over All Tiers)"), leagueTeamGrid);
        leagueTeamStandingsContent.setVisible(false);
        leagueTeamStandingsContent.setPadding(false);

        champTeamStandingsContent.add(new H3("Championship Team Standings"), champTeamGrid);
        champTeamStandingsContent.setVisible(false);
        champTeamStandingsContent.setPadding(false);

        leagueChampTeamStandingsContent.add(new H3("League Championship Team Standings (Over All Tiers)"), leagueChampTeamGrid);
        leagueChampTeamStandingsContent.setVisible(false);
        leagueChampTeamStandingsContent.setPadding(false);

        penaltiesContent.add(new H3("Incident & Penalties Standings (This Tier)"), penaltiesGrid);
        penaltiesContent.setVisible(false);
        penaltiesContent.setPadding(false);

        leaguePenaltiesContent.add(new H3("Incident & Penalties Standings (League Wide)"), leaguePenaltiesGrid);
        leaguePenaltiesContent.setVisible(false);
        leaguePenaltiesContent.setPadding(false);

        add(standingsTabs, driverStandingsContent, teamStandingsContent, leagueTeamStandingsContent, champTeamStandingsContent, leagueChampTeamStandingsContent, penaltiesContent, leaguePenaltiesContent);
    }

    private void configureGrids() {
        driverGrid.addComponentColumn(ds -> {
            HorizontalLayout nameLayout = new HorizontalLayout();
            nameLayout.setAlignItems(Alignment.CENTER);
            nameLayout.setSpacing(false);

            Span flagSpan = new Span(CountryProvider.getFlagByName(ds.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            nameLayout.add(flagSpan);

            if (ds.getRaceNumber() != null && ds.getRaceNumber() > 0) {
                Span raceNum = new Span("#" + ds.getRaceNumber());
                raceNum.getStyle().set("color", "var(--lumo-secondary-text-color)");
                raceNum.getStyle().set("font-size", "0.8em");
                raceNum.getStyle().set("margin-right", "var(--lumo-space-s)");
                nameLayout.add(raceNum);
            }

            Span name = new Span(ds.getDriverName());
            nameLayout.add(name);

            if (ds.isAi()) {
                Span badge = new Span("AI");
                badge.getElement().getThemeList().add("badge contrast small");
                badge.getStyle().set("margin-left", "var(--lumo-space-s)");
                nameLayout.add(badge);
            }
            return nameLayout;
        }).setHeader("Driver").setSortable(true).setComparator(DriverStanding::getDriverName);

        driverGrid.addColumn(DriverStanding::getTeamName).setHeader("Team");
        driverGrid.addColumn(ds -> ds.getPoints() != null ? ds.getPoints() : 0).setHeader("Points").setSortable(true);
        driverGrid.addColumn(ds -> ds.getWins() != null ? ds.getWins() : 0).setHeader("Wins");

        teamGrid.addColumn(TeamStanding::getTeamName).setHeader("Team");
        teamGrid.addColumn(ts -> ts.getPoints() != null ? ts.getPoints() : 0).setHeader("Points").setSortable(true);

        leagueTeamGrid.addColumn(TeamStanding::getTeamName).setHeader("Team");
        leagueTeamGrid.addColumn(ts -> ts.getPoints() != null ? ts.getPoints() : 0).setHeader("Points").setSortable(true);

        configurePenaltyGrid(penaltiesGrid);
        configurePenaltyGrid(leaguePenaltiesGrid);

        champTeamGrid.addColumn(cts -> {
            if ("A".equals(cts.getChampionshipTeam())) {
                return league != null && league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
            } else if ("B".equals(cts.getChampionshipTeam())) {
                return league != null && league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
            }
            return cts.getChampionshipTeam();
        }).setHeader("Championship Team");
        champTeamGrid.addColumn(ChampionshipTeamStanding::getPoints).setHeader("Points").setSortable(true);

        leagueChampTeamGrid.addColumn(cts -> {
            if ("A".equals(cts.getChampionshipTeam())) {
                return league != null && league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
            } else if ("B".equals(cts.getChampionshipTeam())) {
                return league != null && league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
            }
            return cts.getChampionshipTeam();
        }).setHeader("Championship Team");
        leagueChampTeamGrid.addColumn(ChampionshipTeamStanding::getPoints).setHeader("Points").setSortable(true);
    }

    private void configurePenaltyGrid(Grid<PenaltyStandingRow> grid) {
        grid.addComponentColumn(ps -> {
            HorizontalLayout nameLayout = new HorizontalLayout();
            nameLayout.setAlignItems(Alignment.CENTER);
            nameLayout.setSpacing(false);

            Span flagSpan = new Span(CountryProvider.getFlagByName(ps.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            nameLayout.add(flagSpan);

            if (ps.getRaceNumber() != null && ps.getRaceNumber() > 0) {
                Span raceNum = new Span("#" + ps.getRaceNumber());
                raceNum.getStyle().set("color", "var(--lumo-secondary-text-color)");
                raceNum.getStyle().set("font-size", "0.8em");
                raceNum.getStyle().set("margin-right", "var(--lumo-space-s)");
                nameLayout.add(raceNum);
            }

            Span name = new Span(ps.getDriverName());
            nameLayout.add(name);
            return nameLayout;
        }).setHeader("Driver").setSortable(true).setComparator(PenaltyStandingRow::getDriverName);

        grid.addColumn(PenaltyStandingRow::getTeamName).setHeader("Team");
        grid.addColumn(PenaltyStandingRow::getIncidentCount).setHeader("Incidents").setSortable(true);
        grid.addColumn(PenaltyStandingRow::getTotalGivenSeconds).setHeader("Total Seconds").setSortable(true);
        grid.addColumn(PenaltyStandingRow::getTotalPointsDeducted).setHeader("Points Deducted").setSortable(true);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
    }

    private void updatePenaltiesData() {
        if (selectedTier == null) return;
        List<ManualPenalty> penalties = manualPenaltyRepository.findBySessionResultTier(selectedTier);
        List<PenaltyStandingRow> rows = buildPenaltyStandingRows(penalties, selectedTier);
        penaltiesGrid.setItems(rows);
    }

    private void updateLeaguePenaltiesData() {
        if (league == null) return;
        List<ManualPenalty> penalties = manualPenaltyRepository.findBySessionResultTierLeague(league);
        List<PenaltyStandingRow> rows = buildPenaltyStandingRows(penalties, null);
        leaguePenaltiesGrid.setItems(rows);
    }

    private List<PenaltyStandingRow> buildPenaltyStandingRows(List<ManualPenalty> penalties, Tier tier) {
        Map<DriverMapping, List<ManualPenalty>> grouped = penalties.stream()
                .collect(Collectors.groupingBy(ManualPenalty::getDriverMapping));

        // Pre-fetch standings to avoid N+1 queries
        Map<String, String> driverToTeamMap = new HashMap<>();
        Map<String, List<DriverStanding>> groupedStandings = new HashMap<>();

        if (tier != null) {
            List<DriverStanding> standings = driverStandingRepository.findByTier(tier);
            for (DriverStanding ds : standings) {
                String key = ds.getDriverName() + "|" + ds.getRaceNumber() + "|" + ds.getCountry();
                driverToTeamMap.put(key, ds.getTeamName());
            }
        } else if (league != null) {
            List<DriverStanding> allStandings = new ArrayList<>();
            for (Tier t : leagueTiers) {
                allStandings.addAll(driverStandingRepository.findByTier(t));
            }
            groupedStandings = allStandings.stream()
                    .collect(Collectors.groupingBy(
                            ds -> ds.getDriverName() + "|" + ds.getRaceNumber() + "|" + ds.getCountry()
                    ));
        }

        List<PenaltyStandingRow> rows = new ArrayList<>();
        for (Map.Entry<DriverMapping, List<ManualPenalty>> entry : grouped.entrySet()) {
            DriverMapping mapping = entry.getKey();
            List<ManualPenalty> list = entry.getValue();

            String name = mapping.getOverriddenName() != null && !mapping.getOverriddenName().isEmpty() 
                    ? mapping.getOverriddenName() 
                    : mapping.getTelemetryName();

            long incidentCount = list.stream()
                    .filter(p -> (p.getSeconds() != null && p.getSeconds() > 0) || (p.getPointDeduction() != null && p.getPointDeduction() > 0))
                    .count();

            if (incidentCount == 0) {
                continue;
            }

            long totalPointsDeducted = list.stream()
                    .mapToLong(p -> p.getPointDeduction() != null ? p.getPointDeduction() : 0)
                    .sum();

            long totalGivenSeconds = list.stream()
                    .mapToLong(p -> p.getSeconds() != null && p.getSeconds() > 0 ? p.getSeconds() : 0)
                    .sum();

            String teamName = "Unknown";
            String lookupKey = name + "|" + mapping.getRaceNumber() + "|" + mapping.getCountry();

            if (tier != null) {
                teamName = driverToTeamMap.getOrDefault(lookupKey, "Unknown");
            } else {
                List<DriverStanding> standings = groupedStandings.getOrDefault(lookupKey, Collections.emptyList());
                teamName = standings.stream()
                        .map(DriverStanding::getTeamName)
                        .filter(t -> t != null && !t.isEmpty())
                        .distinct()
                        .collect(Collectors.joining(", "));
                if (teamName.isEmpty()) teamName = "Unknown";
            }

            PenaltyStandingRow row = new PenaltyStandingRow();
            row.setDriverName(name);
            row.setTeamName(teamName);
            row.setCountry(mapping.getCountry());
            row.setRaceNumber(mapping.getRaceNumber());
            row.setIncidentCount(incidentCount);
            row.setTotalPointsDeducted(totalPointsDeducted);
            row.setTotalGivenSeconds(totalGivenSeconds);
            rows.add(row);
        }

        rows.sort((r1, r2) -> {
            int comp = Long.compare(r2.getTotalGivenSeconds(), r1.getTotalGivenSeconds());
            if (comp != 0) return comp;
            comp = Long.compare(r2.getTotalPointsDeducted(), r1.getTotalPointsDeducted());
            if (comp != 0) return comp;
            comp = Long.compare(r2.getIncidentCount(), r1.getIncidentCount());
            if (comp != 0) return comp;
            return r1.getDriverName().compareToIgnoreCase(r2.getDriverName());
        });

        return rows;
    }

    public void update(League league, Tier selectedTier, List<Tier> leagueTiers) {
        this.league = league;
        this.selectedTier = selectedTier;
        this.leagueTiers = leagueTiers;

        if (league == null || selectedTier == null) return;

        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        recalculateBtn.setVisible(loggedIn);

        List<DriverStanding> standings = driverStandingRepository.findByTier(selectedTier);
        if (league.isHideAi()) {
            standings = standings.stream().filter(s -> !s.isAi()).toList();
        }

        driverGrid.setItems(standings.stream()
                .sorted(Comparator.comparing((DriverStanding ds) -> ds.getPoints() != null ? ds.getPoints() : 0).reversed())
                .collect(Collectors.toList()));

        teamGrid.setItems(teamStandingRepository.findByTier(selectedTier).stream()
                .sorted(Comparator.comparing((TeamStanding ts) -> ts.getPoints() != null ? ts.getPoints() : 0).reversed())
                .collect(Collectors.toList()));

        // Update League-wide Team Standings (dynamic aggregation over all tiers)
        List<TeamStanding> allTiersTeamStandings = new ArrayList<>();
        for (Tier tier : leagueTiers) {
            allTiersTeamStandings.addAll(teamStandingRepository.findByTier(tier));
        }
        java.util.Map<String, Integer> leagueTeamPoints = allTiersTeamStandings.stream()
                .collect(Collectors.groupingBy(
                        TeamStanding::getTeamName,
                        Collectors.summingInt(ts -> ts.getPoints() != null ? ts.getPoints() : 0)
                ));
        List<TeamStanding> leagueTeamStandings = leagueTeamPoints.entrySet().stream()
                .map(entry -> {
                    TeamStanding ts = new TeamStanding();
                    ts.setTeamName(entry.getKey());
                    ts.setPoints(entry.getValue());
                    return ts;
                })
                .sorted(Comparator.comparing((TeamStanding ts) -> ts.getPoints() != null ? ts.getPoints() : 0).reversed())
                .collect(Collectors.toList());
        leagueTeamGrid.setItems(leagueTeamStandings);

        updatePenaltiesData();
        updateLeaguePenaltiesData();

        boolean teamsEnabled = league != null && Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
            && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
            && league.getTeamBName() != null && !league.getTeamBName().isEmpty();

        champTeamsTab.setVisible(teamsEnabled);
        leagueChampTeamsTab.setVisible(teamsEnabled);

        if (!teamsEnabled) {
            String selectedLabel = standingsTabs.getSelectedTab() != null ? standingsTabs.getSelectedTab().getLabel() : "";
            if (selectedLabel.equals("Championship Teams") || selectedLabel.equals("League Championship Teams")) {
                standingsTabs.setSelectedIndex(0); // Select Drivers
            }
        } else {
            // Populate tier-level Championship Team standings
            List<ChampionshipTeamStanding> ctsList = championshipTeamStandingRepository.findByTier(selectedTier);
            ctsList.sort(Comparator.comparing((ChampionshipTeamStanding cts) -> cts.getPoints() != null ? cts.getPoints() : 0).reversed());
            champTeamGrid.setItems(ctsList);

            // Populate league-wide Championship Team standings (aggregate over all tiers)
            List<ChampionshipTeamStanding> allTiersCts = new ArrayList<>();
            for (Tier tier : leagueTiers) {
                allTiersCts.addAll(championshipTeamStandingRepository.findByTier(tier));
            }
            Map<String, Integer> leagueCtsPoints = allTiersCts.stream()
                .collect(Collectors.groupingBy(
                    ChampionshipTeamStanding::getChampionshipTeam,
                    Collectors.summingInt(cts -> cts.getPoints() != null ? cts.getPoints() : 0)
                ));
            List<ChampionshipTeamStanding> leagueCtsStandings = leagueCtsPoints.entrySet().stream()
                .map(entry -> {
                    ChampionshipTeamStanding cts = new ChampionshipTeamStanding();
                    cts.setChampionshipTeam(entry.getKey());
                    cts.setPoints(entry.getValue());
                    return cts;
                })
                .sorted(Comparator.comparing((ChampionshipTeamStanding cts) -> cts.getPoints() != null ? cts.getPoints() : 0).reversed())
                .collect(Collectors.toList());
            leagueChampTeamGrid.setItems(leagueCtsStandings);
        }
    }
}
