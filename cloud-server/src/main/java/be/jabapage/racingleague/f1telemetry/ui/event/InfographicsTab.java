package be.jabapage.racingleague.f1telemetry.ui.event;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.model.ConsistencyStats;
import be.jabapage.racingleague.f1telemetry.model.LongestStintStats;
import be.jabapage.racingleague.f1telemetry.model.RacePaceStats;
import be.jabapage.racingleague.f1telemetry.repository.SessionResultRepository;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.ui.EventResultsView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;

import java.util.*;
import java.util.stream.Collectors;

public class InfographicsTab extends VerticalLayout {

    private final TelemetryProcessingService telemetryProcessingService;
    private final SessionResultRepository sessionResultRepository;

    private Event currentEvent;

    private final Tabs infographicsSessionTabs = new Tabs();
    private final VerticalLayout infographicsContent = new VerticalLayout();

    public InfographicsTab(TelemetryProcessingService telemetryProcessingService,
                           SessionResultRepository sessionResultRepository) {
        this.telemetryProcessingService = telemetryProcessingService;
        this.sessionResultRepository = sessionResultRepository;

        setSizeFull();
        setPadding(false);

        infographicsSessionTabs.setWidthFull();
        infographicsSessionTabs.addSelectedChangeListener(event -> updateInfographicsContent());
        add(infographicsSessionTabs, infographicsContent);
    }

