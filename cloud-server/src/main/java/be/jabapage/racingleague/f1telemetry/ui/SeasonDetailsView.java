package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.TeamStanding;
import be.jabapage.racingleague.f1telemetry.repository.DriverStandingRepository;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamStandingRepository;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.*;

import java.util.Comparator;
import java.util.stream.Collectors;

@PageTitle("Season Details | F1 Telemetry")
@Route(value = "season", layout = MainLayout.class)
public class SeasonDetailsView extends VerticalLayout implements HasUrlParameter<Long> {

    private final LeagueRepository leagueRepository;
    private final EventRepository eventRepository;
    private final DriverStandingRepository driverStandingRepository;
    private final TeamStandingRepository teamStandingRepository;
    private League league;

    private final H2 seasonName = new H2();
    private final Grid<Event> eventGrid = new Grid<>(Event.class, false);
    private final Grid<DriverStanding> driverGrid = new Grid<>(DriverStanding.class, false);
    private final Grid<TeamStanding> teamGrid = new Grid<>(TeamStanding.class, false);

    private final VerticalLayout eventsLayout = new VerticalLayout();
    private final VerticalLayout standingsLayout = new VerticalLayout();
    private final TelemetryProcessingService telemetryProcessingService;

    public SeasonDetailsView(LeagueRepository leagueRepository, EventRepository eventRepository,
                             DriverStandingRepository driverStandingRepository,
                             TeamStandingRepository teamStandingRepository,
                             TelemetryProcessingService telemetryProcessingService) {
        this.leagueRepository = leagueRepository;
        this.eventRepository = eventRepository;
        this.driverStandingRepository = driverStandingRepository;
        this.teamStandingRepository = teamStandingRepository;
        this.telemetryProcessingService = telemetryProcessingService;

        setSizeFull();
        configureGrids();

        Tabs tabs = new Tabs(new Tab("Race Weekends"), new Tab("Standings"));
        tabs.addSelectedChangeListener(e -> {
            boolean isEvents = e.getSelectedTab().getLabel().equals("Race Weekends");
            eventsLayout.setVisible(isEvents);
            standingsLayout.setVisible(!isEvents);
        });

        eventsLayout.add(new H3("Race Weekends"), eventGrid);
        
        Button recalculateBtn = new Button("Recalculate Standings", e -> {
            if (league != null) {
                telemetryProcessingService.recalculateStandings(league.getId());
                updateData();
                Notification.show("Standings recalculated!");
            }
        });
        standingsLayout.add(new HorizontalLayout(new H3("Driver Standings"), recalculateBtn), 
                driverGrid, new H3("Team Standings"), teamGrid);
        standingsLayout.setVisible(false);

        add(seasonName, tabs, eventsLayout, standingsLayout);
    }

    private void configureGrids() {
        eventGrid.addColumn(Event::getEventName).setHeader("Event");
        eventGrid.addColumn(event -> {
            return event.getSessionResults().stream()
                    .map(SessionResult::getSessionType)
                    .map(type -> TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(type, "S" + type))
                    .distinct()
                    .collect(Collectors.joining(", "));
        }).setHeader("Sessions");

        eventGrid.addComponentColumn(event -> {
            HorizontalLayout actions = new HorizontalLayout();

            RouterLink resultsLink = new RouterLink("Results", EventResultsView.class, event.getId());

            Button deleteBtn = new Button("Delete", e -> {
                ConfirmDialog dialog = new ConfirmDialog();
                dialog.setHeader("Delete Weekend?");
                dialog.setText("Are you sure you want to delete the weekend '" + event.getEventName() + "'? Standings will be automatically recalculated.");
                dialog.setCancelable(true);
                dialog.setConfirmText("Delete");
                dialog.setConfirmButtonTheme("error primary");
                dialog.addConfirmListener(ev -> {
                    eventRepository.delete(event);
                    telemetryProcessingService.recalculateStandings(league.getId());
                    updateData();
                    Notification.show("Weekend deleted and standings recalculated");
                });
                dialog.open();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            actions.add(resultsLink, deleteBtn);
            actions.setAlignItems(FlexComponent.Alignment.CENTER);
            return actions;
        }).setHeader("Actions");

        driverGrid.addColumn(DriverStanding::getDriverName).setHeader("Driver");
        driverGrid.addColumn(DriverStanding::getTeamName).setHeader("Team");
        driverGrid.addColumn(ds -> ds.getPoints() != null ? ds.getPoints() : 0).setHeader("Points").setSortable(true);
        driverGrid.addColumn(ds -> ds.getWins() != null ? ds.getWins() : 0).setHeader("Wins");

        teamGrid.addColumn(TeamStanding::getTeamName).setHeader("Team");
        teamGrid.addColumn(ts -> ts.getPoints() != null ? ts.getPoints() : 0).setHeader("Points").setSortable(true);
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        league = leagueRepository.findById(parameter).orElseThrow();
        seasonName.setText("Season: " + league.getName());
        updateData();
    }

    private void updateData() {
        eventGrid.setItems(eventRepository.findByLeague(league));
        driverGrid.setItems(driverStandingRepository.findByLeague(league).stream()
                .sorted(Comparator.comparing((DriverStanding ds) -> ds.getPoints() != null ? ds.getPoints() : 0).reversed())
                .collect(java.util.stream.Collectors.toList()));
        teamGrid.setItems(teamStandingRepository.findByLeague(league).stream()
                .sorted(Comparator.comparing((TeamStanding ts) -> ts.getPoints() != null ? ts.getPoints() : 0).reversed())
                .collect(java.util.stream.Collectors.toList()));
    }
}
