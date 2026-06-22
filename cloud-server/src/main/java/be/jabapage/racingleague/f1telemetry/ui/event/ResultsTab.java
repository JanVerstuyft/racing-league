package be.jabapage.racingleague.f1telemetry.ui.event;

import be.jabapage.racingleague.f1telemetry.entity.*;
import be.jabapage.racingleague.f1telemetry.repository.*;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryResultsService;
import be.jabapage.racingleague.f1telemetry.ui.EventResultsView;
import be.jabapage.racingleague.f1telemetry.ui.LapComparisonView;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import java.util.*;
import java.util.stream.Collectors;

public class ResultsTab extends VerticalLayout {

    private final SessionResultRepository sessionResultRepository;
    private final DriverResultRepository driverResultRepository;
    private final DriverMappingRepository driverMappingRepository;
    private final TeamMappingRepository teamMappingRepository;
    private final EventLineupEntryRepository eventLineupEntryRepository;
    private final SessionPointConfigRepository sessionPointConfigRepository;
    private final ManualPenaltyRepository manualPenaltyRepository;
    private final LapTelemetryRepository lapTelemetryRepository;
    private final SecurityService securityService;
    private final TelemetryProcessingService telemetryProcessingService;
    private final TelemetryResultsService telemetryResultsService;
    private final Runnable onDataChanged;

    private Event currentEvent;

    private final Tabs sessionTabs = new Tabs();
    private final VerticalLayout sessionContent = new VerticalLayout();
    private final Button addSessionBtn = new Button("Add Manual Session");
    private final Button addResultBtn = new Button("Add Result");

    public ResultsTab(SessionResultRepository sessionResultRepository,
                      DriverResultRepository driverResultRepository,
                      DriverMappingRepository driverMappingRepository,
                      TeamMappingRepository teamMappingRepository,
                      EventLineupEntryRepository eventLineupEntryRepository,
                      SessionPointConfigRepository sessionPointConfigRepository,
                      ManualPenaltyRepository manualPenaltyRepository,
                      LapTelemetryRepository lapTelemetryRepository,
                      SecurityService securityService,
                      TelemetryProcessingService telemetryProcessingService,
                      TelemetryResultsService telemetryResultsService,
                      Runnable onDataChanged) {
        this.sessionResultRepository = sessionResultRepository;
        this.driverResultRepository = driverResultRepository;
        this.driverMappingRepository = driverMappingRepository;
        this.teamMappingRepository = teamMappingRepository;
        this.eventLineupEntryRepository = eventLineupEntryRepository;
        this.sessionPointConfigRepository = sessionPointConfigRepository;
        this.manualPenaltyRepository = manualPenaltyRepository;
        this.lapTelemetryRepository = lapTelemetryRepository;
        this.securityService = securityService;
        this.telemetryProcessingService = telemetryProcessingService;
        this.telemetryResultsService = telemetryResultsService;
        this.onDataChanged = onDataChanged;

        setSizeFull();
        setPadding(false);

        sessionTabs.setWidthFull();
        sessionTabs.addSelectedChangeListener(event -> updateSessionContent());
        
        HorizontalLayout sessionActions = new HorizontalLayout(addSessionBtn, addResultBtn);
        addResultBtn.setVisible(false);
        
        add(sessionTabs, sessionActions, sessionContent);

        configureManualEntry();
    }

    public void update(Event event) {
        this.currentEvent = event;
        if (event == null) return;

        int currentIdx = sessionTabs.getSelectedIndex();
        setupSessionTabs();
        if (currentIdx >= 0 && currentIdx < sessionTabs.getComponentCount()) {
            sessionTabs.setSelectedIndex(currentIdx);
        }
        updateSessionContent();
    }

