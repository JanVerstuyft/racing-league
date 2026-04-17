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

import java.util.ArrayList;
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
        Event e = eventRepository.findByIdWithResults(parameter).orElseThrow();
        eventHeader.setText("Event: " + e.getEventName());
        resultsLayout.removeAll();

        List<SessionResult> sessions = new ArrayList<>(e.getSessionResults());
        // Sort sessions by type (Qualifying then Race)
        sessions.sort(Comparator.comparingInt(SessionResult::getSessionType));

        for (SessionResult session : sessions) {
            String sessionName = SESSION_TYPE_NAMES.getOrDefault(session.getSessionType(), "Session " + session.getSessionType());
            resultsLayout.add(new H3(sessionName));
            
            List<DriverResult> driverResults = session.getDriverResults().stream()
                    .sorted(Comparator.comparingInt(DriverResult::getPosition))
                    .collect(Collectors.toList());

            float fastestLap = driverResults.stream()
                    .map(dr -> dr.getBestLapTime() != null ? dr.getBestLapTime() : 0.0f)
                    .filter(t -> t > 0)
                    .min(Float::compare)
                    .orElse(0.0f);

            Grid<DriverResult> grid = new Grid<>(DriverResult.class, false);
            grid.addColumn(dr -> dr.getPosition() != null ? dr.getPosition() : "-").setHeader("Pos").setWidth("60px").setFlexGrow(0);
            grid.addColumn(dr -> {
                String name = dr.getDriverName();
                Integer status = dr.getResultStatus();
                if (status != null) {
                    if (status == 4) name += " (DNF)";
                    else if (status == 5) name += " (DSQ)";
                    else if (status == 6) name += " (NC)";
                    else if (status == 7) name += " (RET)";
                }
                return name;
            }).setHeader("Driver");
            grid.addColumn(DriverResult::getTeamName).setHeader("Team");
            grid.addColumn(dr -> dr.getGridPosition() != null ? dr.getGridPosition() : "-").setHeader("Grid");
            grid.addColumn(dr -> {
                Float lapTime = dr.getBestLapTime();
                return formatLapTime(lapTime != null ? lapTime : 0.0f);
            })
                    .setHeader("Best Lap")
                    .setPartNameGenerator(dr -> {
                        Float lapTime = dr.getBestLapTime();
                        if (lapTime != null && fastestLap > 0 && lapTime == fastestLap) {
                            return "fastest-lap";
                        }
                        return null;
                    });
            grid.addColumn(DriverResult::getPointsAwarded).setHeader("Points");
            
            grid.setItems(driverResults);
            grid.setAllRowsVisible(true);
            
            resultsLayout.add(grid);
        }
    }

    private String formatLapTime(float seconds) {
        if (seconds <= 0) return "-";
        int minutes = (int) (seconds / 60);
        float remainingSeconds = seconds % 60;
        return String.format("%d:%06.3f", minutes, remainingSeconds);
    }
}
