package be.jabapage.racingleague.f1telemetry.ui.tabs;

import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.TeamStanding;
import be.jabapage.racingleague.f1telemetry.repository.DriverStandingRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamStandingRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class StandingsTab extends VerticalLayout {

    private final DriverStandingRepository driverStandingRepository;
    private final TeamStandingRepository teamStandingRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;

    private League league;
    private final Grid<DriverStanding> driverGrid = new Grid<>(DriverStanding.class, false);
    private final Grid<TeamStanding> teamGrid = new Grid<>(TeamStanding.class, false);
    private final VerticalLayout driverStandingsContent = new VerticalLayout();
    private final VerticalLayout teamStandingsContent = new VerticalLayout();
    private final Button recalculateBtn = new Button("Recalculate Standings");

    public StandingsTab(DriverStandingRepository driverStandingRepository,
                        TeamStandingRepository teamStandingRepository,
                        TelemetryProcessingService telemetryProcessingService,
                        SecurityService securityService) {
        this.driverStandingRepository = driverStandingRepository;
        this.teamStandingRepository = teamStandingRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;

        setSizeFull();
        configureGrids();

        Tabs standingsTabs = new Tabs(new Tab("Drivers"), new Tab("Teams"));
        standingsTabs.addSelectedChangeListener(e -> {
            String label = e.getSelectedTab().getLabel();
            driverStandingsContent.setVisible(label.equals("Drivers"));
            teamStandingsContent.setVisible(label.equals("Teams"));
        });

        driverStandingsContent.add(new HorizontalLayout(new H3("Driver Standings"), recalculateBtn),
                driverGrid);
        driverStandingsContent.setPadding(false);
        
        teamStandingsContent.add(new H3("Team Standings"), teamGrid);
        teamStandingsContent.setVisible(false);
        teamStandingsContent.setPadding(false);

        add(standingsTabs, driverStandingsContent, teamStandingsContent);

        recalculateBtn.addClickListener(e -> {
            if (league != null) {
                telemetryProcessingService.recalculateStandings(league.getId());
                updateData();
                Notification.show("Standings recalculated!", 3000, Notification.Position.TOP_CENTER);
            }
        });
    }

    private void configureGrids() {
        driverGrid.setSelectionMode(Grid.SelectionMode.NONE);
        driverGrid.addComponentColumn(ds -> {
            HorizontalLayout nameLayout = new HorizontalLayout();
            nameLayout.setAlignItems(Alignment.CENTER);
            nameLayout.setSpacing(false);

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
    }

    public void setLeague(League league) {
        this.league = league;
        recalculateBtn.setVisible(securityService.getAuthenticatedUser().isPresent());
        updateData();
    }

    public void updateData() {
        if (league == null) return;

        List<DriverStanding> standings = driverStandingRepository.findByLeague(league);
        if (league.isHideAi()) {
            standings = standings.stream().filter(s -> !s.isAi()).toList();
        }

        driverGrid.setItems(standings.stream()
                .sorted(Comparator.comparing((DriverStanding ds) -> ds.getPoints() != null ? ds.getPoints() : 0).reversed())
                .collect(Collectors.toList()));

        teamGrid.setItems(teamStandingRepository.findByLeague(league).stream()
                .sorted(Comparator.comparing((TeamStanding ts) -> ts.getPoints() != null ? ts.getPoints() : 0).reversed())
                .collect(Collectors.toList()));
    }
}