    private void setupSessionTabs() {
        sessionTabs.removeAll();
        List<SessionResult> sessions = getOrderedSessions();
        Set<Integer> types = currentEvent.getSessionResults().stream()
                .map(SessionResult::getSessionType)
                .collect(Collectors.toSet());
        for (SessionResult session : sessions) {
            String sessionName = EventResultsView.getDynamicSessionName(session.getSessionType(), types);
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

        grid.addColumn(dr -> EventResultsView.formatLapTime(dr.getBestLapTime() != null ? dr.getBestLapTime() : 0.0f))
                .setHeader("Best Lap")
                .setPartNameGenerator(dr -> (dr.getBestLapTime() != null && fastestLap > 0 && dr.getBestLapTime() == fastestLap) ? "fastest-lap" : null);

        if (!isQualifying) {
            grid.addColumn(dr -> {
                if (dr.getPosition() != null && dr.getPosition() == 1) {
                    return EventResultsView.formatLapTime(dr.getTotalTime() != null ? dr.getTotalTime().floatValue() : 0.0f);
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
                return EventResultsView.formatLapTime(s1 / 1000.0f);
            }).setHeader("S1").setPartNameGenerator(dr -> {
                LapResult fastest = getFastestLap(dr);
                long s1 = (fastest != null && fastest.getS1InMS() != null) ? fastest.getS1InMS() : 0L;
                return (s1 > 0 && s1 == sessionBestS1) ? "best-sector" : null;
            });

            grid.addColumn(dr -> {
                LapResult fastest = getFastestLap(dr);
                long s2 = (fastest != null && fastest.getS2InMS() != null) ? fastest.getS2InMS() : 0L;
                return EventResultsView.formatLapTime(s2 / 1000.0f);
            }).setHeader("S2").setPartNameGenerator(dr -> {
                LapResult fastest = getFastestLap(dr);
                long s2 = (fastest != null && fastest.getS2InMS() != null) ? fastest.getS2InMS() : 0L;
                return (s2 > 0 && s2 == sessionBestS2) ? "best-sector" : null;
            });

            grid.addColumn(dr -> {
                LapResult fastest = getFastestLap(dr);
                long s3 = (fastest != null && fastest.getS3InMS() != null) ? fastest.getS3InMS() : 0L;
                return EventResultsView.formatLapTime(s3 / 1000.0f);
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
                        .sorted(Comparator.comparingInt(TyreStint::getStintOrder))
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
            grid.addComponentColumn(dr -> {
                int warnings = dr.getWarnings() != null ? dr.getWarnings() : 0;
                Span span = new Span(String.valueOf(warnings));
                if (warnings > 0 && warnings % 3 == 2) {
                    span.getElement().setAttribute("title", "One warning away from a penalty");
                }
                return span;
            })
            .setHeader("Warn")
            .setPartNameGenerator(dr -> (dr.getWarnings() != null && dr.getWarnings() % 3 == 2) ? "warning-danger" : null);
        }
        
        if (loggedIn) {
            grid.addComponentColumn(dr -> {
                Button editBtn = new Button("Edit", e -> showEditResultDialog(dr, session));
                editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);

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
                            onDataChanged.run();
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

                HorizontalLayout actions = new HorizontalLayout(editBtn, deleteBtn);
                actions.setSpacing(true);
                return actions;
            }).setHeader("Actions").setAutoWidth(true);
        }

        grid.setItems(driverResults);
        grid.setAllRowsVisible(true);
        
        boolean telemetryAvailable = lapTelemetryRepository.existsBySessionResultId(session.getId());
        if (telemetryAvailable) {
            Button compareBtn = new Button("Compare Lap Telemetry", com.vaadin.flow.component.icon.VaadinIcon.CHART.create());
            compareBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            compareBtn.getStyle().set("margin-bottom", "10px");
            compareBtn.addClickListener(e -> compareBtn.getUI().ifPresent(ui -> ui.navigate(LapComparisonView.class, session.getId())));
            sessionContent.add(compareBtn);
        }
        
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
                        onDataChanged.run();
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

        if (!driverResults.isEmpty()) {
            H2 posterHeader = new H2("Results Poster");
            posterHeader.getStyle().set("margin-top", "30px");

            List<SessionResult> allSessions = getOrderedSessions();
            String sessName = EventResultsView.getDynamicSessionName(session.getSessionType(), allSessions.stream().map(SessionResult::getSessionType).toList());

            String carType = getCarTypeForEvent();
            Div poster = PosterRenderer.createResultsPoster(currentEvent, session, driverResults, carType);

            Button downloadResultsBtn = new Button("Download Results Image");
            downloadResultsBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            downloadResultsBtn.getStyle().set("margin-bottom", "15px");
            downloadResultsBtn.addClickListener(e -> {
                getElement().executeJs(
                    "window.downloadInfographic('.results-poster', 'results_' + $0 + '.png', $1)",
                    currentEvent.getEventName().toLowerCase().replace(" ", "_") + "_" + sessName.toLowerCase().replace(" ", "_"),
                    e.getSource().getElement()
                );
            });

            sessionContent.add(posterHeader, downloadResultsBtn, poster);

            boolean isRaceOrSprint = (session.getSessionType() >= 15 && session.getSessionType() <= 17) || session.getSessionType() == 19;
            if (isRaceOrSprint) {
                H2 pitstopsHeader = new H2("Pit Stops Poster");
                pitstopsHeader.getStyle().set("margin-top", "30px");

                Div pitstopsPoster = PosterRenderer.createPitStopsPoster(currentEvent, session, driverResults, carType);

                Button downloadPitStopsBtn = new Button("Download Pit Stops Image");
                downloadPitStopsBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
                downloadPitStopsBtn.getStyle().set("margin-bottom", "15px");
                downloadPitStopsBtn.addClickListener(e -> {
                    getElement().executeJs(
                        "window.downloadInfographic('.pitstops-poster', 'pitstops_' + $0 + '.png', $1)",
                        currentEvent.getEventName().toLowerCase().replace(" ", "_") + "_" + sessName.toLowerCase().replace(" ", "_"),
                        e.getSource().getElement()
                    );
                });

                sessionContent.add(pitstopsHeader, downloadPitStopsBtn, pitstopsPoster);
            }
        }
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
                onDataChanged.run();
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
            Set<Integer> types = currentEvent.getSessionResults().stream().map(SessionResult::getSessionType).collect(Collectors.toSet());
            dialog.setHeaderTitle("Add Result to " + EventResultsView.getDynamicSessionName(session.getSessionType(), types));

            ComboBox<DriverMapping> driverCombo = new ComboBox<>("Driver");
            List<DriverMapping> tierDrivers = driverMappingRepository.findByTier(currentEvent.getTier());
            List<DriverMapping> lineupDrivers = eventLineupEntryRepository.findByEvent(currentEvent).stream()
                    .map(EventLineupEntry::getDriver)
                    .toList();
            Set<DriverMapping> allDrivers = new LinkedHashSet<>(tierDrivers);
            allDrivers.addAll(lineupDrivers);

            driverCombo.setItems(allDrivers.stream()
                    .sorted(Comparator.comparing(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty() ? m.getOverriddenName() : m.getTelemetryName()))
                    .collect(Collectors.toList()));
            driverCombo.setItemLabelGenerator(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty() ? m.getOverriddenName() : m.getTelemetryName());
            driverCombo.setWidthFull();

            League league = currentEvent.getTier().getLeague();
            boolean teamsEnabled = league != null && Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
                && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
                && league.getTeamBName() != null && !league.getTeamBName().isEmpty();

            ComboBox<TeamMapping> teamCombo = new ComboBox<>("Team");
            String carType = getCarTypeForEvent();
            List<TeamMapping> allTeams = getTeamsForCarType(carType);
            teamCombo.setItems(allTeams);
            teamCombo.setItemLabelGenerator(TeamMapping::getTeamName);
            teamCombo.setWidthFull();

            ComboBox<String> champTeamCombo = new ComboBox<>("Championship Team");
            champTeamCombo.setItems("A", "B", "None");
            champTeamCombo.setItemLabelGenerator(val -> {
                if ("A".equals(val)) {
                    return league.getTeamAName() != null ? league.getTeamAName() : "Team A";
                } else if ("B".equals(val)) {
                    return league.getTeamBName() != null ? league.getTeamBName() : "Team B";
                }
                return "None";
            });
            champTeamCombo.setValue("None");
            champTeamCombo.setWidthFull();
            champTeamCombo.setVisible(teamsEnabled);

            driverCombo.addValueChangeListener(ev -> {
                DriverMapping dm = ev.getValue();
                if (dm == null) {
                    teamCombo.clear();
                    teamCombo.setReadOnly(false);
                    champTeamCombo.setValue("None");
                    champTeamCombo.setReadOnly(false);
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
                            teamCombo.setValue(uiTeam);
                            teamCombo.setReadOnly(true);
                        } else {
                            teamCombo.clear();
                            teamCombo.setReadOnly(false);
                        }
                    } else {
                        teamCombo.clear();
                        teamCombo.setReadOnly(false);
                    }

                    if (dm.getChampionshipTeam() != null) {
                        champTeamCombo.setValue(dm.getChampionshipTeam());
                        champTeamCombo.setReadOnly(true);
                    } else {
                        champTeamCombo.setValue("None");
                        champTeamCombo.setReadOnly(false);
                    }
                } else {
                    teamCombo.clear();
                    teamCombo.setReadOnly(false);
                    Optional<EventLineupEntry> eleOpt = eventLineupEntryRepository.findByEvent(currentEvent).stream()
                        .filter(entry -> Objects.equals(entry.getDriver().getId(), dm.getId()))
                        .findFirst();
                    if (eleOpt.isPresent()) {
                        if (eleOpt.get().getChampionshipTeam() != null) {
                            champTeamCombo.setValue(eleOpt.get().getChampionshipTeam());
                            champTeamCombo.setReadOnly(true);
                        } else {
                            champTeamCombo.setValue("None");
                            champTeamCombo.setReadOnly(false);
                        }
                        
                        Integer lineupTeamId = eleOpt.get().getTeamId();
                        if (lineupTeamId != null) {
                            Optional<TeamMapping> tmOpt = teamMappingRepository.findByTeamIdAndCarType(lineupTeamId, carType);
                            if (tmOpt.isPresent()) {
                                String teamName = tmOpt.get().getTeamName();
                                TeamMapping uiTeam = allTeams.stream()
                                        .filter(t -> Objects.equals(t.getTeamName(), teamName))
                                        .findFirst()
                                        .orElse(null);
                                teamCombo.setValue(uiTeam);
                                teamCombo.setReadOnly(true);
                            }
                        }
                    } else {
                        champTeamCombo.setValue("None");
                        champTeamCombo.setReadOnly(false);
                    }
                }
            });

            NumberField posField = new NumberField("Position");
            posField.setStepButtonsVisible(true);
            posField.setMin(1);
            posField.setMax(22);

            NumberField pointsField = new NumberField("Points");
            pointsField.setStepButtonsVisible(true);
            pointsField.setVisible(false);

            posField.addValueChangeListener(ev -> {
                if (ev.getValue() != null) {
                    int pos = ev.getValue().intValue();
                    List<SessionPointConfig> configs = sessionPointConfigRepository.findByLeague(league);
                    int pts = telemetryResultsService.getPointsForPosition(configs, session, pos);
                    pointsField.setValue((double) pts);
                } else {
                    pointsField.setValue(0.0);
                }
            });

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

            VerticalLayout layout = new VerticalLayout(driverCombo, teamCombo, champTeamCombo,
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
                if (teamsEnabled && !"None".equals(champTeamCombo.getValue())) {
                    dr.setChampionshipTeam(champTeamCombo.getValue());
                } else {
                    dr.setChampionshipTeam(null);
                }
                
                if (timeField.getValue() != null && !timeField.getValue().isEmpty()) {
                    try {
                        dr.setBestLapTime(EventResultsView.parseLapTime(timeField.getValue()));
                    } catch (Exception ex) {
                        Notification.show("Invalid best lap time format. Use m:ss.SSS or s.SSS", 5000, Notification.Position.TOP_CENTER);
                        return;
                    }
                }

                if (totalTimeField.getValue() != null && !totalTimeField.getValue().isEmpty()) {
                    try {
                        dr.setTotalTime((double) EventResultsView.parseLapTime(totalTimeField.getValue()));
                        dr.setRawTotalTime(dr.getTotalTime());
                    } catch (Exception ex) {
                        Notification.show("Invalid total time format. Use m:ss.SSS or s.SSS", 5000, Notification.Position.TOP_CENTER);
                        return;
                    }
                }

                driverResultRepository.save(dr);
                
                telemetryProcessingService.calculateGaps(session);
                telemetryProcessingService.recalculateStandings(currentEvent.getTier().getId());
                
                onDataChanged.run();
                dialog.close();
                Notification.show("Result added", 3000, Notification.Position.TOP_CENTER);
            });
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
            dialog.open();
        });
    }

    private void showEditResultDialog(DriverResult dr, SessionResult session) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Result: " + dr.getDriverName());

        TextField driverField = new TextField("Driver");
        driverField.setValue(dr.getDriverName());
        driverField.setReadOnly(true);
        driverField.setWidthFull();

        League league = currentEvent.getTier().getLeague();
        boolean teamsEnabled = league != null && Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
            && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
            && league.getTeamBName() != null && !league.getTeamBName().isEmpty();

        ComboBox<TeamMapping> teamCombo = new ComboBox<>("Team");
        String carType = getCarTypeForEvent();
        List<TeamMapping> allTeams = getTeamsForCarType(carType);
        teamCombo.setItems(allTeams);
        teamCombo.setItemLabelGenerator(TeamMapping::getTeamName);
        teamCombo.setWidthFull();
        if (dr.getTeamId() != null) {
            Optional<TeamMapping> tmOpt = teamMappingRepository.findByTeamIdAndCarType(dr.getTeamId(), carType);
            tmOpt.ifPresent(teamCombo::setValue);
        }

        ComboBox<String> champTeamCombo = new ComboBox<>("Championship Team");
        champTeamCombo.setItems("A", "B", "None");
        champTeamCombo.setItemLabelGenerator(val -> {
            if ("A".equals(val)) {
                return league.getTeamAName() != null ? league.getTeamAName() : "Team A";
            } else if ("B".equals(val)) {
                return league.getTeamBName() != null ? league.getTeamBName() : "Team B";
            }
            return "None";
        });
        champTeamCombo.setValue(dr.getChampionshipTeam() != null ? dr.getChampionshipTeam() : "None");
        champTeamCombo.setWidthFull();
        champTeamCombo.setVisible(teamsEnabled);

        ComboBox<Integer> statusCombo = new ComboBox<>("Result Status");
        statusCombo.setItems(3, 4, 5, 6, 7);
        statusCombo.setItemLabelGenerator(status -> {
            if (status == 3) return "Finished (3)";
            if (status == 4) return "DNF (4)";
            if (status == 5) return "DSQ (5)";
            if (status == 6) return "NC (6)";
            if (status == 7) return "RET (7)";
            return "Unknown (" + status + ")";
        });
        statusCombo.setValue(dr.getResultStatus() != null ? dr.getResultStatus() : 3);
        statusCombo.setWidthFull();

        NumberField overridePosField = new NumberField("Override Position");
        overridePosField.setStepButtonsVisible(true);
        overridePosField.setMin(1);
        overridePosField.setMax(22);
        overridePosField.setHelperText("Leave blank to calculate automatically");

        TextField overrideTimeField = new TextField("Override Total Time (e.g. 45:12.300)");
        overrideTimeField.setHelperText("Leave blank to use raw race time");

        NumberField penaltiesField = new NumberField("Time Penalty (seconds)");
        penaltiesField.setStepButtonsVisible(true);

        com.vaadin.flow.component.textfield.IntegerField warningsField = new com.vaadin.flow.component.textfield.IntegerField("Warnings");
        warningsField.setStepButtonsVisible(true);
        warningsField.setMin(0);
        warningsField.setValue(dr.getWarnings() != null ? dr.getWarnings() : 0);
        warningsField.setHelperText("Shown in yellow when one warning away from a penalty (e.g. 2, 5, 8...)");

        com.vaadin.flow.component.textfield.IntegerField lapsField = new com.vaadin.flow.component.textfield.IntegerField("Laps Completed");
        lapsField.setStepButtonsVisible(true);
        lapsField.setMin(0);
        lapsField.setValue(dr.getNumLaps() != null ? dr.getNumLaps() : 0);

        TextField timeField = new TextField("Best Lap Time (e.g. 1:24.500)");
        if (dr.getBestLapTime() != null && dr.getBestLapTime() > 0) {
            timeField.setValue(EventResultsView.formatLapTime(dr.getBestLapTime()));
        }

        com.vaadin.flow.component.textfield.IntegerField pointDeductionField = new com.vaadin.flow.component.textfield.IntegerField("Points Deduction (PD)");
        pointDeductionField.setStepButtonsVisible(true);
        pointDeductionField.setMin(0);

        TextArea commentField = new TextArea("Comment / Reason");
        commentField.setWidthFull();

        List<DriverMapping> mappings = driverMappingRepository.findByLeague(currentEvent.getTier().getLeague());
        DriverMapping mapping = telemetryResultsService.findMappingForDriverResult(dr, mappings, currentEvent.getTier());
        final DriverMapping finalMapping = mapping;

        ManualPenalty existingPenaltyLocal = null;
        if (mapping != null) {
            existingPenaltyLocal = manualPenaltyRepository.findBySessionResult(session).stream()
                    .filter(p -> p.getDriverMapping().getId().equals(finalMapping.getId()))
                    .findFirst()
                    .orElse(null);
        }
        final ManualPenalty existingPenalty = existingPenaltyLocal;

        if (existingPenalty != null) {
            if (existingPenalty.getSeconds() != null) {
                penaltiesField.setValue(existingPenalty.getSeconds().doubleValue());
            }
            if (existingPenalty.getPointDeduction() != null) {
                pointDeductionField.setValue(existingPenalty.getPointDeduction());
            }
            if (existingPenalty.getComment() != null) {
                commentField.setValue(existingPenalty.getComment());
            }
            if (existingPenalty.getOverridePosition() != null) {
                overridePosField.setValue(existingPenalty.getOverridePosition().doubleValue());
            }
            if (existingPenalty.getOverrideTime() != null) {
                overrideTimeField.setValue(EventResultsView.formatLapTime(existingPenalty.getOverrideTime().floatValue()));
            }
        }

        HorizontalLayout overridesRow = new HorizontalLayout(overridePosField, overrideTimeField);
        overridesRow.setWidthFull();
        HorizontalLayout penaltiesRow = new HorizontalLayout(penaltiesField, pointDeductionField);
        penaltiesRow.setWidthFull();
        HorizontalLayout statsRow = new HorizontalLayout(warningsField, lapsField, timeField);
        statsRow.setWidthFull();

        VerticalLayout layout = new VerticalLayout(driverField, teamCombo, champTeamCombo, statusCombo, overridesRow, penaltiesRow, statsRow, commentField);
        dialog.add(layout);

        Button saveBtn = new Button("Save", ev -> {
            if (teamCombo.getValue() == null) {
                Notification.show("Please select a Team", 3000, Notification.Position.TOP_CENTER);
                return;
            }

            dr.setTeamId(teamCombo.getValue().getTeamId());
            if (teamsEnabled && !"None".equals(champTeamCombo.getValue())) {
                dr.setChampionshipTeam(champTeamCombo.getValue());
            } else {
                dr.setChampionshipTeam(null);
            }
            dr.setResultStatus(statusCombo.getValue());
            dr.setWarnings(warningsField.getValue() != null ? warningsField.getValue() : 0);
            dr.setNumLaps(lapsField.getValue() != null ? lapsField.getValue() : 0);

            if (timeField.getValue() != null && !timeField.getValue().isEmpty()) {
                try {
                    dr.setBestLapTime(EventResultsView.parseLapTime(timeField.getValue()));
                } catch (Exception ex) {
                    Notification.show("Invalid best lap time format. Use m:ss.SSS or s.SSS", 5000, Notification.Position.TOP_CENTER);
                    return;
                }
            } else {
                dr.setBestLapTime(0.0f);
            }

            driverResultRepository.save(dr);

            boolean hasOverridePos = overridePosField.getValue() != null;
            boolean hasOverrideTime = overrideTimeField.getValue() != null && !overrideTimeField.getValue().isEmpty();
            boolean hasSeconds = penaltiesField.getValue() != null && penaltiesField.getValue() != 0.0;
            boolean hasDeduction = pointDeductionField.getValue() != null && pointDeductionField.getValue() != 0;
            boolean hasComment = commentField.getValue() != null && !commentField.getValue().trim().isEmpty();

            if (finalMapping != null) {
                if (hasOverridePos || hasOverrideTime || hasSeconds || hasDeduction || hasComment) {
                    ManualPenalty p = existingPenalty != null ? existingPenalty : new ManualPenalty();
                    p.setSessionResult(session);
                    p.setDriverMapping(finalMapping);
                    p.setOverridePosition(hasOverridePos ? overridePosField.getValue().intValue() : null);

                    if (hasOverrideTime) {
                        try {
                            p.setOverrideTime((double) EventResultsView.parseLapTime(overrideTimeField.getValue()));
                        } catch (Exception ex) {
                            Notification.show("Invalid override total time format. Use m:ss.SSS or s.SSS", 5000, Notification.Position.TOP_CENTER);
                            return;
                        }
                    } else {
                        p.setOverrideTime(null);
                    }

                    p.setSeconds(hasSeconds ? penaltiesField.getValue().intValue() : null);
                    p.setPointDeduction(hasDeduction ? pointDeductionField.getValue() : null);
                    p.setComment(hasComment ? commentField.getValue() : null);

                    manualPenaltyRepository.save(p);
                } else if (existingPenalty != null) {
                    manualPenaltyRepository.delete(existingPenalty);
                }
            }

            telemetryProcessingService.recalculateStandings(currentEvent.getTier().getId());
            onDataChanged.run();
            dialog.close();
            Notification.show("Result updated and standings recalculated", 3000, Notification.Position.TOP_CENTER);
        });

        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
        dialog.open();
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

    private List<TeamMapping> getTeamsForCarType(String carType) {
        List<TeamMapping> teams = teamMappingRepository.findByCarType(carType);
        if ("F1 26".equals(carType)) {
            teams = teams.stream().filter(t -> t.getTeamId() != null && t.getTeamId() >= 400).toList();
        }
        return teams;
    }
}
