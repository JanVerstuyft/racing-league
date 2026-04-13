package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.TeamStanding;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.*;

import java.util.Comparator;

@PageTitle("Season Details | F1 Telemetry")
@Route(value = "season", layout = MainLayout.class)
public class SeasonDetailsView extends VerticalLayout implements HasUrlParameter<Long> {

    private final LeagueRepository leagueRepository;
    private final EventRepository eventRepository;
    private League league;

    private final H2 seasonName = new H2();
    private final Grid<Event> eventGrid = new Grid<>(Event.class, false);
    private final Grid<DriverStanding> driverGrid = new Grid<>(DriverStanding.class, false);
    private final Grid<TeamStanding> teamGrid = new Grid<>(TeamStanding.class, false);

    private final VerticalLayout eventsLayout = new VerticalLayout();
    private final VerticalLayout standingsLayout = new VerticalLayout();

    public SeasonDetailsView(LeagueRepository leagueRepository, EventRepository eventRepository) {
        this.leagueRepository = leagueRepository;
        this.eventRepository = eventRepository;

        setSizeFull();
        configureGrids();

        Tabs tabs = new Tabs(new Tab("Race Weekends"), new Tab("Standings"));
        tabs.addSelectedChangeListener(e -> {
            boolean isEvents = e.getSelectedTab().getLabel().equals("Race Weekends");
            eventsLayout.setVisible(isEvents);
            standingsLayout.setVisible(!isEvents);
        });

        eventsLayout.add(new H3("Race Weekends"), eventGrid);
        standingsLayout.add(new H3("Driver Standings"), driverGrid, new H3("Team Standings"), teamGrid);
        standingsLayout.setVisible(false);

        add(seasonName, tabs, eventsLayout, standingsLayout);
    }

    private void configureGrids() {
        eventGrid.addColumn(Event::getEventName).setHeader("Event");
        eventGrid.addColumn(Event::getTrackId).setHeader("Track ID");
        eventGrid.addComponentColumn(event -> new RouterLink("Results", EventResultsView.class, event.getId())).setHeader("Results");

        driverGrid.addColumn(DriverStanding::getDriverName).setHeader("Driver");
        driverGrid.addColumn(DriverStanding::getPoints).setHeader("Points").setSortable(true);
        driverGrid.addColumn(DriverStanding::getWins).setHeader("Wins");

        teamGrid.addColumn(TeamStanding::getTeamName).setHeader("Team");
        teamGrid.addColumn(TeamStanding::getPoints).setHeader("Points").setSortable(true);
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        league = leagueRepository.findById(parameter).orElseThrow();
        seasonName.setText("Season: " + league.getName());
        updateData();
    }

    private void updateData() {
        eventGrid.setItems(eventRepository.findByLeague(league));
        driverGrid.setItems(league.getDriverStandings().stream()
                .sorted(Comparator.comparingInt(DriverStanding::getPoints).reversed()));
        teamGrid.setItems(league.getTeamStandings().stream()
                .sorted(Comparator.comparingInt(TeamStanding::getPoints).reversed()));
    }
}