    public void update(Event event) {
        this.currentEvent = event;
        if (event == null) return;

        int currentInfoIdx = infographicsSessionTabs.getSelectedIndex();
        setupInfographicsSessionTabs();
        if (currentInfoIdx >= 0 && currentInfoIdx < infographicsSessionTabs.getComponentCount()) {
            infographicsSessionTabs.setSelectedIndex(currentInfoIdx);
        }
        updateInfographicsContent();
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

    private void setupInfographicsSessionTabs() {
        infographicsSessionTabs.removeAll();
        List<SessionResult> sessions = getOrderedSessions();
        if (sessions.size() <= 1) {
            infographicsSessionTabs.setVisible(false);
        } else {
            infographicsSessionTabs.setVisible(true);
        }
        java.util.Set<Integer> types = currentEvent.getSessionResults().stream()
                .map(SessionResult::getSessionType)
                .collect(Collectors.toSet());
        for (SessionResult session : sessions) {
            String sessionName = EventResultsView.getDynamicSessionName(session.getSessionType(), types);
            infographicsSessionTabs.add(new Tab(sessionName));
        }
    }

    private void updateInfographicsContent() {
        infographicsContent.removeAll();
        if (currentEvent == null) return;
        
        int selectedIndex = infographicsSessionTabs.getSelectedIndex();
        if (selectedIndex < 0) return;

        List<SessionResult> sessions = getOrderedSessions();
        SessionResult session = sessions.get(selectedIndex);
        boolean isRaceOrSprint = (session.getSessionType() >= 15 && session.getSessionType() <= 17) || session.getSessionType() == 19;
        
        List<DriverResult> driverResults = session.getDriverResults().stream()
                .sorted(Comparator.comparingInt(dr -> dr.getPosition() != null ? dr.getPosition() : 99))
                .collect(Collectors.toList());

        if (driverResults.isEmpty()) {
            infographicsContent.add(new Span("No results/data available for this session."));
            return;
        }

        String eventNameSafe = currentEvent.getEventName().toLowerCase().replace(" ", "_");
        String sessNameSafe = EventResultsView.getDynamicSessionName(session.getSessionType(), sessions.stream().map(SessionResult::getSessionType).toList()).toLowerCase().replace(" ", "_");
        String carType = getCarTypeForEvent();

        // 1. Results Poster
        H2 resultsHeader = new H2("Results Poster");
        resultsHeader.getStyle().set("margin-top", "30px");
        Button downloadResultsBtn = new Button("Download Results Image", com.vaadin.flow.component.icon.VaadinIcon.DOWNLOAD.create());
        downloadResultsBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        downloadResultsBtn.getStyle().set("margin-bottom", "15px");
        downloadResultsBtn.addClickListener(e -> {
            getElement().executeJs(
                "window.downloadInfographic('.results-poster', 'results_' + $0 + '.png', $1)",
                eventNameSafe + "_" + sessNameSafe,
                e.getSource().getElement()
            );
        });
        Div resultsPoster = PosterRenderer.createResultsPoster(currentEvent, session, driverResults, carType);
        infographicsContent.add(resultsHeader, downloadResultsBtn, resultsPoster);

        // For race/sprint sessions, add other posters
        if (isRaceOrSprint) {
            // 2. Pit Stops Poster
            H2 pitstopsHeader = new H2("Pit Stops Poster");
            pitstopsHeader.getStyle().set("margin-top", "40px");
            Button downloadPitStopsBtn = new Button("Download Pit Stops Image", com.vaadin.flow.component.icon.VaadinIcon.DOWNLOAD.create());
            downloadPitStopsBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            downloadPitStopsBtn.getStyle().set("margin-bottom", "15px");
            downloadPitStopsBtn.addClickListener(e -> {
                getElement().executeJs(
                    "window.downloadInfographic('.pitstops-poster', 'pitstops_' + $0 + '.png', $1)",
                    eventNameSafe + "_" + sessNameSafe,
                    e.getSource().getElement()
                );
            });
            Div pitstopsPoster = PosterRenderer.createPitStopsPoster(currentEvent, session, driverResults, carType);
            infographicsContent.add(pitstopsHeader, downloadPitStopsBtn, pitstopsPoster);

            // 3. Pure Pace Poster
            List<RacePaceStats> rawPaceStats = telemetryProcessingService.calculatePureRacePace(session.getId());
            List<RacePaceStats> paceStats = currentEvent.getTier().getLeague().isHideAi()
                    ? rawPaceStats.stream().filter(s -> !s.isAi()).collect(Collectors.toList())
                    : rawPaceStats;
            if (!paceStats.isEmpty()) {
                H2 paceHeader = new H2("Pure Pace Poster");
                paceHeader.getStyle().set("margin-top", "40px");
                Button downloadPaceBtn = new Button("Download Pace Image", com.vaadin.flow.component.icon.VaadinIcon.DOWNLOAD.create());
                downloadPaceBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
                downloadPaceBtn.getStyle().set("margin-bottom", "15px");
                downloadPaceBtn.addClickListener(e -> {
                    getElement().executeJs(
                        "window.downloadInfographic('.pace-poster', 'pace_' + $0 + '.png', $1)",
                        eventNameSafe + "_" + sessNameSafe,
                        e.getSource().getElement()
                    );
                });
                Div pacePoster = PosterRenderer.createPacePoster(currentEvent, session, paceStats, carType);
                infographicsContent.add(paceHeader, downloadPaceBtn, pacePoster);
            }

            // 4. Tyre Stints Poster (Longest Stints)
            List<LongestStintStats> rawStintStats = telemetryProcessingService.calculateLongestStints(session.getId());
            List<LongestStintStats> stintStats = currentEvent.getTier().getLeague().isHideAi()
                    ? rawStintStats.stream().filter(s -> !s.isAi()).collect(Collectors.toList())
                    : rawStintStats;
            if (!stintStats.isEmpty()) {
                H2 stintsHeader = new H2("Tyre Stints Poster");
                stintsHeader.getStyle().set("margin-top", "40px");
                Button downloadStintsBtn = new Button("Download Stints Image", com.vaadin.flow.component.icon.VaadinIcon.DOWNLOAD.create());
                downloadStintsBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
                downloadStintsBtn.getStyle().set("margin-bottom", "15px");
                downloadStintsBtn.addClickListener(e -> {
                    getElement().executeJs(
                        "window.downloadInfographic('.stints-poster', 'stints_' + $0 + '.png', $1)",
                        eventNameSafe + "_" + sessNameSafe,
                        e.getSource().getElement()
                    );
                });
                Div stintsPoster = PosterRenderer.createStintsPoster(currentEvent, session, stintStats, carType);
                infographicsContent.add(stintsHeader, downloadStintsBtn, stintsPoster);
            }

            // 5. Consistency Poster
            List<ConsistencyStats> rawConsistencyStats = telemetryProcessingService.calculateConsistency(session.getId());
            List<ConsistencyStats> consistencyStats = currentEvent.getTier().getLeague().isHideAi()
                    ? rawConsistencyStats.stream().filter(s -> !s.isAi()).collect(Collectors.toList())
                    : rawConsistencyStats;
            if (!consistencyStats.isEmpty()) {
                H2 consistencyHeader = new H2("Consistency Poster");
                consistencyHeader.getStyle().set("margin-top", "40px");
                Button downloadConsistencyBtn = new Button("Download Consistency Image", com.vaadin.flow.component.icon.VaadinIcon.DOWNLOAD.create());
                downloadConsistencyBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
                downloadConsistencyBtn.getStyle().set("margin-bottom", "15px");
                downloadConsistencyBtn.addClickListener(e -> {
                    getElement().executeJs(
                        "window.downloadInfographic('.consistency-poster', 'consistency_' + $0 + '.png', $1)",
                        eventNameSafe + "_" + sessNameSafe,
                        e.getSource().getElement()
                    );
                });
                Div consistencyPoster = PosterRenderer.createConsistencyPoster(currentEvent, session, consistencyStats, carType);
                infographicsContent.add(consistencyHeader, downloadConsistencyBtn, consistencyPoster);
            }
        }
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
