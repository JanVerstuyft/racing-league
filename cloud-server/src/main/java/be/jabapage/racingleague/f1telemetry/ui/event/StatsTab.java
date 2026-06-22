package be.jabapage.racingleague.f1telemetry.ui.event;

import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.model.ConsistencyStats;
import be.jabapage.racingleague.f1telemetry.model.LongestStintStats;
import be.jabapage.racingleague.f1telemetry.model.RacePaceStats;
import be.jabapage.racingleague.f1telemetry.repository.SessionResultRepository;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.ui.EventResultsView;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;

import java.util.*;
import java.util.stream.Collectors;

public class StatsTab extends VerticalLayout {

    private final TelemetryProcessingService telemetryProcessingService;
    private final SessionResultRepository sessionResultRepository;

    private Event currentEvent;

    private final Tabs statsSessionTabs = new Tabs();
    private final Tabs statsTabs = new Tabs();
    private final VerticalLayout statsContent = new VerticalLayout();

    public StatsTab(TelemetryProcessingService telemetryProcessingService,
                    SessionResultRepository sessionResultRepository) {
        this.telemetryProcessingService = telemetryProcessingService;
        this.sessionResultRepository = sessionResultRepository;

        setSizeFull();
        setPadding(false);

        statsSessionTabs.setWidthFull();
        statsSessionTabs.addSelectedChangeListener(event -> updateStatsContent());

        statsTabs.setWidthFull();
        Tab paceTab = new Tab("Pure Race Pace");
        Tab stintsTab = new Tab("Longest Stints");
        Tab consistencyTab = new Tab("Consistency");
        statsTabs.add(paceTab, stintsTab, consistencyTab);
        statsTabs.addSelectedChangeListener(event -> updateStatsContent());
        
        add(statsSessionTabs, statsTabs, statsContent);
    }

    public void update(Event event) {
        this.currentEvent = event;
        if (event == null) return;

        int currentStatsIdx = statsSessionTabs.getSelectedIndex();
        setupStatsSessionTabs();
        if (currentStatsIdx >= 0 && currentStatsIdx < statsSessionTabs.getComponentCount()) {
            statsSessionTabs.setSelectedIndex(currentStatsIdx);
        }
        updateStatsContent();
    }

    private List<SessionResult> getOrderedSessions() {
        if (currentEvent == null) return Collections.emptyList();
        List<SessionResult> sessions = new ArrayList<>(currentEvent.getSessionResults());
        Map<Integer, Integer> sortOrder = Map.ofEntries(
                Map.entry(1, 1), Map.entry(2, 2), Map.entry(3, 3), Map.entry(4, 4),
                Map.entry(5, 5), Map.entry(6, 6), Map.entry(7, 7), Map.entry(8, 8), Map.entry(9, 9),
                Map.entry(10, 10), Map.entry(11, 11), Map.entry(12, 12), Map.entry(13, 13), Map.entry(14, 14),
                Map.entry(15, 15), Map.entry(16, 16), Map.entry(17, 17),
                Map.entry(18, 18), Map.entry(19, 19)
        );
        sessions.sort(Comparator.comparingInt(s -> sortOrder.getOrDefault(s.getSessionType(), 99)));
        return sessions;
    }

    private List<SessionResult> getRaceSessions() {
        List<SessionResult> sessions = getOrderedSessions();
        return sessions.stream()
                .filter(s -> (s.getSessionType() >= 15 && s.getSessionType() <= 17) || s.getSessionType() == 19)
                .collect(Collectors.toList());
    }

    private void setupStatsSessionTabs() {
        statsSessionTabs.removeAll();
        List<SessionResult> raceSessions = getRaceSessions();
        if (raceSessions.size() <= 1) {
            statsSessionTabs.setVisible(false);
        } else {
            statsSessionTabs.setVisible(true);
        }
        java.util.Set<Integer> types = currentEvent.getSessionResults().stream()
                .map(SessionResult::getSessionType)
                .collect(Collectors.toSet());
        for (SessionResult session : raceSessions) {
            String name = EventResultsView.getDynamicSessionName(session.getSessionType(), types);
            Tab tab = new Tab(name);
            ComponentUtil.setData(tab, Long.class, session.getId());
            statsSessionTabs.add(tab);
        }
    }

