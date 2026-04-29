package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.model.RacePaceStats;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AnonymousAllowed
@PageTitle("Event Results | F1 Telemetry")
@Route(value = "event")
public class EventResultsView extends VerticalLayout implements HasUrlParameter<Long> {

    private final EventRepository eventRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final H2 eventHeader = new H2();
    private final RouterLink backToSeason = new RouterLink("Back to Season", SeasonDetailsView.class, 0L);
    
    private final VerticalLayout resultsContainer = new VerticalLayout();
    private final VerticalLayout statsContainer = new VerticalLayout();
    
    private final Tabs sessionTabs = new Tabs();
    private final VerticalLayout sessionContent = new VerticalLayout();
    
    private final Tabs statsTabs = new Tabs();
    private final VerticalLayout statsContent = new VerticalLayout();
    
    private Long currentEventId;
    private Event currentEvent;

    public EventResultsView(EventRepository eventRepository, TelemetryProcessingService telemetryProcessingService) {
        this.eventRepository = eventRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        setSizeFull();

        // Main Tabs
        Tab resultsTab = new Tab("Results");
        Tab statsTab = new Tab("Stats");
        Tabs mainTabs = new Tabs(resultsTab, statsTab);
        
        mainTabs.addSelectedChangeListener(event -> {
            boolean isResults = event.getSelectedTab().equals(resultsTab);
            resultsContainer.setVisible(isResults);
            statsContainer.setVisible(!isResults);
            if (!isResults) {
                updateStatsContent();
            }
        });

        // Results Section
        sessionTabs.setWidthFull();
        sessionTabs.addSelectedChangeListener(event -> updateSessionContent());
        resultsContainer.add(sessionTabs, sessionContent);
        resultsContainer.setSizeFull();

        // Stats Section
        statsTabs.setWidthFull();
        Tab paceTab = new Tab("Pure Race Pace");
        statsTabs.add(paceTab);
        statsTabs.addSelectedChangeListener(event -> updateStatsContent());
        statsContainer.add(statsTabs, statsContent);
        statsContainer.setSizeFull();
        statsContainer.setVisible(false);

        add(backToSeason, eventHeader, mainTabs, resultsContainer, statsContainer);
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        this.currentEventId = parameter;
        this.currentEvent = eventRepository.findByIdWithResults(parameter).orElseThrow();
        eventHeader.setText("Event: " + currentEvent.getEventName());
        
        backToSeason.setRoute(SeasonDetailsView.class, currentEvent.getLeague().getId());
        
        setupSessionTabs();
        updateSessionContent();
    }

