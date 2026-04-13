package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@PageTitle("Event Results | F1 Telemetry")
@Route(value = "event", layout = MainLayout.class)
public class EventResultsView extends VerticalLayout implements HasUrlParameter<Long> {

    private final EventRepository eventRepository;
    private final H2 eventHeader = new H2();
    private final VerticalLayout resultsLayout = new VerticalLayout();

    // Map session type to human-readable name
    private static final Map<Integer, String> SESSION_TYPE_NAMES = Map.of(
            5, "Q1", 6, "Q2", 7, "Q3", 8, "Short Qualifying", 9, "One-Shot Qualifying",
            10, "Race", 11, "Short Race", 12, "Medium Race"
    );

    public EventResultsView(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
        setSizeFull();
        add(eventHeader, resultsLayout);
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        Event e = eventRepository.findById(parameter).orElseThrow();
        eventHeader.setText("Event: " + e.getEventName());
        resultsLayout.removeAll();

        List<SessionResult> sessions = e.getSessionResults();
        // Sort sessions by type (Qualifying then Race)
        sessions.sort(Comparator.comparingInt(SessionResult::getSessionType));

        for (SessionResult session : sessions) {
            String sessionName = SESSION_TYPE_NAMES.getOrDefault(session.getSessionType(), "Session " + session.getSessionType());
            resultsLayout.add(new H3(sessionName));
            
            Grid<DriverResult> grid = new Grid<>(DriverResult.class, false);
            grid.addColumn(DriverResult::getPosition).setHeader("Pos").setWidth("60px").setFlexGrow(0);
            grid.addColumn(DriverResult::getDriverName).setHeader("Driver");
            grid.addColumn(DriverResult::getTeamName).setHeader("Team");
            grid.addColumn(DriverResult::getGridPosition).setHeader("Grid");
            grid.addColumn(DriverResult::getBestLapTime).setHeader("Best Lap");
            grid.addColumn(DriverResult::getPointsAwarded).setHeader("Points");
            
            grid.setItems(session.getDriverResults().stream()
                    .sorted(Comparator.comparingInt(DriverResult::getPosition))
                    .collect(Collectors.toList()));
            grid.setAllRowsVisible(true);
            
            resultsLayout.add(grid);
        }
    }
}