    private void updateStatsContent() {
        statsContent.removeAll();
        if (currentEvent == null) return;
        
        List<SessionResult> raceSessions = getRaceSessions();
        if (raceSessions.isEmpty()) {
            statsContent.add(new Span("No race session data available."));
            return;
        }

        int selectedStatsSessionIdx = statsSessionTabs.getSelectedIndex();
        if (selectedStatsSessionIdx < 0 || selectedStatsSessionIdx >= raceSessions.size()) {
            selectedStatsSessionIdx = 0;
        }
        
        SessionResult selectedSession = raceSessions.get(selectedStatsSessionIdx);
        Long sessionResultId = selectedSession.getId();

        if (statsTabs.getSelectedIndex() == 0) { // Pure Race Pace
            updatePaceData(sessionResultId);
        } else if (statsTabs.getSelectedIndex() == 1) { // Longest Stints
            updateLongestStintsData(sessionResultId);
        } else if (statsTabs.getSelectedIndex() == 2) { // Consistency
            updateConsistencyData(sessionResultId);
        }
    }

    private void updateConsistencyData(Long sessionResultId) {
        List<ConsistencyStats> stats = telemetryProcessingService.calculateConsistency(sessionResultId);

        if (currentEvent.getTier().getLeague().isHideAi()) {
            stats = stats.stream().filter(s -> !s.isAi()).collect(Collectors.toList());
        }

        if (stats.isEmpty()) {
            statsContent.add(new Span("No consistency data available (need at least 3 valid laps)."));
            return;
        }

        Grid<ConsistencyStats> grid = new Grid<>(ConsistencyStats.class, false);
        grid.addComponentColumn(s -> {
            Span flagSpan = new Span(CountryProvider.getFlagByName(s.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            Span name = new Span(s.getDriverName());
            HorizontalLayout row = new HorizontalLayout(flagSpan, name);
            row.setAlignItems(Alignment.CENTER);
            row.setSpacing(false);
            return row;
        }).setHeader("Driver");
        grid.addColumn(ConsistencyStats::getTeamName).setHeader("Team");
        grid.addColumn(s -> String.format("%.1f", s.getRating())).setHeader("Rating");
        grid.addColumn(s -> String.format("%.3fs", s.getAvgDiff())).setHeader("Avg Diff");
        grid.addColumn(s -> String.format("%.1f", s.getS1Rating())).setHeader("S1");
        grid.addColumn(s -> String.format("%.1f", s.getS2Rating())).setHeader("S2");
        grid.addColumn(s -> String.format("%.1f", s.getS3Rating())).setHeader("S3");

        grid.setItems(stats);
        grid.setAllRowsVisible(true);
        statsContent.add(grid);

        // Consistency Poster
        List<SessionResult> raceSessions = getRaceSessions();
        int selectedStatsSessionIdx = statsSessionTabs.getSelectedIndex();
        if (selectedStatsSessionIdx < 0 || selectedStatsSessionIdx >= raceSessions.size()) {
            selectedStatsSessionIdx = 0;
        }
        SessionResult selectedSession = raceSessions.isEmpty() ? null : raceSessions.get(selectedStatsSessionIdx);

        if (selectedSession != null) {
            H2 posterHeader = new H2("Consistency Poster");
            posterHeader.getStyle().set("margin-top", "30px");

            Button downloadBtn = new Button("Download Consistency Image", com.vaadin.flow.component.icon.VaadinIcon.DOWNLOAD.create());
            downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            String eventNameSafe = currentEvent.getEventName().toLowerCase().replace(" ", "_");
            String sessNameSafe = EventResultsView.getDynamicSessionName(selectedSession.getSessionType(), getOrderedSessions().stream().map(SessionResult::getSessionType).toList()).toLowerCase().replace(" ", "_");
            downloadBtn.addClickListener(ev -> {
                getElement().executeJs(
                    "window.downloadInfographic('.consistency-poster', $0 + '_consistency.png', $1)",
                    eventNameSafe + "_" + sessNameSafe,
                    ev.getSource().getElement()
                );
            });

            String carType = getCarTypeForEvent();
            Div poster = PosterRenderer.createConsistencyPoster(currentEvent, selectedSession, stats, carType);
            statsContent.add(posterHeader, downloadBtn, poster);
        }
    }

    private void updateLongestStintsData(Long sessionResultId) {
        List<LongestStintStats> stats = telemetryProcessingService.calculateLongestStints(sessionResultId);

        if (currentEvent.getTier().getLeague().isHideAi()) {
            stats = stats.stream().filter(s -> !s.isAi()).collect(Collectors.toList());
        }

        if (stats.isEmpty()) {
            statsContent.add(new Span("No stint data available."));
            return;
        }

        Grid<LongestStintStats> grid = new Grid<>(LongestStintStats.class, false);
        grid.addComponentColumn(s -> {
            Span flagSpan = new Span(CountryProvider.getFlagByName(s.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            Span name = new Span(s.getDriverName());
            HorizontalLayout row = new HorizontalLayout(flagSpan, name);
            row.setAlignItems(Alignment.CENTER);
            row.setSpacing(false);
            return row;
        }).setHeader("Driver");
        grid.addColumn(LongestStintStats::getTeamName).setHeader("Team");
        grid.addColumn(LongestStintStats::getLaps).setHeader("Laps");
        grid.addComponentColumn(s -> {
            Span badge = new Span();
            badge.addClassName("tyre-badge");
            badge.setText(s.getTyreCompound().substring(0, 1));

            switch (s.getTyreCompound()) {
                case "Soft" -> badge.addClassName("tyre-soft");
                case "Medium" -> badge.addClassName("tyre-medium");
                case "Hard" -> badge.addClassName("tyre-hard");
                case "Inter" -> badge.addClassName("tyre-inter");
                case "Wet" -> badge.addClassName("tyre-wet");
                default -> badge.addClassName("tyre-unknown");
            }
            return badge;
        }).setHeader("Tyre");
        grid.addColumn(s -> EventResultsView.formatLapTime((float) s.getAvgLapTime())).setHeader("Avg Lap");
        grid.addColumn(s -> EventResultsView.formatLapTime((float) s.getAvgS1())).setHeader("Avg S1");
        grid.addColumn(s -> EventResultsView.formatLapTime((float) s.getAvgS2())).setHeader("Avg S2");
        grid.addColumn(s -> EventResultsView.formatLapTime((float) s.getAvgS3())).setHeader("Avg S3");

        grid.setItems(stats);
        grid.setAllRowsVisible(true);
        statsContent.add(grid);

        // Longest Stints Poster
        List<SessionResult> raceSessions = getRaceSessions();
        int selectedStatsSessionIdx = statsSessionTabs.getSelectedIndex();
        if (selectedStatsSessionIdx < 0 || selectedStatsSessionIdx >= raceSessions.size()) {
            selectedStatsSessionIdx = 0;
        }
        SessionResult selectedSession = raceSessions.isEmpty() ? null : raceSessions.get(selectedStatsSessionIdx);

        if (selectedSession != null) {
            H2 posterHeader = new H2("Tyre Stints Poster");
            posterHeader.getStyle().set("margin-top", "30px");

            Button downloadBtn = new Button("Download Stints Image", com.vaadin.flow.component.icon.VaadinIcon.DOWNLOAD.create());
            downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            String eventNameSafe = currentEvent.getEventName().toLowerCase().replace(" ", "_");
            String sessNameSafe = EventResultsView.getDynamicSessionName(selectedSession.getSessionType(), getOrderedSessions().stream().map(SessionResult::getSessionType).toList()).toLowerCase().replace(" ", "_");
            downloadBtn.addClickListener(ev -> {
                getElement().executeJs(
                    "window.downloadInfographic('.stints-poster', $0 + '_stints.png', $1)",
                    eventNameSafe + "_" + sessNameSafe,
                    ev.getSource().getElement()
                );
            });

            String carType = getCarTypeForEvent();
            Div poster = PosterRenderer.createStintsPoster(currentEvent, selectedSession, stats, carType);
            statsContent.add(posterHeader, downloadBtn, poster);
        }
    }

    private void updatePaceData(Long sessionResultId) {
        List<RacePaceStats> rawStats = telemetryProcessingService.calculatePureRacePace(sessionResultId);

        final List<RacePaceStats> stats;
        if (currentEvent.getTier().getLeague().isHideAi()) {
            stats = rawStats.stream().filter(s -> !s.isAi()).collect(Collectors.toList());
        } else {
            stats = rawStats;
        }

        if (stats.isEmpty()) {
            statsContent.add(new Span("No pace data available (only for Race sessions with drivers > 60% distance)."));
            return;
        }
        final double bestPace = stats.get(0).getPureRacePace();

        statsContent.add(new H2("PURE RACE PACE"));

        Grid<RacePaceStats> grid = new Grid<>(RacePaceStats.class, false);
        grid.addColumn(s -> stats.indexOf(s) + 1).setHeader("#").setWidth("50px").setFlexGrow(0);
        grid.addComponentColumn(s -> {
            Span flagSpan = new Span(CountryProvider.getFlagByName(s.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            Span name = new Span(s.getDriverName());
            HorizontalLayout row = new HorizontalLayout(flagSpan, name);
            row.setAlignItems(Alignment.CENTER);
            row.setSpacing(false);
            return row;
        }).setHeader("Driver").setAutoWidth(true);
        grid.addColumn(RacePaceStats::getTeamName).setHeader("Team").setAutoWidth(true);
        grid.addColumn(s -> {
            if (s.getPureRacePace() == bestPace) {
                return EventResultsView.formatLapTime((float) s.getPureRacePace());
            } else {
                return String.format("+%.3f", s.getPureRacePace() - bestPace);
            }
        }).setHeader("PURE TIME").setAutoWidth(true);

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
                badge.getStyle().set("width", "20px").set("height", "20px").set("font-size", "10px");

                Span text = new Span(String.format("%.0f%%", percent));
                text.getStyle().set("font-size", "0.8em");

                HorizontalLayout info = new HorizontalLayout(badge, text);
                info.setSpacing(false);
                info.setAlignItems(Alignment.CENTER);
                container.add(info);
            });
            return container;
        }).setHeader("Tyre Usage").setAutoWidth(true);

        grid.addComponentColumn(s -> createPerformanceBadge(s.getS1Performance())).setHeader("S1").setWidth("80px");
        grid.addComponentColumn(s -> createPerformanceBadge(s.getS2Performance())).setHeader("S2").setWidth("80px");
        grid.addComponentColumn(s -> createPerformanceBadge(s.getS3Performance())).setHeader("S3").setWidth("80px");

        grid.setItems(stats);
        grid.setAllRowsVisible(true);
        statsContent.add(grid);

        Html legend = new Html("""
            <div class="legend-text">
                <b>Pure laptime</b> - it's combined time of the best sectors in the race. Each sector (S1, S2, S3) is processing independently, 30% of the best race sectors of each driver are fully taken into account. The influence of the next 30% on the final result decreases linearly to 0. Only drivers who have driven at least 60% of the race distance are counted.<br/>
                <b>Tyre compound usage</b> - is the percentage of tyres that have shown sector times that were used in the pure laptime calculation.<br/>
                <b>Sector performance</b> - it is the efficiency of driving sectors for each driver relative to others. 10 - equals that only this driver showed all the best sectors. 5 - equals that driver show time corresponding the total average time for all drivers who participate in the rating.<br/>
                During processing the race distance is divided into 3 segments, and each segment is processed independently, the final result is the weighted average of all segments.
            </div>
            """);
        statsContent.add(legend);

        // Pace Poster
        List<SessionResult> raceSessions = getRaceSessions();
        int selectedStatsSessionIdx = statsSessionTabs.getSelectedIndex();
        if (selectedStatsSessionIdx < 0 || selectedStatsSessionIdx >= raceSessions.size()) {
            selectedStatsSessionIdx = 0;
        }
        SessionResult selectedSession = raceSessions.isEmpty() ? null : raceSessions.get(selectedStatsSessionIdx);

        if (selectedSession != null) {
            H2 posterHeader = new H2("Pure Pace Poster");
            posterHeader.getStyle().set("margin-top", "30px");

            Button downloadBtn = new Button("Download Pace Image", com.vaadin.flow.component.icon.VaadinIcon.DOWNLOAD.create());
            downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            String eventNameSafe = currentEvent.getEventName().toLowerCase().replace(" ", "_");
            String sessNameSafe = EventResultsView.getDynamicSessionName(selectedSession.getSessionType(), getOrderedSessions().stream().map(SessionResult::getSessionType).toList()).toLowerCase().replace(" ", "_");
            downloadBtn.addClickListener(ev -> {
                getElement().executeJs(
                    "window.downloadInfographic('.pace-poster', $0 + '_pace.png', $1)",
                    eventNameSafe + "_" + sessNameSafe,
                    ev.getSource().getElement()
                );
            });

            String carType = getCarTypeForEvent();
            Div poster = PosterRenderer.createPacePoster(currentEvent, selectedSession, stats, carType);
            statsContent.add(posterHeader, downloadBtn, poster);
        }
    }

    private Span createPerformanceBadge(double perf) {
        Span span = new Span(String.format("%.1f", perf).replace('.', ','));
        if (perf >= 9.0) {
            span.addClassName("perf-purple");
        } else if (perf >= 7.0) {
            span.addClassName("perf-green");
        } else if (perf >= 4.0) {
            span.addClassName("perf-yellow");
        } else {
            span.addClassName("perf-red");
        }
        return span;
    }

    private String getCarTypeForEvent() {
        if (currentEvent == null) return "F1 25";
        if (currentEvent.getTier() != null && currentEvent.getTier().getLeague() != null && currentEvent.getTier().getLeague().getCarType() != null) {
            return currentEvent.getTier().getLeague().getCarType();
        }
        String currentEventCarType = currentEvent.getSessionResults().stream()
                .map(SessionResult::getCarType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (currentEventCarType != null) {
            return currentEventCarType;
        }
        return sessionResultRepository.findByTier(currentEvent.getTier()).stream()
                .map(SessionResult::getCarType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("F1 25");
    }
}