    private void setupSessionTabs() {
        sessionTabs.removeAll();
        List<SessionResult> sessions = new ArrayList<>(currentEvent.getSessionResults());
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
            Tab tab = new Tab(sessionName);
            // Store session ID or reference? We can use the list index
            sessionTabs.add(tab);
        }
    }

    private void updateSessionContent() {
        sessionContent.removeAll();
        int selectedIndex = sessionTabs.getSelectedIndex();
        if (selectedIndex < 0) return;

        List<SessionResult> sessions = new ArrayList<>(currentEvent.getSessionResults());
        Map<Integer, Integer> sortOrder = Map.ofEntries(
                Map.entry(1, 1), Map.entry(2, 2), Map.entry(3, 3), Map.entry(4, 4), // Practice
                Map.entry(5, 5), Map.entry(6, 6), Map.entry(7, 7), Map.entry(8, 8), Map.entry(9, 9), // Quali
                Map.entry(10, 10), Map.entry(11, 11), Map.entry(12, 12), Map.entry(13, 13), Map.entry(14, 14), // Sprint Shootout
                Map.entry(15, 15), Map.entry(16, 16), Map.entry(17, 17), // Race
                Map.entry(18, 18) // Time Trial
        );
        sessions.sort(Comparator.comparingInt(s -> sortOrder.getOrDefault(s.getSessionType(), 99)));

        SessionResult session = sessions.get(selectedIndex);
        boolean isQualifying = session.getSessionType() >= 5 && session.getSessionType() <= 14;
        
        List<DriverResult> driverResults = session.getDriverResults().stream()
                .sorted(Comparator.comparingInt(DriverResult::getPosition))
                .collect(Collectors.toList());
        
        if (currentEvent.getLeague().isHideAi()) {
            driverResults = driverResults.stream().filter(dr -> !dr.isAi()).collect(Collectors.toList());
        }

        float fastestLap = driverResults.stream()
                .map(dr -> dr.getBestLapTime() != null ? dr.getBestLapTime() : 0.0f)
                .filter(t -> t > 0)
                .min(Float::compare)
                .orElse(0.0f);

        // Calculate session best sectors for highlighting
        long sessionBestS1 = driverResults.stream().flatMap(dr -> dr.getLapResults().stream()).mapToLong(l -> l.getS1InMS() != null ? l.getS1InMS() : Long.MAX_VALUE).min().orElse(Long.MAX_VALUE);
        long sessionBestS2 = driverResults.stream().flatMap(dr -> dr.getLapResults().stream()).mapToLong(l -> l.getS2InMS() != null ? l.getS2InMS() : Long.MAX_VALUE).min().orElse(Long.MAX_VALUE);
        long sessionBestS3 = driverResults.stream().flatMap(dr -> dr.getLapResults().stream()).mapToLong(l -> l.getS3InMS() != null ? l.getS3InMS() : Long.MAX_VALUE).min().orElse(Long.MAX_VALUE);

        Grid<DriverResult> grid = new Grid<>(DriverResult.class, false);
        grid.addColumn(dr -> dr.getPosition() != null ? dr.getPosition() : "-").setHeader("Pos").setWidth("60px").setFlexGrow(0);
        
        grid.addComponentColumn(dr -> {
            String nameText = dr.getDriverName();
            Integer status = dr.getResultStatus();
            if (status != null) {
                if (status == 4) nameText += " (DNF)";
                else if (status == 5) nameText += " (DSQ)";
                else if (status == 6) nameText += " (NC)";
                else if (status == 7) nameText += " (RET)";
            }
            Span name = new Span(nameText);
            if (dr.isAi()) {
                Span badge = new Span("AI");
                badge.getElement().getThemeList().add("badge contrast small");
                badge.getStyle().set("margin-left", "var(--lumo-space-s)");
                return new HorizontalLayout(name, badge);
            }
            return name;
        }).setHeader("Driver");
        
        grid.addColumn(DriverResult::getTeamName).setHeader("Team");
        
        if (!isQualifying) {
            grid.addColumn(dr -> dr.getGridPosition() != null ? dr.getGridPosition() : "-").setHeader("Grid");
        }

        grid.addColumn(dr -> formatLapTime(dr.getBestLapTime() != null ? dr.getBestLapTime() : 0.0f))
                .setHeader("Best Lap")
                .setPartNameGenerator(dr -> (dr.getBestLapTime() != null && fastestLap > 0 && dr.getBestLapTime() == fastestLap) ? "fastest-lap" : null);

        if (isQualifying) {
            grid.addColumn(dr -> {
                if (dr.getBestLapTime() == null || dr.getBestLapTime() == 0 || fastestLap == 0) return "-";
                float gap = dr.getBestLapTime() - fastestLap;
                return gap <= 0 ? "-" : String.format("+%.3fs", gap);
            }).setHeader("Gap");

            grid.addColumn(dr -> {
                long bestS1 = dr.getLapResults().stream().mapToLong(l -> l.getS1InMS() != null ? l.getS1InMS() : Long.MAX_VALUE).min().orElse(0);
                return formatLapTime(bestS1 / 1000.0f);
            }).setHeader("S1").setPartNameGenerator(dr -> {
                long bestS1 = dr.getLapResults().stream().mapToLong(l -> l.getS1InMS() != null ? l.getS1InMS() : Long.MAX_VALUE).min().orElse(0);
                return (bestS1 > 0 && bestS1 == sessionBestS1) ? "best-sector" : null;
            });

            grid.addColumn(dr -> {
                long bestS2 = dr.getLapResults().stream().mapToLong(l -> l.getS2InMS() != null ? l.getS2InMS() : Long.MAX_VALUE).min().orElse(0);
                return formatLapTime(bestS2 / 1000.0f);
            }).setHeader("S2").setPartNameGenerator(dr -> {
                long bestS2 = dr.getLapResults().stream().mapToLong(l -> l.getS2InMS() != null ? l.getS2InMS() : Long.MAX_VALUE).min().orElse(0);
                return (bestS2 > 0 && bestS2 == sessionBestS2) ? "best-sector" : null;
            });

            grid.addColumn(dr -> {
                long bestS3 = dr.getLapResults().stream().mapToLong(l -> l.getS3InMS() != null ? l.getS3InMS() : Long.MAX_VALUE).min().orElse(0);
                return formatLapTime(bestS3 / 1000.0f);
            }).setHeader("S3").setPartNameGenerator(dr -> {
                long bestS3 = dr.getLapResults().stream().mapToLong(l -> l.getS3InMS() != null ? l.getS3InMS() : Long.MAX_VALUE).min().orElse(0);
                return (bestS3 > 0 && bestS3 == sessionBestS3) ? "best-sector" : null;
            });
        }

        if (!isQualifying) {
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
            grid.addColumn(dr -> dr.getPenalties() != null && dr.getPenalties() > 0 ? dr.getPenalties() + "s" : "-").setHeader("Pen");
        }
        
        grid.setItems(driverResults);
        grid.setAllRowsVisible(true);
        
        sessionContent.add(grid);
    }

    private void updateStatsContent() {
        statsContent.removeAll();
        if (statsTabs.getSelectedIndex() == 0) { // Pure Race Pace
            updatePaceData();
        }
    }

    private void updatePaceData() {
        List<RacePaceStats> stats = telemetryProcessingService.calculatePureRacePace(currentEventId);
        
        if (currentEvent.getLeague().isHideAi()) {
            stats = stats.stream().filter(s -> !s.isAi()).collect(Collectors.toList());
        }
        
        if (stats.isEmpty()) {
            statsContent.add(new Span("No pace data available (only for Race sessions with drivers > 50% distance)."));
            return;
        }

        Grid<RacePaceStats> grid = new Grid<>(RacePaceStats.class, false);
        grid.addColumn(RacePaceStats::getDriverName).setHeader("Driver");
        grid.addColumn(RacePaceStats::getTeamName).setHeader("Team");
        grid.addColumn(s -> formatLapTime((float) s.getPureRacePace())).setHeader("Pure Pace");
        grid.addColumn(s -> String.format("%.2f", s.getSectorPerformance())).setHeader("S.Perf");
        grid.addColumn(s -> formatLapTime((float) s.getS1Pace())).setHeader("S1");
        grid.addColumn(s -> formatLapTime((float) s.getS2Pace())).setHeader("S2");
        grid.addColumn(s -> formatLapTime((float) s.getS3Pace())).setHeader("S3");
        
        grid.addComponentColumn(s -> {
            HorizontalLayout container = new HorizontalLayout();
            s.getTyreUsage().forEach((compound, percent) -> {
                Span badge = new Span();
                badge.addClassName("tyre-badge");
                badge.setText(compound.substring(0, 1));
                
                switch (compound) {
                    case "Soft" -> badge.addClassName("tyre-soft");
                    case "Medium" -> badge.addClassName("tyre-medium");
                    case "Hard" -> badge.addClassName("tyre-hard");
                    case "Inter" -> badge.addClassName("tyre-inter");
                    case "Wet" -> badge.addClassName("tyre-wet");
                    default -> badge.addClassName("tyre-unknown");
                }
                
                Span text = new Span(String.format("%.0f%%", percent));
                text.getStyle().set("font-size", "0.8em");
                
                HorizontalLayout info = new HorizontalLayout(badge, text);
                info.setSpacing(false);
                info.setAlignItems(Alignment.CENTER);
                container.add(info);
            });
            return container;
        }).setHeader("Tyre Usage");

        grid.setItems(stats);
        grid.setAllRowsVisible(true);
        statsContent.add(grid);
    }

    private String formatLapTime(float seconds) {
        if (seconds <= 0) return "-";
        int minutes = (int) (seconds / 60);
        float remainingSeconds = seconds % 60;
        return String.format("%d:%06.3f", minutes, remainingSeconds);
    }
}
