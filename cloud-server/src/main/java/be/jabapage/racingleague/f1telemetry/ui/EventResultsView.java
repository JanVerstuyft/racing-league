package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
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
        // Sort sessions by type chronologically: Practice -> Sprint Shootout -> Race
        Map<Integer, Integer> sortOrder = Map.ofEntries(
                Map.entry(1, 1), Map.entry(2, 2), Map.entry(3, 3), Map.entry(4, 4), // Practice
                Map.entry(5, 5), Map.entry(6, 6), Map.entry(7, 7), Map.entry(8, 8), Map.entry(9, 9), // Quali
                Map.entry(10, 10), Map.entry(11, 11), Map.entry(12, 12), Map.entry(13, 13), Map.entry(14, 14), // Sprint Shootout
                Map.entry(15, 15), Map.entry(16, 16), Map.entry(17, 17), // Race
                Map.entry(18, 18) // Time Trial
        );
        sessions.sort(Comparator.comparingInt(s -> sortOrder.getOrDefault(s.getSessionType(), 99)));

        for (SessionResult session : sessions) {
            String sessionName = TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(session.getSessionType(), "Session " + session.getSessionType());
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

            grid.addComponentColumn(dr -> {
                HorizontalLayout container = new HorizontalLayout();
                container.setSpacing(true);
                container.addClassName("stint-container");

                dr.getTyreStints().stream()
                        .sorted(Comparator.comparingInt(be.jabapage.racingleague.f1telemetry.entity.TyreStint::getStintOrder))
                        .forEach(stint -> {
                            Span badge = new Span();
                            badge.addClassName("tyre-badge");
                            String compoundName = TelemetryProcessingService.TYRE_COMPOUNDS.getOrDefault(stint.getTyreCompound(), "U");
                            badge.setText(compoundName.substring(0, 1));
                            
                            switch (compoundName) {
                                case "Soft" -> badge.addClassName("tyre-soft");
                                case "Medium" -> badge.addClassName("tyre-medium");
                                case "Hard" -> badge.addClassName("tyre-hard");
                                case "Inter" -> badge.addClassName("tyre-inter");
                                case "Wet" -> badge.addClassName("tyre-wet");
                                default -> badge.addClassName("tyre-unknown");
                            }

                            Span laps = new Span(stint.getLaps().toString());
                            laps.getStyle().set("font-size", "0.8em");
                            
                            HorizontalLayout stintInfo = new HorizontalLayout(badge, laps);
                            stintInfo.setSpacing(false);
                            stintInfo.setAlignItems(Alignment.CENTER);
                            container.add(stintInfo);
                        });
                return container;
            }).setHeader("Tyres").setAutoWidth(true);

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
