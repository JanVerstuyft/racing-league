package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.model.RacePaceStats;
import be.jabapage.racingleague.f1telemetry.model.LongestStintStats;
import be.jabapage.racingleague.f1telemetry.model.ConsistencyStats;
import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.DriverResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.repository.SessionResultRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
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

    private final H2 eventHeader = new H2();
    private final RouterLink backToSeason = new RouterLink("Back to Season", SeasonDetailsView.class, 0L);
    
    private final VerticalLayout resultsContainer = new VerticalLayout();
    private final VerticalLayout statsContainer = new VerticalLayout();
    
    private final Tabs sessionTabs = new Tabs();
    private final VerticalLayout sessionContent = new VerticalLayout();
    
    private final Tabs statsTabs = new Tabs();
    private final VerticalLayout statsContent = new VerticalLayout();
    
    private final Button addSessionBtn = new Button("Add Manual Session");
    private final Button addResultBtn = new Button("Add Result");
    
    private Long currentEventId;
    private Event currentEvent;

    public EventResultsView(EventRepository eventRepository,
                            SessionResultRepository sessionResultRepository,
                            DriverResultRepository driverResultRepository,
                            DriverMappingRepository driverMappingRepository,
                            TelemetryProcessingService telemetryProcessingService,
                            SecurityService securityService) {
        this.eventRepository = eventRepository;
        this.sessionResultRepository = sessionResultRepository;
        this.driverResultRepository = driverResultRepository;
        this.driverMappingRepository = driverMappingRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;
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
        
        HorizontalLayout sessionActions = new HorizontalLayout(addSessionBtn, addResultBtn);
        addResultBtn.setVisible(false);
        
        resultsContainer.add(sessionTabs, sessionActions, sessionContent);
        resultsContainer.setSizeFull();

        // Stats Section
        statsTabs.setWidthFull();
        Tab paceTab = new Tab("Pure Race Pace");
        Tab stintsTab = new Tab("Longest Stints");
        Tab consistencyTab = new Tab("Consistency");
        statsTabs.add(paceTab, stintsTab, consistencyTab);
        statsTabs.addSelectedChangeListener(event -> updateStatsContent());
        statsContainer.add(statsTabs, statsContent);
        statsContainer.setSizeFull();
        statsContainer.setVisible(false);

        HorizontalLayout nav = new HorizontalLayout(backToSeason);
        if (!securityService.getAuthenticatedUser().isPresent()) {
            nav.add(new RouterLink("Login", LoginView.class));
        }
        nav.add(new RouterLink("Documentation", DocumentationView.class));
        nav.setSpacing(true);

        add(nav, eventHeader, mainTabs, resultsContainer, statsContainer);
        
        configureManualEntry();
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
            typeCombo.setItems(java.util.stream.IntStream.rangeClosed(1, 18).boxed().toList());
            typeCombo.setItemLabelGenerator(id -> TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(id, "Session " + id));
            typeCombo.setWidthFull();

            VerticalLayout layout = new VerticalLayout(typeCombo);
            dialog.add(layout);

            Button saveBtn = new Button("Add", ev -> {
                if (typeCombo.getValue() == null) return;
                SessionResult sr = new SessionResult();
                sr.setLeague(currentEvent.getLeague());
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
            dialog.setHeaderTitle("Add Result to " + TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(session.getSessionType(), "Session"));

            ComboBox<DriverMapping> driverCombo = new ComboBox<>("Driver");
            driverCombo.setItems(driverMappingRepository.findByLeague(currentEvent.getLeague()));
            driverCombo.setItemLabelGenerator(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty() ? m.getOverriddenName() : m.getTelemetryName());
            driverCombo.setWidthFull();

            ComboBox<String> teamCombo = new ComboBox<>("Team");
            teamCombo.setItems("Mercedes", "Ferrari", "Red Bull Racing", "Williams", "Aston Martin", "Alpine", "RB", "Haas", "McLaren", "Sauber");
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
                dr.setTeamName(teamCombo.getValue());
                dr.setPosition(posField.getValue().intValue());
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
                    } catch (Exception ex) {
                        Notification.show("Invalid total time format. Use m:ss.SSS or s.SSS", 5000, Notification.Position.TOP_CENTER);
                        return;
                    }
                }

                driverResultRepository.save(dr);
                
                // Recalculate gaps and standings for this league
                telemetryProcessingService.calculateGaps(session);
                telemetryProcessingService.recalculateStandings(currentEvent.getLeague().getId());
                
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
                Map.entry(18, 18)
        );
        sessions.sort(Comparator.comparingInt(s -> sortOrder.getOrDefault(s.getSessionType(), 99)));
        return sessions;
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        this.currentEventId = parameter;
        eventRepository.findByIdWithResults(parameter).ifPresentOrElse(e -> {
            this.currentEvent = e;
            eventHeader.setText("Event: " + currentEvent.getEventName());
            backToSeason.setRoute(SeasonDetailsView.class, currentEvent.getLeague().getId());
            setupSessionTabs();
            updateSessionContent();
        }, () -> {
            event.forwardTo(SeasonListView.class);
        });
    }

    private void setupSessionTabs() {
        sessionTabs.removeAll();
        List<SessionResult> sessions = getOrderedSessions();
        for (SessionResult session : sessions) {
            String sessionName = TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(session.getSessionType(), "Session " + session.getSessionType());
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

        grid.addColumn(dr -> dr.getNumLaps() != null ? dr.getNumLaps() : "-").setHeader("Laps");

        grid.addColumn(dr -> formatLapTime(dr.getBestLapTime() != null ? dr.getBestLapTime() : 0.0f))
                .setHeader("Best Lap")
                .setPartNameGenerator(dr -> (dr.getBestLapTime() != null && fastestLap > 0 && dr.getBestLapTime() == fastestLap) ? "fastest-lap" : null);

        if (!isQualifying) {
            grid.addColumn(dr -> formatLapTime(dr.getTotalTime() != null ? dr.getTotalTime().floatValue() : 0.0f)).setHeader("Total Time");
            grid.addColumn(dr -> dr.getGapToLeader() != null ? dr.getGapToLeader() : "-").setHeader("Gap");
        }

        if (isQualifying) {
            grid.addColumn(dr -> dr.getGapToLeader() != null ? dr.getGapToLeader() : "-").setHeader("Gap");

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
        if (statsTabs.getSelectedIndex() == 0) { // Pure Race Pace
            updatePaceData();
        } else if (statsTabs.getSelectedIndex() == 1) { // Longest Stints
            updateLongestStintsData();
        } else if (statsTabs.getSelectedIndex() == 2) { // Consistency
            updateConsistencyData();
        }
    }

    private void updateConsistencyData() {
        List<ConsistencyStats> stats = telemetryProcessingService.calculateConsistency(currentEventId);

        if (currentEvent.getLeague().isHideAi()) {
            stats = stats.stream().filter(s -> !s.isAi()).collect(Collectors.toList());
        }

        if (stats.isEmpty()) {
            statsContent.add(new Span("No consistency data available (need at least 3 valid laps)."));
            return;
        }

        Grid<ConsistencyStats> grid = new Grid<>(ConsistencyStats.class, false);
        grid.addColumn(ConsistencyStats::getDriverName).setHeader("Driver");
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

    private void updateLongestStintsData() {
        List<LongestStintStats> stats = telemetryProcessingService.calculateLongestStints(currentEventId);

        if (currentEvent.getLeague().isHideAi()) {
            stats = stats.stream().filter(s -> !s.isAi()).collect(Collectors.toList());
        }

        if (stats.isEmpty()) {
            statsContent.add(new Span("No stint data available."));
            return;
        }

        Grid<LongestStintStats> grid = new Grid<>(LongestStintStats.class, false);
        grid.addColumn(LongestStintStats::getDriverName).setHeader("Driver");
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
