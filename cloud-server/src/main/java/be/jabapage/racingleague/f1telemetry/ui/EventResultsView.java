package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.entity.LapResult;
import be.jabapage.racingleague.f1telemetry.entity.EventLineupEntry;
import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.repository.EventLineupEntryRepository;
import be.jabapage.racingleague.f1telemetry.repository.DriverStandingRepository;
import java.util.HashMap;
import java.util.Objects;
import java.util.Collections;
import java.util.Optional;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.server.StreamResource;
import be.jabapage.racingleague.f1telemetry.entity.LeagueLogo;
import be.jabapage.racingleague.f1telemetry.repository.LeagueLogoRepository;
import java.io.ByteArrayInputStream;
import be.jabapage.racingleague.f1telemetry.model.RacePaceStats;
import be.jabapage.racingleague.f1telemetry.model.LongestStintStats;
import be.jabapage.racingleague.f1telemetry.model.ConsistencyStats;
import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.TeamMapping;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.DriverResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.repository.SessionResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamMappingRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
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
    private final SessionResultRepository sessionResultRepository;
    private final DriverResultRepository driverResultRepository;
    private final DriverMappingRepository driverMappingRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;
    private final TeamMappingRepository teamMappingRepository;
    private final EventLineupEntryRepository eventLineupEntryRepository;
    private final DriverStandingRepository driverStandingRepository;

    private final HorizontalLayout logoContainer = new HorizontalLayout();
    private final VerticalLayout lineupContainer = new VerticalLayout();
    private final Grid<EventLineupEntry> lineupGrid = new Grid<>(EventLineupEntry.class, false);
    private final Button addLineupBtn = new Button("Add Driver to Lineup");
    private final Button clearLineupBtn = new Button("Clear Lineup");
    private final Button updateRealLineupBtn = new Button("Update with Real Lineup");

    private final H2 eventHeader = new H2();
    private final RouterLink backToSeason = new RouterLink("Back to Season", SeasonDetailsView.class, 0L);
    
    private final VerticalLayout resultsContainer = new VerticalLayout();
    private final VerticalLayout statsContainer = new VerticalLayout();
    
    private final Tabs sessionTabs = new Tabs();
    private final VerticalLayout sessionContent = new VerticalLayout();
    
    private final Tabs statsSessionTabs = new Tabs();
    private final Tabs statsTabs = new Tabs();
    private final VerticalLayout statsContent = new VerticalLayout();
    
    private final Button addSessionBtn = new Button("Add Manual Session");
    private final Button addResultBtn = new Button("Add Result");
    
    private Long currentEventId;
    private Event currentEvent;
    private final LeagueLogoRepository leagueLogoRepository;

    public EventResultsView(EventRepository eventRepository,
                            SessionResultRepository sessionResultRepository,
                            DriverResultRepository driverResultRepository,
                            DriverMappingRepository driverMappingRepository,
                            TelemetryProcessingService telemetryProcessingService,
                            SecurityService securityService,
                            TeamMappingRepository teamMappingRepository,
                            LeagueLogoRepository leagueLogoRepository,
                            EventLineupEntryRepository eventLineupEntryRepository,
                            DriverStandingRepository driverStandingRepository) {
        this.eventRepository = eventRepository;
        this.sessionResultRepository = sessionResultRepository;
        this.driverResultRepository = driverResultRepository;
        this.driverMappingRepository = driverMappingRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;
        this.teamMappingRepository = teamMappingRepository;
        this.leagueLogoRepository = leagueLogoRepository;
        this.eventLineupEntryRepository = eventLineupEntryRepository;
        this.driverStandingRepository = driverStandingRepository;
        setSizeFull();

        // Main Tabs
        Tab resultsTab = new Tab("Results");
        Tab statsTab = new Tab("Stats");
        Tab lineupTab = new Tab("Lineup");
        Tabs mainTabs = new Tabs(resultsTab, statsTab, lineupTab);
        
        mainTabs.addSelectedChangeListener(event -> {
            boolean isResults = event.getSelectedTab().equals(resultsTab);
            boolean isStats = event.getSelectedTab().equals(statsTab);
            boolean isLineup = event.getSelectedTab().equals(lineupTab);
            
            resultsContainer.setVisible(isResults);
            statsContainer.setVisible(isStats);
            lineupContainer.setVisible(isLineup);
            
            if (isStats) {
                setupStatsSessionTabs();
                updateStatsContent();
            } else if (isLineup) {
                initLineupIfEmpty();
                updateLineupContent();
            }
        });

        // Results Section
        sessionTabs.setWidthFull();
        sessionTabs.addSelectedChangeListener(event -> updateSessionContent());
        
        HorizontalLayout sessionActions = new HorizontalLayout(addSessionBtn, addResultBtn);
        addResultBtn.setVisible(false);
        
        resultsContainer.add(sessionTabs, sessionActions, sessionContent);
        resultsContainer.setSizeFull();

        // Stats Section
        statsSessionTabs.setWidthFull();
        statsSessionTabs.addSelectedChangeListener(event -> updateStatsContent());

        statsTabs.setWidthFull();
        Tab paceTab = new Tab("Pure Race Pace");
        Tab stintsTab = new Tab("Longest Stints");
        Tab consistencyTab = new Tab("Consistency");
        statsTabs.add(paceTab, stintsTab, consistencyTab);
        statsTabs.addSelectedChangeListener(event -> updateStatsContent());
        statsContainer.add(statsSessionTabs, statsTabs, statsContent);
        statsContainer.setSizeFull();
        statsContainer.setVisible(false);

        HorizontalLayout nav = new HorizontalLayout(backToSeason);
        if (!securityService.getAuthenticatedUser().isPresent()) {
            nav.add(new RouterLink("Login", LoginView.class));
        }
        nav.add(new RouterLink("Documentation", DocumentationView.class));
        nav.setSpacing(true);

        logoContainer.setAlignItems(Alignment.CENTER);
        HorizontalLayout titleLayout = new HorizontalLayout(logoContainer, eventHeader);
        titleLayout.setAlignItems(Alignment.CENTER);
        titleLayout.setSpacing(true);

        lineupContainer.setSizeFull();
        lineupContainer.setVisible(false);
        add(nav, titleLayout, mainTabs, resultsContainer, statsContainer, lineupContainer);
        
        configureManualEntry();
        configureLineupGrid();
    }

    private void configureManualEntry() {
        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        addSessionBtn.setVisible(loggedIn);
        addResultBtn.setVisible(false);

        addSessionBtn.addClickListener(e -> {
            if (currentEvent == null) return;
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle("Add Manual Session");

            ComboBox<Integer> typeCombo = new ComboBox<>("Session Type");
            typeCombo.setItems(TelemetryProcessingService.SESSION_TYPE_NAMES.keySet().stream().filter(id -> id > 0).sorted().toList());
            typeCombo.setItemLabelGenerator(id -> TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(id, "Session " + id));
            typeCombo.setWidthFull();

            VerticalLayout layout = new VerticalLayout(typeCombo);
            dialog.add(layout);

            Button saveBtn = new Button("Add", ev -> {
                if (typeCombo.getValue() == null) return;
                SessionResult sr = new SessionResult();
                sr.setTier(currentEvent.getTier());
                sr.setEvent(currentEvent);
                sr.setTrackId(currentEvent.getTrackId());
                sr.setSessionType(typeCombo.getValue());
                sr.setSessionUID(System.currentTimeMillis());
                sessionResultRepository.save(sr);
                refreshEvent();
                dialog.close();
                Notification.show("Session added", 3000, Notification.Position.TOP_CENTER);
            });
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
            dialog.open();
        });

        addResultBtn.addClickListener(e -> {
            int selectedIndex = sessionTabs.getSelectedIndex();
            if (selectedIndex < 0) return;
            List<SessionResult> sessions = getOrderedSessions();
            SessionResult session = sessions.get(selectedIndex);

            Dialog dialog = new Dialog();
            java.util.Set<Integer> types = currentEvent.getSessionResults().stream().map(SessionResult::getSessionType).collect(Collectors.toSet());
            dialog.setHeaderTitle("Add Result to " + getDynamicSessionName(session.getSessionType(), types));

            ComboBox<DriverMapping> driverCombo = new ComboBox<>("Driver");
            driverCombo.setItems(driverMappingRepository.findByLeague(currentEvent.getTier().getLeague()));
            driverCombo.setItemLabelGenerator(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty() ? m.getOverriddenName() : m.getTelemetryName());
            driverCombo.setWidthFull();

            ComboBox<TeamMapping> teamCombo = new ComboBox<>("Team");
            String carType = session.getCarType() != null ? session.getCarType() : "F1 25";
            teamCombo.setItems(getTeamsForCarType(carType));
            teamCombo.setItemLabelGenerator(TeamMapping::getTeamName);
            teamCombo.setWidthFull();

            NumberField posField = new NumberField("Position");
            posField.setStepButtonsVisible(true);
            posField.setMin(1);
            posField.setMax(22);

            NumberField pointsField = new NumberField("Points");
            pointsField.setStepButtonsVisible(true);

            NumberField penaltiesField = new NumberField("Penalties (seconds)");
            penaltiesField.setStepButtonsVisible(true);
            penaltiesField.setMin(0);

            com.vaadin.flow.component.textfield.IntegerField warningsField = new com.vaadin.flow.component.textfield.IntegerField("Warnings");
            warningsField.setStepButtonsVisible(true);
            warningsField.setMin(0);

            com.vaadin.flow.component.textfield.IntegerField lapsField = new com.vaadin.flow.component.textfield.IntegerField("Laps Completed");
            lapsField.setStepButtonsVisible(true);
            lapsField.setMin(0);

            TextField timeField = new TextField("Best Lap Time (e.g. 1:24.500)");
            TextField totalTimeField = new TextField("Total Race Time (e.g. 45:12.300)");

            VerticalLayout layout = new VerticalLayout(driverCombo, teamCombo, 
                    new HorizontalLayout(posField, pointsField, penaltiesField, warningsField, lapsField), 
                    timeField, totalTimeField);
            dialog.add(layout);

            Button saveBtn = new Button("Add", ev -> {
                if (driverCombo.getValue() == null || teamCombo.getValue() == null || posField.getValue() == null) {
                    Notification.show("Please fill in Driver, Team and Position", 3000, Notification.Position.TOP_CENTER);
                    return;
                }
                DriverResult dr = new DriverResult();
                dr.setSessionResult(session);
                dr.setDriverName(driverCombo.getValue().getOverriddenName() != null ? driverCombo.getValue().getOverriddenName() : driverCombo.getValue().getTelemetryName());
                dr.setTelemetryName(driverCombo.getValue().getTelemetryName());
                dr.setRaceNumber(driverCombo.getValue().getRaceNumber());
                dr.setDriverId(driverCombo.getValue().getDriverId());
                dr.setCountry(driverCombo.getValue().getCountry());
                dr.setTeamId(teamCombo.getValue().getTeamId());
                dr.setPosition(posField.getValue().intValue());
                dr.setRawPosition(dr.getPosition());
                dr.setNumLaps(lapsField.getValue() != null ? lapsField.getValue() : 0);
                dr.setPointsAwarded(pointsField.getValue() != null ? pointsField.getValue().intValue() : 0);
                dr.setResultStatus(3);
                dr.setAi(false);
                dr.setPenalties(penaltiesField.getValue() != null ? penaltiesField.getValue().intValue() : 0);
                dr.setWarnings(warningsField.getValue() != null ? warningsField.getValue() : 0);
                
                if (timeField.getValue() != null && !timeField.getValue().isEmpty()) {
                    try {
                        dr.setBestLapTime(parseLapTime(timeField.getValue()));
                    } catch (Exception ex) {
                        Notification.show("Invalid best lap time format. Use m:ss.SSS or s.SSS", 5000, Notification.Position.TOP_CENTER);
                        return;
                    }
                }

                if (totalTimeField.getValue() != null && !totalTimeField.getValue().isEmpty()) {
                    try {
                        dr.setTotalTime((double) parseLapTime(totalTimeField.getValue()));
                        dr.setRawTotalTime(dr.getTotalTime());
                    } catch (Exception ex) {
                        Notification.show("Invalid total time format. Use m:ss.SSS or s.SSS", 5000, Notification.Position.TOP_CENTER);
                        return;
                    }
                }

                driverResultRepository.save(dr);
                
                // Recalculate gaps and standings for this league
                telemetryProcessingService.calculateGaps(session);
                telemetryProcessingService.recalculateStandings(currentEvent.getTier().getId());
                
                refreshEvent();
                dialog.close();
                Notification.show("Result added", 3000, Notification.Position.TOP_CENTER);
            });
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
            dialog.open();
        });
    }

    private float parseLapTime(String text) {
        if (text == null || text.isEmpty()) return 0;
        if (text.contains(":")) {
            String[] parts = text.split(":");
            if (parts.length == 3) { // HH:mm:ss.SSS
                int hours = Integer.parseInt(parts[0]);
                int mins = Integer.parseInt(parts[1]);
                float secs = Float.parseFloat(parts[2]);
                return hours * 3600 + mins * 60 + secs;
            } else if (parts.length == 2) { // mm:ss.SSS
                int mins = Integer.parseInt(parts[0]);
                float secs = Float.parseFloat(parts[1]);
                return mins * 60 + secs;
            }
        }
        return Float.parseFloat(text);
    }

    private void refreshEvent() {
        eventRepository.findByIdWithResults(currentEventId).ifPresent(e -> {
            this.currentEvent = e;
            int currentIdx = sessionTabs.getSelectedIndex();
            setupSessionTabs();
            if (currentIdx >= 0 && currentIdx < sessionTabs.getComponentCount()) {
                sessionTabs.setSelectedIndex(currentIdx);
            }
            int currentStatsIdx = statsSessionTabs.getSelectedIndex();
            setupStatsSessionTabs();
            if (currentStatsIdx >= 0 && currentStatsIdx < statsSessionTabs.getComponentCount()) {
                statsSessionTabs.setSelectedIndex(currentStatsIdx);
            }
            updateSessionContent();
        });
    }

    private List<SessionResult> getOrderedSessions() {
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
            String name = getDynamicSessionName(session.getSessionType(), types);
            Tab tab = new Tab(name);
            ComponentUtil.setData(tab, Long.class, session.getId());
            statsSessionTabs.add(tab);
        }
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        this.currentEventId = parameter;
        eventRepository.findByIdWithResults(parameter).ifPresentOrElse(e -> {
            this.currentEvent = e;
            eventHeader.setText("Event: " + currentEvent.getEventName());
            backToSeason.setRoute(SeasonDetailsView.class, currentEvent.getTier().getLeague().getId());
            updateLogo();
            setupSessionTabs();
            setupStatsSessionTabs();
            updateSessionContent();
        }, () -> {
            event.forwardTo(SeasonListView.class);
        });
    }

    private void setupSessionTabs() {
        sessionTabs.removeAll();
        List<SessionResult> sessions = getOrderedSessions();
        java.util.Set<Integer> types = currentEvent.getSessionResults().stream()
                .map(SessionResult::getSessionType)
                .collect(Collectors.toSet());
        for (SessionResult session : sessions) {
            String sessionName = getDynamicSessionName(session.getSessionType(), types);
            sessionTabs.add(new Tab(sessionName));
        }
    }

    private void updateSessionContent() {
        sessionContent.removeAll();
        int selectedIndex = sessionTabs.getSelectedIndex();
        
        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        addResultBtn.setVisible(loggedIn && selectedIndex >= 0);

        if (selectedIndex < 0) return;

        List<SessionResult> sessions = getOrderedSessions();
        SessionResult session = sessions.get(selectedIndex);
        boolean isQualifying = session.getSessionType() >= 5 && session.getSessionType() <= 14;
        
        List<DriverResult> driverResults = session.getDriverResults().stream()
                .sorted(Comparator.comparingInt(dr -> dr.getPosition() != null ? dr.getPosition() : 99))
                .collect(Collectors.toList());
        
        if (currentEvent.getTier().getLeague().isHideAi()) {
            driverResults = driverResults.stream().filter(dr -> !dr.isAi()).collect(Collectors.toList());
        }

        float fastestLap = driverResults.stream()
                .map(dr -> dr.getBestLapTime() != null ? dr.getBestLapTime() : 0.0f)
                .filter(t -> t > 0)
                .min(Float::compare)
                .orElse(0.0f);

        // Calculate session best sectors for highlighting
        long sessionBestS1 = driverResults.stream().flatMap(dr -> dr.getLapResults().stream()).filter(l -> l.getIsValid() != null && l.getIsValid()).mapToLong(l -> l.getS1InMS() != null ? l.getS1InMS() : Long.MAX_VALUE).min().orElse(Long.MAX_VALUE);
        long sessionBestS2 = driverResults.stream().flatMap(dr -> dr.getLapResults().stream()).filter(l -> l.getIsValid() != null && l.getIsValid()).mapToLong(l -> l.getS2InMS() != null ? l.getS2InMS() : Long.MAX_VALUE).min().orElse(Long.MAX_VALUE);
        long sessionBestS3 = driverResults.stream().flatMap(dr -> dr.getLapResults().stream()).filter(l -> l.getIsValid() != null && l.getIsValid()).mapToLong(l -> l.getS3InMS() != null ? l.getS3InMS() : Long.MAX_VALUE).min().orElse(Long.MAX_VALUE);

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
            Span flagSpan = new Span(CountryProvider.getFlagByName(dr.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            Span name = new Span(nameText);
            HorizontalLayout row = new HorizontalLayout(flagSpan, name);
            row.setAlignItems(Alignment.CENTER);
            row.setSpacing(false);
            if (dr.isAi()) {
                Span badge = new Span("AI");
                badge.getElement().getThemeList().add("badge contrast small");
                badge.getStyle().set("margin-left", "var(--lumo-space-s)");
                row.add(badge);
            }
            return row;
        }).setHeader("Driver");
        
        grid.addColumn(DriverResult::getTeamName).setHeader("Team");
        
        if (!isQualifying) {
            grid.addColumn(dr -> dr.getGridPosition() != null ? dr.getGridPosition() : "-").setHeader("Grid");
        }

        grid.addColumn(dr -> dr.getNumLaps() != null ? dr.getNumLaps() : "-").setHeader("Laps");

        grid.addColumn(dr -> formatLapTime(dr.getBestLapTime() != null ? dr.getBestLapTime() : 0.0f))
                .setHeader("Best Lap")
                .setPartNameGenerator(dr -> (dr.getBestLapTime() != null && fastestLap > 0 && dr.getBestLapTime() == fastestLap) ? "fastest-lap" : null);

        if (!isQualifying) {
            grid.addColumn(dr -> {
                if (dr.getPosition() != null && dr.getPosition() == 1) {
                    return formatLapTime(dr.getTotalTime() != null ? dr.getTotalTime().floatValue() : 0.0f);
                } else {
                    return dr.getGapToLeader() != null ? dr.getGapToLeader() : "-";
                }
            }).setHeader("Time");
        }

        if (isQualifying) {
            grid.addColumn(dr -> dr.getGapToLeader() != null ? dr.getGapToLeader() : "-").setHeader("Gap");

            grid.addColumn(dr -> {
                LapResult fastest = getFastestLap(dr);
                long s1 = (fastest != null && fastest.getS1InMS() != null) ? fastest.getS1InMS() : 0L;
                return formatLapTime(s1 / 1000.0f);
            }).setHeader("S1").setPartNameGenerator(dr -> {
                LapResult fastest = getFastestLap(dr);
                long s1 = (fastest != null && fastest.getS1InMS() != null) ? fastest.getS1InMS() : 0L;
                return (s1 > 0 && s1 == sessionBestS1) ? "best-sector" : null;
            });

            grid.addColumn(dr -> {
                LapResult fastest = getFastestLap(dr);
                long s2 = (fastest != null && fastest.getS2InMS() != null) ? fastest.getS2InMS() : 0L;
                return formatLapTime(s2 / 1000.0f);
            }).setHeader("S2").setPartNameGenerator(dr -> {
                LapResult fastest = getFastestLap(dr);
                long s2 = (fastest != null && fastest.getS2InMS() != null) ? fastest.getS2InMS() : 0L;
                return (s2 > 0 && s2 == sessionBestS2) ? "best-sector" : null;
            });

            grid.addColumn(dr -> {
                LapResult fastest = getFastestLap(dr);
                long s3 = (fastest != null && fastest.getS3InMS() != null) ? fastest.getS3InMS() : 0L;
                return formatLapTime(s3 / 1000.0f);
            }).setHeader("S3").setPartNameGenerator(dr -> {
                LapResult fastest = getFastestLap(dr);
                long s3 = (fastest != null && fastest.getS3InMS() != null) ? fastest.getS3InMS() : 0L;
                return (s3 > 0 && s3 == sessionBestS3) ? "best-sector" : null;
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
            grid.addColumn(dr -> dr.getPenalties() != null && dr.getPenalties() != 0 ? dr.getPenalties() + "s" : "-").setHeader("Game");
            grid.addColumn(dr -> dr.getStewardsPenalties() != null && dr.getStewardsPenalties() != 0 ? dr.getStewardsPenalties() + "s" : "-").setHeader("Stewards");
            grid.addColumn(dr -> {
                int gamePen = dr.getPenalties() != null ? dr.getPenalties() : 0;
                int stewardPen = dr.getStewardsPenalties() != null ? dr.getStewardsPenalties() : 0;
                int totalPen = gamePen + stewardPen;
                return totalPen != 0 ? totalPen + "s" : "-";
            }).setHeader("Penalties");
            grid.addColumn(dr -> dr.getWarnings() != null ? dr.getWarnings() : 0)
                    .setHeader("Warn")
                    .setPartNameGenerator(dr -> (dr.getWarnings() != null && dr.getWarnings() == 2) ? "warning-danger" : null);
        }
        
        if (loggedIn) {
            grid.addComponentColumn(dr -> {
                Button deleteBtn = new Button("Delete", e -> {
                    ConfirmDialog dialog = new ConfirmDialog();
                    dialog.setHeader("Delete Result?");
                    dialog.setText("Are you sure you want to delete this result for '" + dr.getDriverName() + "'?");
                    dialog.setCancelable(true);
                    dialog.setConfirmText("Delete");
                    dialog.setConfirmButtonTheme("error primary");
                    dialog.addConfirmListener(ev -> {
                    Notification deletingNote = new Notification("Deleting...");
                    deletingNote.setPosition(Notification.Position.TOP_CENTER);
                    deletingNote.setDuration(0);
                    deletingNote.open();
                    try {
                        driverResultRepository.delete(dr);
                        refreshEvent();
                        deletingNote.close();
                        Notification.show("Result deleted", 3000, Notification.Position.TOP_CENTER);
                    } catch (Exception ex) {
                        deletingNote.close();
                        Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                    }
                });
                    dialog.open();
                });
                deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
                return deleteBtn;
            }).setHeader("Actions");
        }

        grid.setItems(driverResults);
        grid.setAllRowsVisible(true);
        
        sessionContent.add(grid);

        if (loggedIn) {
            Button deleteSessionBtn = new Button("Delete This Session");
            deleteSessionBtn.addClickListener(e -> {
                ConfirmDialog dialog = new ConfirmDialog();
                dialog.setHeader("Delete Session?");
                dialog.setText("Are you sure you want to delete this session and all its results?");
                dialog.setCancelable(true);
                dialog.setConfirmText("Delete");
                dialog.setConfirmButtonTheme("error primary");
                dialog.addConfirmListener(ev -> {
                    Notification deletingNote = new Notification("Deleting session...");
                    deletingNote.setPosition(Notification.Position.TOP_CENTER);
                    deletingNote.setDuration(0);
                    deletingNote.open();
                    try {
                        sessionResultRepository.delete(session);
                        refreshEvent();
                        deletingNote.close();
                        Notification.show("Session deleted", 3000, Notification.Position.TOP_CENTER);
                    } catch (Exception ex) {
                        deletingNote.close();
                        Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                    }
                });
                dialog.open();
            });
            deleteSessionBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            sessionContent.add(deleteSessionBtn);
        }
    }

    private void updateStatsContent() {
        statsContent.removeAll();
        
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
        grid.addColumn(s -> formatLapTime((float) s.getAvgLapTime())).setHeader("Avg Lap");
        grid.addColumn(s -> formatLapTime((float) s.getAvgS1())).setHeader("Avg S1");
        grid.addColumn(s -> formatLapTime((float) s.getAvgS2())).setHeader("Avg S2");
        grid.addColumn(s -> formatLapTime((float) s.getAvgS3())).setHeader("Avg S3");

        grid.setItems(stats);
        grid.setAllRowsVisible(true);
        statsContent.add(grid);
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
                return formatLapTime((float) s.getPureRacePace());
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
    public static String getDynamicSessionName(int sessionType, java.util.Collection<Integer> sessionTypesInEvent) {
        if (sessionType == 15) {
            if (sessionTypesInEvent.contains(16)) {
                return "Sprint Race";
            }
            return "Race";
        }
        if (sessionType == 16) {
            if (sessionTypesInEvent.contains(15)) {
                return "Race";
            }
            return "Race 2";
        }
        return TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(sessionType, "Session " + sessionType);
    }

    private String formatLapTime(float seconds) {
        if (seconds <= 0) return "-";
        int minutes = (int) (seconds / 60);
        float remainingSeconds = seconds % 60;
        return String.format("%d:%06.3f", minutes, remainingSeconds);
    }

    private static LapResult getFastestLap(DriverResult dr) {
        if (dr.getLapResults() == null || dr.getLapResults().isEmpty()) {
            return null;
        }
        return dr.getLapResults().stream()
                .filter(l -> l.getIsValid() != null && l.getIsValid() && l.getLapTimeInMS() != null && l.getLapTimeInMS() > 0)
                .min(Comparator.comparingLong(LapResult::getLapTimeInMS))
                .orElse(null);
    }

    private void updateLogo() {
        logoContainer.removeAll();
        if (currentEvent != null && currentEvent.getTier() != null && currentEvent.getTier().getLeague() != null) {
            League league = currentEvent.getTier().getLeague();
            if (league.getHasLogo()) {
                StreamResource resource = new StreamResource("logo-" + league.getId() + "-" + System.currentTimeMillis() + ".png",
                        () -> {
                            byte[] logoBytes = leagueLogoRepository.findById(league.getId())
                                    .map(LeagueLogo::getLogo)
                                    .orElse(new byte[0]);
                            return new ByteArrayInputStream(logoBytes);
                        });
                Image logoImg = new Image(resource, "logo");
                logoImg.setHeight("40px");
                logoContainer.add(logoImg);
            }
            if (league.getLogoBackgroundColor() != null) {
                getUI().ifPresent(ui -> ui.getPage().executeJs(
                    "document.documentElement.style.setProperty('--lumo-base-color', $0); document.body.style.backgroundColor = $0;",
                    league.getLogoBackgroundColor()
                ));
            } else {
                getUI().ifPresent(ui -> ui.getPage().executeJs(
                    "document.documentElement.style.removeProperty('--lumo-base-color'); document.body.style.backgroundColor = '';"
                ));
            }
        }
    }

    @Override
    protected void onAttach(com.vaadin.flow.component.AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        updateLogo();
    }

    @Override
    protected void onDetach(com.vaadin.flow.component.DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        detachEvent.getUI().getPage().executeJs(
            "document.documentElement.style.removeProperty('--lumo-base-color'); document.body.style.backgroundColor = '';"
        );
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

    private void initLineupIfEmpty() {
        if (currentEvent == null) return;
        List<EventLineupEntry> existing = eventLineupEntryRepository.findByEvent(currentEvent);
        if (!existing.isEmpty()) return;

        List<DriverMapping> leagueDrivers = driverMappingRepository.findByLeague(currentEvent.getTier().getLeague());
        List<DriverMapping> regularDrivers = leagueDrivers.stream()
                .filter(d -> !d.isReserve())
                .filter(d -> java.util.Objects.equals(d.getTier(), currentEvent.getTier()))
                .toList();

        if (regularDrivers.isEmpty()) {
            // fallback: check all mappings for league in case tier assignment is empty
            regularDrivers = leagueDrivers.stream()
                .filter(d -> !d.isReserve())
                .toList();
        }

        Map<Integer, List<DriverMapping>> teamDrivers = new HashMap<>();
        String carType = getCarTypeForEvent();

        for (DriverMapping driver : regularDrivers) {
            Integer teamId = driver.getTeamId();
            if (teamId != null && teamId != -1) {
                teamDrivers.computeIfAbsent(teamId, k -> new ArrayList<>()).add(driver);
            }
        }

        List<String> warnedTeams = new ArrayList<>();
        for (Map.Entry<Integer, List<DriverMapping>> entry : teamDrivers.entrySet()) {
            Integer teamId = entry.getKey();
            List<DriverMapping> drivers = entry.getValue();
            String teamName = TelemetryProcessingService.getTeamNameStatic(teamId, carType);

            int limit = Math.min(drivers.size(), 2);
            for (int i = 0; i < limit; i++) {
                EventLineupEntry ele = new EventLineupEntry();
                ele.setEvent(currentEvent);
                ele.setDriver(drivers.get(i));
                ele.setTeamId(teamId);
                ele.setCarType(carType);
                eventLineupEntryRepository.save(ele);
            }

            if (drivers.size() > 2) {
                warnedTeams.add(teamName);
            }
        }

        if (!warnedTeams.isEmpty()) {
            String warningMsg = "Lineup pre-populated. Warning: The following teams have more than 2 regular drivers: "
                    + String.join(", ", warnedTeams) + ". Only the first 2 drivers were automatically assigned.";
            Notification notification = Notification.show(warningMsg, 8000, Notification.Position.MIDDLE);
            notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_WARNING);
        }
    }



    private void updateLineupContent() {
        lineupContainer.removeAll();
        if (currentEvent == null) return;

        List<EventLineupEntry> lineup = eventLineupEntryRepository.findByEvent(currentEvent);
        lineupGrid.setItems(lineup);

        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        addLineupBtn.setVisible(loggedIn);
        clearLineupBtn.setVisible(loggedIn);
        updateRealLineupBtn.setVisible(loggedIn);

        if (loggedIn) {
            HorizontalLayout toolbar = new HorizontalLayout(addLineupBtn, updateRealLineupBtn, clearLineupBtn);
            toolbar.setSpacing(true);
            lineupContainer.add(new H2("Lineup Manager"), toolbar, lineupGrid);
        }

        String carType = getCarTypeForEvent();
        Map<Integer, List<EventLineupEntry>> teamAssignments = lineup.stream()
                .collect(Collectors.groupingBy(EventLineupEntry::getTeamId));

        List<Integer> leftTeamIds;
        List<Integer> rightTeamIds;
        List<Integer> centerTeamIds = new ArrayList<>();

        if ("F1 26".equals(carType)) {
            leftTeamIds = List.of(484, 478, 480, 483, 479); // McLaren, Red Bull, Aston, Haas, Williams
            rightTeamIds = List.of(477, 476, 481, 482, 485); // Ferrari, Mercedes, Alpine, RB, Audi
            centerTeamIds = List.of(486); // Cadillac
        } else {
            leftTeamIds = List.of(8, 2, 4, 7, 3); // McLaren, Red Bull, Aston, Haas, Williams
            rightTeamIds = List.of(1, 0, 5, 6, 9); // Ferrari, Mercedes, Alpine, RB, Sauber
        }

        League league = currentEvent.getTier().getLeague();

        Div posterWrapper = new Div();
        posterWrapper.addClassName("lineup-poster-wrapper");

        Div poster = new Div();
        poster.addClassName("lineup-poster");

        if (league.getLogoBackgroundColor() != null && !league.getLogoBackgroundColor().isEmpty()) {
            poster.getStyle().set("background", "linear-gradient(135deg, " + league.getLogoBackgroundColor() + " 0%, #090a0f 100%)");
        }

        // Set accent color variable and render text ribbons
        String accentColor = league.getAccentColor() != null && !league.getAccentColor().isEmpty()
                ? league.getAccentColor()
                : "#eef30d";
        poster.getStyle().set("--lineup-accent-color", accentColor);

        Div topLeftRibbon = new Div(new Span(league.getName()));
        topLeftRibbon.addClassName("lineup-ribbon");
        topLeftRibbon.addClassName("lineup-ribbon-top-left");

        Div bottomRightRibbon = new Div(new Span(league.getName()));
        bottomRightRibbon.addClassName("lineup-ribbon");
        bottomRightRibbon.addClassName("lineup-ribbon-bottom-right");

        poster.add(topLeftRibbon, bottomRightRibbon);

        Div header = new Div();
        header.addClassName("lineup-poster-header");
        H4 subtitle = new H4(currentEvent.getTier().getName().toUpperCase());
        subtitle.addClassName("lineup-poster-title-mini");
        H1 title = new H1(currentEvent.getEventName().toUpperCase() + " DRIVER LINE-UP");
        title.addClassName("lineup-poster-title-main");
        header.add(subtitle, title);
        poster.add(header);

        Div grid = new Div();
        grid.addClassName("lineup-poster-grid");

        Div leftCol = new Div();
        leftCol.addClassName("lineup-column");
        for (Integer teamId : leftTeamIds) {
            leftCol.add(createTeamCard(teamId, carType, teamAssignments));
        }
        grid.add(leftCol);

        Div centerCol = new Div();
        centerCol.addClassName("lineup-center-column");

        Div logoContainer = new Div();
        logoContainer.addClassName("lineup-trophy-container");
        
        byte[] logoBytes = leagueLogoRepository.findById(league.getId())
                .map(LeagueLogo::getLogo)
                .orElse(null);
                
        if (logoBytes != null && logoBytes.length > 0) {
            String base64Logo = java.util.Base64.getEncoder().encodeToString(logoBytes);
            String dataUrl = "data:image/png;base64," + base64Logo;
            Image leagueLogoImg = new Image(dataUrl, "League Logo");
            leagueLogoImg.getStyle().set("max-height", "110px");
            leagueLogoImg.getStyle().set("max-width", "180px");
            leagueLogoImg.getStyle().set("object-fit", "contain");
            logoContainer.add(leagueLogoImg);
        } else {
            Span trophyIcon = new Span("🏆");
            trophyIcon.addClassName("lineup-trophy-icon");
            logoContainer.add(trophyIcon);
        }
        centerCol.add(logoContainer);

        if (!centerTeamIds.isEmpty()) {
            centerCol.add(createTeamCard(centerTeamIds.get(0), carType, teamAssignments));
        }

        grid.add(centerCol);

        Div rightCol = new Div();
        rightCol.addClassName("lineup-column");
        for (Integer teamId : rightTeamIds) {
            rightCol.add(createTeamCard(teamId, carType, teamAssignments));
        }
        grid.add(rightCol);

        poster.add(grid);

        Div footer = new Div();
        footer.addClassName("lineup-poster-footer");
        if (league.getYoutubeHandle() != null && !league.getYoutubeHandle().isEmpty()) {
            footer.add(createSocialItem("youtube", league.getYoutubeHandle()));
        }
        if (league.getTiktokHandle() != null && !league.getTiktokHandle().isEmpty()) {
            footer.add(createSocialItem("tiktok", league.getTiktokHandle()));
        }
        if (league.getXHandle() != null && !league.getXHandle().isEmpty()) {
            footer.add(createSocialItem("x", league.getXHandle()));
        }
        if (league.getInstagramHandle() != null && !league.getInstagramHandle().isEmpty()) {
            footer.add(createSocialItem("instagram", league.getInstagramHandle()));
        }
        if (league.getTwitchHandle() != null && !league.getTwitchHandle().isEmpty()) {
            footer.add(createSocialItem("twitch", league.getTwitchHandle()));
        }
        poster.add(footer);

        posterWrapper.add(poster);

        Button downloadBtn = new Button("Download Lineup Image");
        downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        downloadBtn.getStyle().set("margin-bottom", "15px");
        downloadBtn.addClickListener(e -> {
            getElement().executeJs(
                "if (!window.html2canvas) {" +
                "  const script = document.createElement('script');" +
                "  script.src = 'https://unpkg.com/html2canvas@1.4.1/dist/html2canvas.min.js';" +
                "  script.onload = () => { downloadLineupPoster(); };" +
                "  document.head.appendChild(script);" +
                "} else {" +
                "  downloadLineupPoster();" +
                "}" +
                "function downloadLineupPoster() {" +
                "  const el = document.querySelector('.lineup-poster');" +
                "  if (el) {" +
                "    html2canvas(el, { useCORS: true, backgroundColor: null }).then(canvas => {" +
                "      const link = document.createElement('a');" +
                "      link.download = 'lineup_' + $0 + '.png';" +
                "      link.href = canvas.toDataURL('image/png');" +
                "      link.click();" +
                "    });" +
                "  }" +
                "}",
                currentEvent.getEventName().toLowerCase().replace(" ", "_")
            );
        });

        lineupContainer.add(new H2("Lineup Poster"), downloadBtn, posterWrapper);
    }

    private Div createTeamCard(Integer teamId, String carType, Map<Integer, List<EventLineupEntry>> teamAssignments) {
        Div card = new Div();
        card.addClassName("lineup-team-card");
        card.getStyle().set("--team-color", getTeamColor(teamId, carType));

        Div header = new Div();
        header.addClassName("lineup-team-header");
        Span name = new Span(TelemetryProcessingService.getTeamNameStatic(teamId, carType));
        name.addClassName("lineup-team-name");
        Span symbol = new Span(getTeamSymbol(teamId, carType));
        symbol.addClassName("lineup-team-symbol");
        header.add(name, symbol);
        card.add(header);

        Div row = new Div();
        row.addClassName("lineup-drivers-row");

        List<EventLineupEntry> entries = teamAssignments.getOrDefault(teamId, Collections.emptyList());
        for (int i = 0; i < 2; i++) {
            Div slot = new Div();
            slot.addClassName("lineup-driver-slot");
            Span driverName = new Span();
            driverName.addClassName("lineup-driver-name");

            if (i < entries.size()) {
                DriverMapping dm = entries.get(i).getDriver();
                String dispName = dm.getOverriddenName() != null && !dm.getOverriddenName().isEmpty() 
                    ? dm.getOverriddenName() 
                    : dm.getTelemetryName();
                driverName.setText(dispName);
                slot.addClassName("assigned");
            } else {
                driverName.setText("VACANT");
                slot.addClassName("vacant");
            }
            slot.add(driverName);
            row.add(slot);
        }
        card.add(row);
        return card;
    }

    private String getTeamColor(Integer teamId, String carType) {
        if ("F1 26".equals(carType)) {
            return switch (teamId) {
                case 220, 476 -> "#00a19b";
                case 221, 477 -> "#ef1a2d";
                case 222, 478 -> "#0600ef";
                case 223, 479 -> "#005aff";
                case 224, 480 -> "#00594f";
                case 225, 481 -> "#0090ff";
                case 226, 482 -> "#1e41ff";
                case 227, 483 -> "#e60000";
                case 228, 484 -> "#ff8700";
                case 229, 485 -> "#c4002d";
                case 230, 486 -> "#fdb913";
                default -> "#888888";
            };
        } else {
            return switch (teamId) {
                case 0 -> "#00a19b";
                case 1 -> "#ef1a2d";
                case 2 -> "#0600ef";
                case 3 -> "#005aff";
                case 4 -> "#00594f";
                case 5 -> "#0090ff";
                case 6 -> "#1e41ff";
                case 7 -> "#e60000";
                case 8 -> "#ff8700";
                case 9 -> "#52e252";
                default -> "#888888";
            };
        }
    }

    private String getTeamSymbol(Integer teamId, String carType) {
        if ("F1 26".equals(carType)) {
            return switch (teamId) {
                case 220, 476 -> "✦";
                case 221, 477 -> "🐎";
                case 222, 478 -> "🐂";
                case 223, 479 -> "W";
                case 224, 480 -> "▲";
                case 225, 481 -> "A";
                case 226, 482 -> "RB";
                case 227, 483 -> "H";
                case 228, 484 -> "M";
                case 229, 485 -> "四";
                case 230, 486 -> "★";
                default -> "T";
            };
        } else {
            return switch (teamId) {
                case 0 -> "✦";
                case 1 -> "🐎";
                case 2 -> "🐂";
                case 3 -> "W";
                case 4 -> "▲";
                case 5 -> "A";
                case 6 -> "RB";
                case 7 -> "H";
                case 8 -> "M";
                case 9 -> "K";
                default -> "T";
            };
        }
    }

    private void showAddLineupDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Driver to Lineup");

        ComboBox<DriverMapping> driverCombo = new ComboBox<>("Driver");
        driverCombo.setItems(driverMappingRepository.findByLeague(currentEvent.getTier().getLeague()));
        driverCombo.setItemLabelGenerator(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty() 
            ? m.getOverriddenName() 
            : m.getTelemetryName());
        driverCombo.setWidthFull();

        Button createNewDriverBtn = new Button("New Driver");
        createNewDriverBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        createNewDriverBtn.addClickListener(e -> showCreateManualDriverDialog(driverCombo));

        HorizontalLayout driverLayout = new HorizontalLayout(driverCombo, createNewDriverBtn);
        driverLayout.setAlignItems(Alignment.END);
        driverLayout.setWidthFull();

        ComboBox<TeamMapping> teamCombo = new ComboBox<>("Team");
        teamCombo.setWidthFull();

        String carType = getCarTypeForEvent();
        List<TeamMapping> allTeams = getTeamsForCarType(carType);

        Span note = new Span();
        note.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        driverCombo.addValueChangeListener(e -> {
            DriverMapping dm = e.getValue();
            if (dm == null) {
                teamCombo.clear();
                teamCombo.setReadOnly(false);
                note.setText("");
                return;
            }

            if (!dm.isReserve()) {
                Integer teamId = dm.getTeamId();
                if (teamId != null) {
                    Optional<TeamMapping> tmOpt = teamMappingRepository.findByTeamIdAndCarType(teamId, carType);
                    if (tmOpt.isPresent()) {
                        String teamName = tmOpt.get().getTeamName();
                        TeamMapping uiTeam = allTeams.stream()
                                .filter(t -> Objects.equals(t.getTeamName(), teamName))
                                .findFirst()
                                .orElse(null);
                        teamCombo.setItems(allTeams);
                        teamCombo.setValue(uiTeam);
                        teamCombo.setReadOnly(true);
                        note.setText("Regular driver auto-assigned to their primary team.");
                        return;
                    }
                }
                teamCombo.setReadOnly(false);
                teamCombo.setItems(allTeams);
                note.setText("Regular driver (no primary team assigned) - please assign a team.");
            } else {
                teamCombo.setReadOnly(false);
                List<EventLineupEntry> currentLineup = eventLineupEntryRepository.findByEvent(currentEvent);
                Map<Integer, Long> teamCounts = currentLineup.stream()
                        .collect(Collectors.groupingBy(EventLineupEntry::getTeamId, Collectors.counting()));
                List<TeamMapping> remainingTeams = allTeams.stream()
                        .filter(tm -> teamCounts.getOrDefault(tm.getTeamId(), 0L) < 2)
                        .toList();
                
                teamCombo.setItems(remainingTeams);
                note.setText("Reserve driver - select from remaining teams with open seats.");
            }
        });

        teamCombo.setItemLabelGenerator(TeamMapping::getTeamName);

        VerticalLayout dialogLayout = new VerticalLayout(driverLayout, teamCombo, note);
        dialog.add(dialogLayout);

        Button saveBtn = new Button("Save", ev -> {
            if (driverCombo.getValue() == null || teamCombo.getValue() == null) {
                Notification.show("Please select both Driver and Team", 3000, Notification.Position.TOP_CENTER);
                return;
            }

            DriverMapping driver = driverCombo.getValue();
            TeamMapping team = teamCombo.getValue();

            EventLineupEntry ele = new EventLineupEntry();
            ele.setEvent(currentEvent);
            ele.setDriver(driver);
            ele.setTeamId(team.getTeamId());
            ele.setCarType(carType);

            try {
                eventLineupEntryRepository.save(ele);
                updateLineupContent();
                dialog.close();
                Notification.show("Driver added to lineup", 3000, Notification.Position.TOP_CENTER);
            } catch (Exception ex) {
                Notification.show("Error: Driver might already be assigned.", 5000, Notification.Position.TOP_CENTER);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
        dialog.open();
    }

    private void showCreateManualDriverDialog(ComboBox<DriverMapping> parentCombo) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create New Driver");

        TextField nameField = new TextField("Display Name");
        nameField.setWidthFull();

        TextField telemetryNameField = new TextField("Telemetry Name (Optional)");
        telemetryNameField.setWidthFull();

        com.vaadin.flow.component.textfield.IntegerField raceNumField = new com.vaadin.flow.component.textfield.IntegerField("Race #");
        raceNumField.setWidthFull();

        ComboBox<String> countryCombo = new ComboBox<>("Country");
        countryCombo.setItems(CountryProvider.getCountryNames());
        countryCombo.setValue("Unknown");
        countryCombo.setWidthFull();

        VerticalLayout dialogLayout = new VerticalLayout(nameField, telemetryNameField, raceNumField, countryCombo);
        dialog.add(dialogLayout);

        Button saveBtn = new Button("Create", ev -> {
            if (nameField.getValue().isEmpty()) {
                Notification.show("Please enter a display name", 3000, Notification.Position.TOP_CENTER);
                return;
            }

            DriverMapping dm = new DriverMapping();
            dm.setLeague(currentEvent.getTier().getLeague());
            dm.setOverriddenName(nameField.getValue());
            dm.setTelemetryName(telemetryNameField.getValue().isEmpty() ? nameField.getValue() : telemetryNameField.getValue());
            dm.setRaceNumber(raceNumField.getValue() != null ? raceNumField.getValue() : 0);
            dm.setCountry(countryCombo.getValue() != null ? countryCombo.getValue() : "Unknown");
            dm.setDriverId(255);
            dm.setTier(currentEvent.getTier());

            driverMappingRepository.save(dm);
            
            parentCombo.setItems(driverMappingRepository.findByLeague(currentEvent.getTier().getLeague()));
            parentCombo.setValue(dm);

            dialog.close();
            Notification.show("Driver created successfully", 3000, Notification.Position.TOP_CENTER);
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
        dialog.open();
    }

    private void configureLineupGrid() {
        addLineupBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addLineupBtn.addClickListener(e -> showAddLineupDialog());

        clearLineupBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        clearLineupBtn.addClickListener(e -> {
            if (currentEvent == null) return;
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Clear Lineup?");
            dialog.setText("Are you sure you want to clear the entire lineup for this event?");
            dialog.setCancelable(true);
            dialog.setConfirmText("Clear");
            dialog.setConfirmButtonTheme("error primary");
            dialog.addConfirmListener(ev -> {
                eventLineupEntryRepository.deleteAll(eventLineupEntryRepository.findByEvent(currentEvent));
                updateLineupContent();
                Notification.show("Lineup cleared", 3000, Notification.Position.TOP_CENTER);
            });
            dialog.open();
        });

        updateRealLineupBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        updateRealLineupBtn.addClickListener(e -> {
            if (currentEvent == null) return;
            showUpdateRealLineupConfirmDialog();
        });

        lineupGrid.addComponentColumn(entry -> {
            DriverMapping dm = entry.getDriver();
            String nameText = dm.getOverriddenName() != null && !dm.getOverriddenName().isEmpty() 
                ? dm.getOverriddenName() 
                : dm.getTelemetryName();
            Span flagSpan = new Span(CountryProvider.getFlagByName(dm.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            Span name = new Span(nameText);
            HorizontalLayout row = new HorizontalLayout(flagSpan, name);
            row.setAlignItems(Alignment.CENTER);
            row.setSpacing(false);
            return row;
        }).setHeader("Driver").setSortable(true);

        lineupGrid.addColumn(entry -> {
            return TelemetryProcessingService.getTeamNameStatic(entry.getTeamId(), entry.getCarType());
        }).setHeader("Team").setSortable(true);

        lineupGrid.addColumn(entry -> {
            return entry.getDriver().isReserve() ? "Reserve Driver" : "Regular Driver";
        }).setHeader("Status");

        if (securityService.getAuthenticatedUser().isPresent()) {
            lineupGrid.addComponentColumn(entry -> {
                Button deleteBtn = new Button("Remove", e -> {
                    ConfirmDialog dialog = new ConfirmDialog();
                    dialog.setHeader("Remove from Lineup?");
                    dialog.setText("Are you sure you want to remove '" 
                        + entry.getDriver().getOverriddenName() + "' from this weekend's lineup?");
                    dialog.setCancelable(true);
                    dialog.setConfirmText("Remove");
                    dialog.setConfirmButtonTheme("error primary");
                    dialog.addConfirmListener(ev -> {
                        eventLineupEntryRepository.delete(entry);
                        updateLineupContent();
                        Notification.show("Driver removed from lineup", 3000, Notification.Position.TOP_CENTER);
                    });
                    dialog.open();
                });
                deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
                return deleteBtn;
            }).setHeader("Actions");
        }
        lineupGrid.setAllRowsVisible(true);
    }

    private Div createSocialItem(String platform, String handle) {
        Div item = new Div();
        item.addClassName("lineup-social-item");

        String svgStr = switch (platform) {
            case "youtube" -> "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"width:16px; height:16px; display:inline-block; vertical-align:middle;\">" +
                    "<path d=\"M23.498 6.163a3.003 3.003 0 0 0-2.11-2.108C19.53 3.5 12 3.5 12 3.5s-7.53 0-9.388.555A3.003 3.003 0 0 0 .502 6.163C0 8.07 0 12 0 12s0 3.93.502 5.837a3.003 3.003 0 0 0 2.11 2.108C4.47 20.5 12 20.5 12 20.5s7.53 0 9.388-.555a3.003 3.003 0 0 0 2.11-2.108C24 15.93 24 12 24 12s0-3.93-.502-5.837zM9.545 15.568V8.432L15.818 12l-6.273 3.568z\"/></svg>";
            case "tiktok" -> "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"width:16px; height:16px; display:inline-block; vertical-align:middle;\">" +
                    "<path d=\"M12.53.086c.3-.01.597.026.887.106.198.055.39.155.556.294.137.116.24.27.3.44.027.08.04.16.046.244.032 1.637.7 3.12 1.83 4.2 1.05.998 2.45 1.59 3.98 1.66.27.014.54.004.805-.03v3.25c-1.12.016-2.22-.276-3.19-.844-.82-.48-1.5-1.16-1.98-1.98v6.78c.005.89-.164 1.77-.5 2.6-.58 1.41-1.63 2.59-2.98 3.32a8.88 8.88 0 0 1-5.18.91c-1.5-.12-2.93-.72-4.1-1.7a9.14 9.14 0 0 1-2.8-5.38 8.89 8.89 0 0 1 .91-5.18c.73-1.35 1.91-2.4 3.32-2.98 1.13-.47 2.35-.61 3.56-.41v3.29c-.6-.07-1.22.01-1.78.24-.7.29-1.28.82-1.64 1.5-.56.98-.56 2.2 0 3.18.36.68.94 1.21 1.64 1.5.82.34 1.74.34 2.56 0 .7-.29 1.28-.82 1.64-1.5.23-.56.31-1.18.24-1.78V0l3.29.086z\"/></svg>";
            case "x" -> "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"width:16px; height:16px; display:inline-block; vertical-align:middle;\">" +
                    "<path d=\"M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z\"/></svg>";
            case "instagram" -> "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" style=\"width:16px; height:16px; display:inline-block; vertical-align:middle;\">" +
                    "<rect x=\"2\" y=\"2\" width=\"20\" height=\"20\" rx=\"5\" ry=\"5\"></rect>" +
                    "<path d=\"M16 11.37A4 4 0 1 1 12.63 8 4 4 0 0 1 16 11.37z\"></path>" +
                    "<line x1=\"17.5\" y1=\"6.5\" x2=\"17.51\" y2=\"6.5\"></line></svg>";
            case "twitch" -> "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"width:16px; height:16px; display:inline-block; vertical-align:middle;\">" +
                    "<path d=\"M11.571 4.714h1.715v5.143H11.57zm4.715 0H18v5.143h-1.714zM6 0L1.714 4.286v15.428h5.143V24l4.286-4.286h3.428L22.286 12V0zm14.571 11.143l-3.428 3.428h-3.429l-3 3v-3H6.857V1.714h13.714Z\"/></svg>";
            default -> "";
        };

        if (!svgStr.isEmpty()) {
            com.vaadin.flow.component.Html iconHtml = new com.vaadin.flow.component.Html(svgStr);
            Span iconSpan = new Span(iconHtml);
            iconSpan.addClassName("lineup-social-icon");
            item.add(iconSpan);
        }

        Span textSpan = new Span(handle);
        textSpan.addClassName("lineup-social-text");

        item.add(textSpan);
        return item;
    }

    private void showUpdateRealLineupConfirmDialog() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Update with Real Lineup?");
        dialog.setText("Are you sure you want to clear the current lineup and update it with the actual drivers who participated in this weekend's session results?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Update");
        dialog.setConfirmButtonTheme("primary");
        dialog.addConfirmListener(ev -> {
            List<SessionResult> sessions = sessionResultRepository.findByTier(currentEvent.getTier()).stream()
                    .filter(sr -> sr.getEvent() != null && Objects.equals(sr.getEvent().getId(), currentEvent.getId()))
                    .toList();
            if (sessions.isEmpty()) {
                Notification.show("No telemetry session results found for this weekend to update from.", 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_WARNING);
                return;
            }
            List<DriverMapping> mappings = driverMappingRepository.findByLeague(currentEvent.getTier().getLeague());
            Map<DriverMapping, Integer> driverToTeamMap = new java.util.LinkedHashMap<>();
            for (SessionResult sr : sessions) {
                for (DriverResult dr : sr.getDriverResults()) {
                    if (dr.isAi()) {
                        continue;
                    }
                    DriverMapping mapping = findMappingForDriverResult(dr, mappings);
                    if (mapping != null && dr.getTeamId() != null && dr.getTeamId() != -1) {
                        driverToTeamMap.put(mapping, dr.getTeamId());
                    }
                }
            }
            if (driverToTeamMap.isEmpty()) {
                Notification.show("No matching driver mappings found in telemetry session results.", 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_WARNING);
                return;
            }
            // Clear existing
            eventLineupEntryRepository.deleteAll(eventLineupEntryRepository.findByEvent(currentEvent));
            
            // Save new
            String carType = getCarTypeForEvent();
            for (Map.Entry<DriverMapping, Integer> entry : driverToTeamMap.entrySet()) {
                EventLineupEntry ele = new EventLineupEntry();
                ele.setEvent(currentEvent);
                ele.setDriver(entry.getKey());
                ele.setTeamId(entry.getValue());
                ele.setCarType(carType);
                eventLineupEntryRepository.save(ele);
            }
            
            updateLineupContent();
            Notification.show("Lineup updated with real participants", 3000, Notification.Position.TOP_CENTER);
        });
        dialog.open();
    }

    private DriverMapping findMappingForDriverResult(DriverResult result, List<DriverMapping> mappings) {
        if (result.getTelemetryName() == null || result.getRaceNumber() == null || result.getDriverId() == null) {
            return null;
        }
        for (DriverMapping m : mappings) {
            if (Objects.equals(m.getTelemetryName(), result.getTelemetryName())
                    && Objects.equals(m.getRaceNumber(), result.getRaceNumber())
                    && Objects.equals(m.getDriverId(), result.getDriverId())
                    && Objects.equals(m.getCountry(), result.getCountry())) {
                return m;
            }
        }
        return null;
    }

    private List<TeamMapping> getTeamsForCarType(String carType) {
        List<TeamMapping> teams = teamMappingRepository.findByCarType(carType);
        if ("F1 26".equals(carType)) {
            teams = teams.stream().filter(t -> t.getTeamId() != null && t.getTeamId() >= 400).toList();
        }
        return teams;
    }
}
