package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.entity.LapResult;
import be.jabapage.racingleague.f1telemetry.entity.EventLineupEntry;
import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.entity.TyreStint;
import be.jabapage.racingleague.f1telemetry.entity.ManualPenalty;
import be.jabapage.racingleague.f1telemetry.repository.ManualPenaltyRepository;
import com.vaadin.flow.component.textfield.TextArea;
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
import be.jabapage.racingleague.f1telemetry.entity.SessionPointConfig;
import be.jabapage.racingleague.f1telemetry.repository.SessionPointConfigRepository;
import be.jabapage.racingleague.f1telemetry.repository.SessionResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.LapTelemetryRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryResultsService;
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
    private final TelemetryResultsService telemetryResultsService;
    private final SessionPointConfigRepository sessionPointConfigRepository;
    private final LapTelemetryRepository lapTelemetryRepository;
    private final ManualPenaltyRepository manualPenaltyRepository;

    private final HorizontalLayout logoContainer = new HorizontalLayout();
    private final VerticalLayout lineupContainer = new VerticalLayout();
    private final Grid<EventLineupEntry> lineupGrid = new Grid<>(EventLineupEntry.class, false);
    private Grid.Column<EventLineupEntry> champTeamColumn;
    private final Button addLineupBtn = new Button("Add Driver to Lineup");
    private final Button clearLineupBtn = new Button("Clear Lineup");
    private final Button updateRealLineupBtn = new Button("Update with Real Lineup");

    private final H2 eventHeader = new H2();
    private final Span statusBadge = new Span();
    private final Button toggleFinalizedBtn = new Button();
    private final RouterLink backToSeason = new RouterLink("Back to Season", SeasonDetailsView.class, 0L);
    
    private final VerticalLayout resultsContainer = new VerticalLayout();
    private final VerticalLayout statsContainer = new VerticalLayout();
    private final VerticalLayout infographicsContainer = new VerticalLayout();
    
    private final Tabs sessionTabs = new Tabs();
    private final VerticalLayout sessionContent = new VerticalLayout();
    
    private final Tabs infographicsSessionTabs = new Tabs();
    private final VerticalLayout infographicsContent = new VerticalLayout();
    
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
                            DriverStandingRepository driverStandingRepository,
                            TelemetryResultsService telemetryResultsService,
                            SessionPointConfigRepository sessionPointConfigRepository,
                            LapTelemetryRepository lapTelemetryRepository,
                            ManualPenaltyRepository manualPenaltyRepository) {
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
        this.telemetryResultsService = telemetryResultsService;
        this.sessionPointConfigRepository = sessionPointConfigRepository;
        this.lapTelemetryRepository = lapTelemetryRepository;
        this.manualPenaltyRepository = manualPenaltyRepository;
        setSizeFull();

        // Main Tabs
        Tab resultsTab = new Tab("Results");
        Tab statsTab = new Tab("Stats");
        Tab lineupTab = new Tab("Lineup");
        Tab infographicsTab = new Tab("Infographics");
        Tabs mainTabs = new Tabs(resultsTab, statsTab, lineupTab, infographicsTab);
        
        mainTabs.addSelectedChangeListener(event -> {
            boolean isResults = event.getSelectedTab().equals(resultsTab);
            boolean isStats = event.getSelectedTab().equals(statsTab);
            boolean isLineup = event.getSelectedTab().equals(lineupTab);
            boolean isInfographics = event.getSelectedTab().equals(infographicsTab);
            
            resultsContainer.setVisible(isResults);
            statsContainer.setVisible(isStats);
            lineupContainer.setVisible(isLineup);
            infographicsContainer.setVisible(isInfographics);
            
            if (isStats) {
                setupStatsSessionTabs();
                updateStatsContent();
            } else if (isLineup) {
                initLineupIfEmpty();
                updateLineupContent();
            } else if (isInfographics) {
                setupInfographicsSessionTabs();
                updateInfographicsContent();
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
        statusBadge.getStyle().set("margin-left", "var(--lumo-space-m)");
        toggleFinalizedBtn.getStyle().set("margin-left", "var(--lumo-space-m)");
        HorizontalLayout titleLayout = new HorizontalLayout(logoContainer, eventHeader, statusBadge, toggleFinalizedBtn);
        titleLayout.setAlignItems(Alignment.CENTER);
        titleLayout.setSpacing(true);

        toggleFinalizedBtn.addClickListener(ev -> {
            if (currentEvent == null) return;
            boolean newStatus = !Boolean.TRUE.equals(currentEvent.getFinalized());
            String statusWord = newStatus ? "final" : "provisional";
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Mark Event as " + statusWord.substring(0, 1).toUpperCase() + statusWord.substring(1) + "?");
            dialog.setText("Are you sure you want to mark this event as " + statusWord + "? Standings will be recalculated.");
            dialog.setCancelable(true);
            dialog.setConfirmText("Yes");
            dialog.addConfirmListener(confirmEv -> {
                currentEvent.setFinalized(newStatus);
                eventRepository.save(currentEvent);
                telemetryProcessingService.recalculateStandings(currentEvent.getTier().getId());
                refreshEvent();
                Notification.show("Event marked as " + statusWord + " and standings recalculated", 3000, Notification.Position.TOP_CENTER);
            });
            dialog.open();
        });

        // Infographics Section
        infographicsSessionTabs.setWidthFull();
        infographicsSessionTabs.addSelectedChangeListener(event -> updateInfographicsContent());
        infographicsContainer.add(infographicsSessionTabs, infographicsContent);
        infographicsContainer.setSizeFull();
        infographicsContainer.setVisible(false);

        lineupContainer.setSizeFull();
        lineupContainer.setVisible(false);
        add(nav, titleLayout, mainTabs, resultsContainer, statsContainer, lineupContainer, infographicsContainer);
        
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
            java.util.List<DriverMapping> tierDrivers = driverMappingRepository.findByTier(currentEvent.getTier());
            java.util.List<DriverMapping> lineupDrivers = eventLineupEntryRepository.findByEvent(currentEvent).stream()
                    .map(EventLineupEntry::getDriver)
                    .toList();
            java.util.Set<DriverMapping> allDrivers = new java.util.LinkedHashSet<>(tierDrivers);
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
                
                // If regular driver, resolve from mapping
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
                    // For reserve, check if there's a lineup entry for this driver in this event
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
            timeField.setValue(formatLapTime(dr.getBestLapTime()));
        }

        com.vaadin.flow.component.textfield.IntegerField pointDeductionField = new com.vaadin.flow.component.textfield.IntegerField("Points Deduction (PD)");
        pointDeductionField.setStepButtonsVisible(true);
        pointDeductionField.setMin(0);

        TextArea commentField = new TextArea("Comment / Reason");
        commentField.setWidthFull();

        // Load existing ManualPenalty values
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
                overrideTimeField.setValue(formatLapTime(existingPenalty.getOverrideTime().floatValue()));
            }
        }

        // Layout
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
                    dr.setBestLapTime(parseLapTime(timeField.getValue()));
                } catch (Exception ex) {
                    Notification.show("Invalid best lap time format. Use m:ss.SSS or s.SSS", 5000, Notification.Position.TOP_CENTER);
                    return;
                }
            } else {
                dr.setBestLapTime(0.0f);
            }

            // Save DriverResult changes
            driverResultRepository.save(dr);

            // Handle ManualPenalty save/update/delete
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
                            p.setOverrideTime((double) parseLapTime(overrideTimeField.getValue()));
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

            // Recalculate standings & gaps
            telemetryProcessingService.recalculateStandings(currentEvent.getTier().getId());
            refreshEvent();
            dialog.close();
            Notification.show("Result updated and standings recalculated", 3000, Notification.Position.TOP_CENTER);
        });

        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
        dialog.open();
    }

    public static float parseLapTime(String text) {
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
        telemetryProcessingService.getEventWithAllResults(currentEventId).ifPresent(e -> {
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
            int currentInfoIdx = infographicsSessionTabs.getSelectedIndex();
            setupInfographicsSessionTabs();
            if (currentInfoIdx >= 0 && currentInfoIdx < infographicsSessionTabs.getComponentCount()) {
                infographicsSessionTabs.setSelectedIndex(currentInfoIdx);
            }
            updateSessionContent();
            updateInfographicsContent();
            updateStatusUI();
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
            String sessionName = getDynamicSessionName(session.getSessionType(), types);
            infographicsSessionTabs.add(new Tab(sessionName));
        }
    }

    private void updateInfographicsContent() {
        infographicsContent.removeAll();
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
        String sessNameSafe = getDynamicSessionName(session.getSessionType(), sessions.stream().map(SessionResult::getSessionType).toList()).toLowerCase().replace(" ", "_");

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
        Div resultsPoster = createResultsPoster(session, driverResults);
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
            Div pitstopsPoster = createPitStopsPoster(session, driverResults);
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
                Div pacePoster = createPacePoster(session, paceStats);
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
                Div stintsPoster = createStintsPoster(session, stintStats);
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
                Div consistencyPoster = createConsistencyPoster(session, consistencyStats);
                infographicsContent.add(consistencyHeader, downloadConsistencyBtn, consistencyPoster);
            }
        }
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        this.currentEventId = parameter;
        telemetryProcessingService.getEventWithAllResults(parameter).ifPresentOrElse(e -> {
            this.currentEvent = e;
            eventHeader.setText("Event: " + currentEvent.getEventName());
            backToSeason.setRoute(SeasonDetailsView.class, currentEvent.getTier().getLeague().getId());
            updateLogo();
            setupSessionTabs();
            setupStatsSessionTabs();
            setupInfographicsSessionTabs();
            updateSessionContent();
            updateStatusUI();
        }, () -> {
            event.forwardTo(SeasonListView.class);
        });
    }

    private void updateStatusUI() {
        if (currentEvent == null) return;

        statusBadge.getElement().getThemeList().clear();
        statusBadge.getElement().removeAttribute("title");
        if ("FINAL".equalsIgnoreCase(currentEvent.getStatus())) {
            statusBadge.setText("Final");
            statusBadge.getElement().getThemeList().add("badge success");
        } else if ("PROVISIONAL_WARNING".equalsIgnoreCase(currentEvent.getStatus())) {
            statusBadge.setText("Provisional (Warning)");
            statusBadge.getElement().getThemeList().add("badge warning");
            statusBadge.getElement().setAttribute("title", "Saved via fallback: didn't receive final classification packages.");
        } else {
            statusBadge.setText("Provisional");
            statusBadge.getElement().getThemeList().add("badge error");
        }

        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        toggleFinalizedBtn.setVisible(loggedIn);
        if (loggedIn) {
            toggleFinalizedBtn.getElement().getThemeList().clear();
            toggleFinalizedBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
            if (Boolean.TRUE.equals(currentEvent.getFinalized())) {
                toggleFinalizedBtn.setText("Reopen");
                toggleFinalizedBtn.setIcon(com.vaadin.flow.component.icon.VaadinIcon.UNLOCK.create());
                toggleFinalizedBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            } else {
                toggleFinalizedBtn.setText("Mark Final");
                toggleFinalizedBtn.setIcon(com.vaadin.flow.component.icon.VaadinIcon.LOCK.create());
                toggleFinalizedBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            }
        }
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

        if (!driverResults.isEmpty()) {
            H2 posterHeader = new H2("Results Poster");
            posterHeader.getStyle().set("margin-top", "30px");

            List<SessionResult> allSessions = getOrderedSessions();
            String sessName = getDynamicSessionName(session.getSessionType(), allSessions.stream().map(SessionResult::getSessionType).toList());

            Div poster = createResultsPoster(session, driverResults);

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

                Div pitstopsPoster = createPitStopsPoster(session, driverResults);

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
            downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            String eventNameSafe = currentEvent.getEventName().toLowerCase().replace(" ", "_");
            String sessNameSafe = getDynamicSessionName(selectedSession.getSessionType(), getOrderedSessions().stream().map(SessionResult::getSessionType).toList()).toLowerCase().replace(" ", "_");
            downloadBtn.addClickListener(ev -> {
                getElement().executeJs(
                    "window.downloadInfographic('.consistency-poster', $0 + '_consistency.png', $1)",
                    eventNameSafe + "_" + sessNameSafe,
                    ev.getSource().getElement()
                );
            });

            Div poster = createConsistencyPoster(selectedSession, stats);
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
        grid.addColumn(s -> formatLapTime((float) s.getAvgLapTime())).setHeader("Avg Lap");
        grid.addColumn(s -> formatLapTime((float) s.getAvgS1())).setHeader("Avg S1");
        grid.addColumn(s -> formatLapTime((float) s.getAvgS2())).setHeader("Avg S2");
        grid.addColumn(s -> formatLapTime((float) s.getAvgS3())).setHeader("Avg S3");

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
            downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            String eventNameSafe = currentEvent.getEventName().toLowerCase().replace(" ", "_");
            String sessNameSafe = getDynamicSessionName(selectedSession.getSessionType(), getOrderedSessions().stream().map(SessionResult::getSessionType).toList()).toLowerCase().replace(" ", "_");
            downloadBtn.addClickListener(ev -> {
                getElement().executeJs(
                    "window.downloadInfographic('.stints-poster', $0 + '_stints.png', $1)",
                    eventNameSafe + "_" + sessNameSafe,
                    ev.getSource().getElement()
                );
            });

            Div poster = createStintsPoster(selectedSession, stats);
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
            downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            String eventNameSafe = currentEvent.getEventName().toLowerCase().replace(" ", "_");
            String sessNameSafe = getDynamicSessionName(selectedSession.getSessionType(), getOrderedSessions().stream().map(SessionResult::getSessionType).toList()).toLowerCase().replace(" ", "_");
            downloadBtn.addClickListener(ev -> {
                getElement().executeJs(
                    "window.downloadInfographic('.pace-poster', $0 + '_pace.png', $1)",
                    eventNameSafe + "_" + sessNameSafe,
                    ev.getSource().getElement()
                );
            });

            Div poster = createPacePoster(selectedSession, stats);
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

    public static String formatLapTime(float seconds) {
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
        attachEvent.getUI().getPage().executeJs(getDownloadInfographicJs());
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

        League league = currentEvent.getTier().getLeague();
        boolean teamsEnabled = league != null && Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
            && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
            && league.getTeamBName() != null && !league.getTeamBName().isEmpty();

        if (champTeamColumn != null) {
            champTeamColumn.setVisible(teamsEnabled);
        }

        List<Integer> leftTeamIds = new ArrayList<>();
        List<Integer> rightTeamIds = new ArrayList<>();
        List<Integer> splitTeamIds = new ArrayList<>();

        List<TeamMapping> allTeamsForCarType = getTeamsForCarType(carType);
        List<Integer> allConstructorIds = allTeamsForCarType.stream().map(TeamMapping::getTeamId).toList();

        if (teamsEnabled) {
            for (Integer teamId : allConstructorIds) {
                List<EventLineupEntry> teamEntries = lineup.stream().filter(e -> Objects.equals(e.getTeamId(), teamId)).toList();
                List<String> teams = new ArrayList<>();
                if (!teamEntries.isEmpty()) {
                    for (EventLineupEntry entry : teamEntries) {
                        String ct = entry.getChampionshipTeam() != null ? entry.getChampionshipTeam() : entry.getDriver().getChampionshipTeam();
                        if (ct != null) {
                            teams.add(ct);
                        }
                    }
                } else {
                    List<DriverMapping> regularDrivers = driverMappingRepository.findByTier(currentEvent.getTier()).stream()
                        .filter(m -> !m.isReserve() && Objects.equals(m.getTeamId(), teamId))
                        .toList();
                    for (DriverMapping dm : regularDrivers) {
                        if (dm.getChampionshipTeam() != null) {
                            teams.add(dm.getChampionshipTeam());
                        }
                    }
                }

                boolean hasA = teams.contains("A");
                boolean hasB = teams.contains("B");

                if (hasA && hasB) {
                    splitTeamIds.add(teamId);
                } else if (hasA) {
                    leftTeamIds.add(teamId);
                } else if (hasB) {
                    rightTeamIds.add(teamId);
                } else {
                    // Default fallback
                    if ("F1 26".equals(carType)) {
                        if (List.of(484, 478, 480, 483, 479).contains(teamId)) {
                            leftTeamIds.add(teamId);
                        } else if (List.of(477, 476, 481, 482, 485).contains(teamId)) {
                            rightTeamIds.add(teamId);
                        } else {
                            splitTeamIds.add(teamId);
                        }
                    } else {
                        if (List.of(8, 2, 4, 7, 3).contains(teamId)) {
                            leftTeamIds.add(teamId);
                        } else {
                            rightTeamIds.add(teamId);
                        }
                    }
                }
            }
        } else {
            if ("F1 26".equals(carType)) {
                leftTeamIds = List.of(484, 478, 480, 483, 479); // McLaren, Red Bull, Aston, Haas, Williams
                rightTeamIds = List.of(477, 476, 481, 482, 485); // Ferrari, Mercedes, Alpine, RB, Audi
                splitTeamIds = List.of(486); // Cadillac
            } else {
                leftTeamIds = List.of(8, 2, 4, 7, 3); // McLaren, Red Bull, Aston, Haas, Williams
                rightTeamIds = List.of(1, 0, 5, 6, 9); // Ferrari, Mercedes, Alpine, RB, Sauber
            }
        }

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
        if (teamsEnabled) {
            Div leftHeader = new Div(new Span(league.getTeamAName().toUpperCase()));
            leftHeader.addClassName("lineup-column-header");
            leftCol.add(leftHeader);
        }
        for (Integer teamId : leftTeamIds) {
            leftCol.add(createTeamCard(teamId, carType, teamAssignments, teamsEnabled, splitTeamIds));
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

        for (Integer teamId : splitTeamIds) {
            centerCol.add(createTeamCard(teamId, carType, teamAssignments, teamsEnabled, splitTeamIds));
        }

        grid.add(centerCol);

        Div rightCol = new Div();
        rightCol.addClassName("lineup-column");
        if (teamsEnabled) {
            Div rightHeader = new Div(new Span(league.getTeamBName().toUpperCase()));
            rightHeader.addClassName("lineup-column-header");
            rightCol.add(rightHeader);
        }
        for (Integer teamId : rightTeamIds) {
            rightCol.add(createTeamCard(teamId, carType, teamAssignments, teamsEnabled, splitTeamIds));
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

        Span watermark = new Span("made by https://racingleague.jabapage.be");
        watermark.addClassName("poster-watermark");
        poster.add(watermark);

        posterWrapper.add(poster);

        Button downloadBtn = new Button("Download Lineup Image");
        downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        downloadBtn.getStyle().set("margin-bottom", "15px");
        downloadBtn.addClickListener(e -> {
            getElement().executeJs(
                "window.downloadInfographic('.lineup-poster', 'lineup_' + $0 + '.png', $1)",
                currentEvent.getEventName().toLowerCase().replace(" ", "_"),
                e.getSource().getElement()
            );
        });

        lineupContainer.add(new H2("Lineup Poster"), downloadBtn, posterWrapper);
    }

    private Div createTeamCard(Integer teamId, String carType, Map<Integer, List<EventLineupEntry>> teamAssignments, boolean teamsEnabled, List<Integer> splitTeamIds) {
        League league = currentEvent != null && currentEvent.getTier() != null ? currentEvent.getTier().getLeague() : null;

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
        boolean isSplit = teamsEnabled && splitTeamIds != null && splitTeamIds.contains(teamId);

        for (int i = 0; i < 2; i++) {
            Div slot = new Div();
            slot.addClassName("lineup-driver-slot");
            
            Div slotContent = new Div();
            slotContent.addClassName("lineup-driver-slot-content");
            
            Span driverName = new Span();
            driverName.addClassName("lineup-driver-name");

            EventLineupEntry matchedEntry = null;
            if (isSplit) {
                String targetTeam = (i == 0) ? "A" : "B";
                matchedEntry = entries.stream()
                        .filter(e -> {
                            String ct = e.getChampionshipTeam() != null ? e.getChampionshipTeam() : e.getDriver().getChampionshipTeam();
                            return targetTeam.equals(ct);
                        })
                        .findFirst()
                        .orElse(null);
            } else {
                if (i < entries.size()) {
                    matchedEntry = entries.get(i);
                }
            }

            if (matchedEntry != null) {
                DriverMapping dm = matchedEntry.getDriver();
                String dispName = dm.getOverriddenName() != null && !dm.getOverriddenName().isEmpty() 
                    ? dm.getOverriddenName() 
                    : dm.getTelemetryName();
                driverName.setText(dispName);
                slot.addClassName("assigned");
                
                slotContent.add(driverName);

                if (teamsEnabled && league != null) {
                    String ct = matchedEntry.getChampionshipTeam() != null ? matchedEntry.getChampionshipTeam() : dm.getChampionshipTeam();
                    if ("A".equals(ct)) {
                        Span badge = new Span(league.getTeamAName().toUpperCase());
                        badge.addClassName("lineup-driver-team-badge");
                        badge.addClassName("team-a");
                        slotContent.add(badge);
                    } else if ("B".equals(ct)) {
                        Span badge = new Span(league.getTeamBName().toUpperCase());
                        badge.addClassName("lineup-driver-team-badge");
                        badge.addClassName("team-b");
                        slotContent.add(badge);
                    }
                }
            } else {
                driverName.setText("VACANT");
                slot.addClassName("vacant");
                slotContent.add(driverName);
            }
            slot.add(slotContent);
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
        driverCombo.setItems(driverMappingRepository.findByTier(currentEvent.getTier()).stream()
                .sorted(Comparator.comparing(m -> m.getOverriddenName() != null ? m.getOverriddenName() : m.getTelemetryName()))
                .collect(Collectors.toList()));
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

        League league = currentEvent.getTier().getLeague();
        boolean teamsEnabled = league != null && Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
            && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
            && league.getTeamBName() != null && !league.getTeamBName().isEmpty();

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

        Span note = new Span();
        note.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        driverCombo.addValueChangeListener(e -> {
            DriverMapping dm = e.getValue();
            if (dm == null) {
                teamCombo.clear();
                teamCombo.setReadOnly(false);
                champTeamCombo.setValue("None");
                champTeamCombo.setReadOnly(false);
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
                        
                        if (dm.getChampionshipTeam() != null) {
                            champTeamCombo.setValue(dm.getChampionshipTeam());
                            champTeamCombo.setReadOnly(true);
                        } else {
                            champTeamCombo.setValue("None");
                            champTeamCombo.setReadOnly(false);
                        }
                        
                        note.setText("Regular driver auto-assigned to their primary team.");
                        return;
                    }
                }
                teamCombo.setReadOnly(false);
                teamCombo.setItems(allTeams);
                if (dm.getChampionshipTeam() != null) {
                    champTeamCombo.setValue(dm.getChampionshipTeam());
                    champTeamCombo.setReadOnly(true);
                } else {
                    champTeamCombo.setValue("None");
                    champTeamCombo.setReadOnly(false);
                }
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
                champTeamCombo.setReadOnly(false);
                champTeamCombo.setValue("None");
                note.setText("Reserve driver - select from remaining teams with open seats.");
            }
        });

        teamCombo.setItemLabelGenerator(TeamMapping::getTeamName);

        VerticalLayout dialogLayout = new VerticalLayout(driverLayout, teamCombo, champTeamCombo, note);
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
            if (teamsEnabled && !"None".equals(champTeamCombo.getValue())) {
                ele.setChampionshipTeam(champTeamCombo.getValue());
            } else {
                ele.setChampionshipTeam(null);
            }

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
            
            java.util.List<DriverMapping> tierDrivers = driverMappingRepository.findByTier(currentEvent.getTier());
            java.util.List<DriverMapping> lineupDrivers = eventLineupEntryRepository.findByEvent(currentEvent).stream()
                    .map(EventLineupEntry::getDriver)
                    .toList();
            java.util.Set<DriverMapping> allDrivers = new java.util.LinkedHashSet<>(tierDrivers);
            allDrivers.addAll(lineupDrivers);

            parentCombo.setItems(allDrivers.stream()
                    .sorted(Comparator.comparing(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty() ? m.getOverriddenName() : m.getTelemetryName()))
                    .collect(Collectors.toList()));
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

        champTeamColumn = lineupGrid.addColumn(entry -> {
            League lg = (currentEvent != null && currentEvent.getTier() != null) ? currentEvent.getTier().getLeague() : null;
            String ct = entry.getChampionshipTeam() != null ? entry.getChampionshipTeam() : (entry.getDriver() != null ? entry.getDriver().getChampionshipTeam() : null);
            if ("A".equals(ct)) {
                return lg != null && lg.getTeamAName() != null ? lg.getTeamAName() : "Team A";
            } else if ("B".equals(ct)) {
                return lg != null && lg.getTeamBName() != null ? lg.getTeamBName() : "Team B";
            }
            return "None";
        }).setHeader("Championship Team");

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
            case "youtube" -> "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"width:12px; height:12px; display:inline-block; vertical-align:middle;\">" +
                    "<path d=\"M23.498 6.163a3.003 3.003 0 0 0-2.11-2.108C19.53 3.5 12 3.5 12 3.5s-7.53 0-9.388.555A3.003 3.003 0 0 0 .502 6.163C0 8.07 0 12 0 12s0 3.93.502 5.837a3.003 3.003 0 0 0 2.11 2.108C4.47 20.5 12 20.5 12 20.5s7.53 0 9.388-.555a3.003 3.003 0 0 0 2.11-2.108C24 15.93 24 12 24 12s0-3.93-.502-5.837zM9.545 15.568V8.432L15.818 12l-6.273 3.568z\"/></svg>";
            case "tiktok" -> "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"width:12px; height:12px; display:inline-block; vertical-align:middle;\">" +
                    "<path d=\"M12.53.086c.3-.01.597.026.887.106.198.055.39.155.556.294.137.116.24.27.3.44.027.08.04.16.046.244.032 1.637.7 3.12 1.83 4.2 1.05.998 2.45 1.59 3.98 1.66.27.014.54.004.805-.03v3.25c-1.12.016-2.22-.276-3.19-.844-.82-.48-1.5-1.16-1.98-1.98v6.78c.005.89-.164 1.77-.5 2.6-.58 1.41-1.63 2.59-2.98 3.32a8.88 8.88 0 0 1-5.18.91c-1.5-.12-2.93-.72-4.1-1.7a9.14 9.14 0 0 1-2.8-5.38 8.89 8.89 0 0 1 .91-5.18c.73-1.35 1.91-2.4 3.32-2.98 1.13-.47 2.35-.61 3.56-.41v3.29c-.6-.07-1.22.01-1.78.24-.7.29-1.28.82-1.64 1.5-.56.98-.56 2.2 0 3.18.36.68.94 1.21 1.64 1.5.82.34 1.74.34 2.56 0 .7-.29 1.28-.82 1.64-1.5.23-.56.31-1.18.24-1.78V0l3.29.086z\"/></svg>";
            case "x" -> "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"width:12px; height:12px; display:inline-block; vertical-align:middle;\">" +
                    "<path d=\"M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z\"/></svg>";
            case "instagram" -> "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" style=\"width:12px; height:12px; display:inline-block; vertical-align:middle;\">" +
                    "<rect x=\"2\" y=\"2\" width=\"20\" height=\"20\" rx=\"5\" ry=\"5\"></rect>" +
                    "<path d=\"M16 11.37A4 4 0 1 1 12.63 8 4 4 0 0 1 16 11.37z\"></path>" +
                    "<line x1=\"17.5\" y1=\"6.5\" x2=\"17.51\" y2=\"6.5\"></line></svg>";
            case "twitch" -> "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"width:12px; height:12px; display:inline-block; vertical-align:middle;\">" +
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
                    DriverMapping mapping = findMappingForDriverResult(dr, mappings, currentEvent.getTier());
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
                if (entry.getKey().getChampionshipTeam() != null) {
                    ele.setChampionshipTeam(entry.getKey().getChampionshipTeam());
                }
                eventLineupEntryRepository.save(ele);
            }
            
            updateLineupContent();
            Notification.show("Lineup updated with real participants", 3000, Notification.Position.TOP_CENTER);
        });
        dialog.open();
    }

    private DriverMapping findMappingForDriverResult(DriverResult result, List<DriverMapping> mappings, Tier tier) {
        if (result.getTelemetryName() == null || result.getRaceNumber() == null || result.getDriverId() == null) {
            return null;
        }
        DriverMapping bestMatch = null;
        for (DriverMapping m : mappings) {
            if (Objects.equals(m.getTelemetryName(), result.getTelemetryName())
                    && Objects.equals(m.getRaceNumber(), result.getRaceNumber())
                    && Objects.equals(m.getDriverId(), result.getDriverId())
                    && Objects.equals(m.getCountry(), result.getCountry())) {
                if (tier != null && m.getTier() != null && Objects.equals(m.getTier().getId(), tier.getId())) {
                    return m;
                }
                if (bestMatch == null) {
                    bestMatch = m;
                } else if (bestMatch.getTier() != null && m.getTier() == null) {
                    bestMatch = m;
                }
            }
        }
        return bestMatch;
    }

    private List<TeamMapping> getTeamsForCarType(String carType) {
        List<TeamMapping> teams = teamMappingRepository.findByCarType(carType);
        if ("F1 26".equals(carType)) {
            teams = teams.stream().filter(t -> t.getTeamId() != null && t.getTeamId() >= 400).toList();
        }
        return teams;
    }

    private Div createBasePoster(String titleText, Div bodyContent, String posterClass) {
        League league = currentEvent.getTier().getLeague();

        Div posterWrapper = new Div();
        posterWrapper.addClassName("results-poster-wrapper");

        Div poster = new Div();
        poster.addClassName("results-poster");
        poster.addClassName(posterClass);

        if (league.getLogoBackgroundColor() != null && !league.getLogoBackgroundColor().isEmpty()) {
            poster.getStyle().set("background", "linear-gradient(135deg, " + league.getLogoBackgroundColor() + " 0%, #090a0f 100%)");
        }

        String accentColor = league.getAccentColor() != null && !league.getAccentColor().isEmpty()
                ? league.getAccentColor()
                : "#eef30d";
        poster.getStyle().set("--results-accent-color", accentColor);

        // Ribbons
        Div topLeftRibbon = new Div(new Span(league.getName()));
        topLeftRibbon.addClassName("results-ribbon");
        topLeftRibbon.addClassName("results-ribbon-top-left");

        Div bottomRightRibbon = new Div(new Span(league.getName()));
        bottomRightRibbon.addClassName("results-ribbon");
        bottomRightRibbon.addClassName("results-ribbon-bottom-right");

        poster.add(topLeftRibbon, bottomRightRibbon);

        // Header
        Div header = new Div();
        header.addClassName("results-poster-header");
        H4 subtitle = new H4(currentEvent.getTier().getName().toUpperCase());
        subtitle.addClassName("results-poster-title-mini");

        H1 title = new H1(titleText.toUpperCase());
        title.addClassName("results-poster-title-main");
        header.add(subtitle, title);
        poster.add(header);

        // Body
        poster.add(bodyContent);

        // Footer
        Div footer = new Div();
        footer.addClassName("results-poster-footer");
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

        // Watermark
        Span watermark = new Span("made by https://racingleague.jabapage.be");
        watermark.addClassName("poster-watermark");
        poster.add(watermark);

        posterWrapper.add(poster);
        return posterWrapper;
    }

    private String getTrackNameForEvent() {
        if (currentEvent == null || currentEvent.getTrackId() == null) return "Unknown Track";
        try {
            int trackIdInt = Integer.parseInt(currentEvent.getTrackId());
            return TelemetryProcessingService.TRACK_NAMES.getOrDefault(trackIdInt, "Unknown Track");
        } catch (NumberFormatException e) {
            return currentEvent.getTrackId();
        }
    }

    private static class StintSegment {
        final int laps;
        final int endLap;
        final String compound;

        StintSegment(int laps, int endLap, String compound) {
            this.laps = laps;
            this.endLap = endLap;
            this.compound = compound;
        }
    }

    private Div createPitStopsPoster(SessionResult session, List<DriverResult> driverResults) {
        String carType = getCarTypeForEvent();
        Div body = new Div();
        body.addClassName("results-poster-body");

        Div rowsContainer = new Div();
        rowsContainer.addClassName("pitstops-rows-container");

        // Calculate max laps in the session
        int maxLaps = driverResults.stream()
                .mapToInt(dr -> dr.getNumLaps() != null ? dr.getNumLaps() : 0)
                .max().orElse(50);
        if (maxLaps <= 0) maxLaps = 50;

        for (int i = 0; i < driverResults.size(); i++) {
            DriverResult dr = driverResults.get(i);
            Div row = new Div();
            row.addClassName("pitstops-row");

            Span posSpan = new Span(String.valueOf(i + 1));
            posSpan.addClassName("pitstops-row-pos");

            Span flagSpan = new Span(CountryProvider.getFlagByName(dr.getCountry()));
            flagSpan.addClassName("pitstops-row-flag");

            Span nameSpan = new Span(dr.getDriverName());
            nameSpan.addClassName("pitstops-row-name");

            Div timelineWrapper = new Div();
            timelineWrapper.addClassName("pitstops-timeline-wrapper");

            Div timeline = new Div();
            timeline.addClassName("pitstops-timeline");

            // Extract stints
            List<TyreStint> stints = dr.getTyreStints().stream()
                    .sorted(java.util.Comparator.comparingInt(TyreStint::getStintOrder))
                    .toList();

            int drLaps = dr.getNumLaps() != null ? dr.getNumLaps() : 0;
            List<StintSegment> segments = new ArrayList<>();
            if (stints.isEmpty() && drLaps > 0) {
                segments.add(new StintSegment(drLaps, drLaps, "Dry"));
            } else {
                int currentEndLap = 0;
                for (TyreStint stint : stints) {
                    int laps = stint.getLaps();
                    currentEndLap = stint.getEndLap() != null ? stint.getEndLap() : (currentEndLap + laps);
                    String compound = TelemetryProcessingService.TYRE_COMPOUNDS.getOrDefault(stint.getTyreCompound(), "Dry");
                    segments.add(new StintSegment(laps, currentEndLap, compound));
                }
            }

            int activeLapsSum = 0;
            for (StintSegment seg : segments) {
                Div segmentDiv = new Div();
                segmentDiv.addClassName("pitstops-segment");
                segmentDiv.getStyle().set("flex", String.valueOf(seg.laps));

                // Tyre compound style
                switch (seg.compound) {
                    case "Soft" -> segmentDiv.addClassName("tyre-soft");
                    case "Medium" -> segmentDiv.addClassName("tyre-medium");
                    case "Hard" -> segmentDiv.addClassName("tyre-hard");
                    case "Inter" -> segmentDiv.addClassName("tyre-inter");
                    case "Wet" -> segmentDiv.addClassName("tyre-wet");
                    default -> segmentDiv.addClassName("tyre-unknown");
                }

                Span label = new Span(String.format("%02d", seg.endLap));
                label.addClassName("pitstops-lap-label");
                segmentDiv.add(label);

                timeline.add(segmentDiv);
                activeLapsSum += seg.laps;
            }

            // If retired/incomplete, add a spacer
            if (activeLapsSum < maxLaps) {
                Div spacerDiv = new Div();
                spacerDiv.addClassName("pitstops-segment-spacer");
                spacerDiv.getStyle().set("flex", String.valueOf(maxLaps - activeLapsSum));
                timeline.add(spacerDiv);
            }

            timelineWrapper.add(timeline);
            row.add(posSpan, flagSpan, nameSpan, timelineWrapper);
            rowsContainer.add(row);
        }

        body.add(rowsContainer);

        String trackName = getTrackNameForEvent();
        String title = trackName + " Pit Stops";

        return createBasePoster(title, body, "pitstops-poster");
    }

    private Div createResultsPoster(SessionResult session, List<DriverResult> driverResults) {
        String carType = getCarTypeForEvent();
        boolean isQualifying = session.getSessionType() >= 5 && session.getSessionType() <= 14;

        Div body = new Div();
        body.addClassName("results-poster-body");

        // Podium container
        Div podiumContainer = new Div();
        podiumContainer.addClassName("results-podium-container");

        DriverResult first = driverResults.size() > 0 ? driverResults.get(0) : null;
        DriverResult second = driverResults.size() > 1 ? driverResults.get(1) : null;
        DriverResult third = driverResults.size() > 2 ? driverResults.get(2) : null;

        podiumContainer.add(createPodiumStep(second, 2, carType, isQualifying));
        podiumContainer.add(createPodiumStep(first, 1, carType, isQualifying));
        podiumContainer.add(createPodiumStep(third, 3, carType, isQualifying));
        body.add(podiumContainer);

        // List container
        Div listContainer = new Div();
        listContainer.addClassName("results-list-container");

        if (driverResults.size() > 3) {
            List<DriverResult> remaining = driverResults.subList(3, driverResults.size());
            int mid = (remaining.size() + 1) / 2;

            Div leftCol = new Div();
            leftCol.addClassName("results-list-column");
            for (int i = 0; i < mid; i++) {
                leftCol.add(createListRow(remaining.get(i), i + 4, carType));
            }

            Div rightCol = new Div();
            rightCol.addClassName("results-list-column");
            for (int i = mid; i < remaining.size(); i++) {
                rightCol.add(createListRow(remaining.get(i), i + 4, carType));
            }

            listContainer.add(leftCol, rightCol);
        }
        body.add(listContainer);

        List<SessionResult> sessions = getOrderedSessions();
        String sessionName = getDynamicSessionName(session.getSessionType(), sessions.stream().map(SessionResult::getSessionType).toList());
        String title = currentEvent.getEventName() + " " + sessionName;

        return createBasePoster(title, body, "results-poster");
    }

    private DriverResult findDriverResultByName(Event event, String driverName) {
        if (event == null || driverName == null) return null;
        for (SessionResult sr : event.getSessionResults()) {
            for (DriverResult dr : sr.getDriverResults()) {
                if (driverName.equals(dr.getDriverName())) {
                    return dr;
                }
            }
        }
        return null;
    }

    private Div createPacePoster(SessionResult session, List<RacePaceStats> stats) {
        String carType = getCarTypeForEvent();
        Div body = new Div();
        body.addClassName("results-poster-body");

        Div podiumContainer = new Div();
        podiumContainer.addClassName("results-podium-container");

        RacePaceStats first = stats.size() > 0 ? stats.get(0) : null;
        RacePaceStats second = stats.size() > 1 ? stats.get(1) : null;
        RacePaceStats third = stats.size() > 2 ? stats.get(2) : null;
        double bestPace = first != null ? first.getPureRacePace() : 0.0;

        podiumContainer.add(createPacePodiumStep(second, 2, carType, bestPace));
        podiumContainer.add(createPacePodiumStep(first, 1, carType, bestPace));
        podiumContainer.add(createPacePodiumStep(third, 3, carType, bestPace));
        body.add(podiumContainer);

        Div listContainer = new Div();
        listContainer.addClassName("results-list-container");

        if (stats.size() > 3) {
            List<RacePaceStats> remaining = stats.subList(3, stats.size());
            int mid = (remaining.size() + 1) / 2;

            Div leftCol = new Div();
            leftCol.addClassName("results-list-column");
            for (int i = 0; i < mid; i++) {
                leftCol.add(createPaceListRow(remaining.get(i), i + 4, carType, bestPace));
            }

            Div rightCol = new Div();
            rightCol.addClassName("results-list-column");
            for (int i = mid; i < remaining.size(); i++) {
                rightCol.add(createPaceListRow(remaining.get(i), i + 4, carType, bestPace));
            }

            listContainer.add(leftCol, rightCol);
        }
        body.add(listContainer);

        List<SessionResult> sessions = getOrderedSessions();
        String sessionName = getDynamicSessionName(session.getSessionType(), sessions.stream().map(SessionResult::getSessionType).toList());
        String title = currentEvent.getEventName() + " " + sessionName + " - Pure Pace";

        return createBasePoster(title, body, "pace-poster");
    }

    private Div createPacePodiumStep(RacePaceStats stat, int place, String carType, double bestPace) {
        Div stepContainer = new Div();
        stepContainer.addClassName("podium-step-container");
        stepContainer.addClassName("place-" + place);

        if (stat != null) {
            DriverResult dr = findDriverResultByName(currentEvent, stat.getDriverName());
            Integer teamId = dr != null ? dr.getTeamId() : null;
            stepContainer.getStyle().set("--team-color", getTeamColor(teamId, carType));

            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");

            Span name = new Span(stat.getDriverName());
            name.addClassName("podium-driver-name");

            Span team = new Span(stat.getTeamName() != null ? stat.getTeamName() : "");
            team.addClassName("podium-team-name");

            String timeText = "";
            if (place == 1) {
                timeText = formatLapTime((float) stat.getPureRacePace());
            } else {
                timeText = String.format("+%.3fs", stat.getPureRacePace() - bestPace);
            }
            Span time = new Span(timeText);
            time.addClassName("podium-time");

            driverInfo.add(name, team, time);
            stepContainer.add(driverInfo);
        } else {
            stepContainer.getStyle().set("--team-color", "#3e404b");
            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");
            Span name = new Span("VACANT");
            name.addClassName("podium-driver-name");
            name.getStyle().set("color", "#3e404b").set("font-style", "italic");
            driverInfo.add(name);
            stepContainer.add(driverInfo);
        }

        Div step = new Div();
        step.addClassName("podium-step");
        step.addClassName("step-" + place);
        Span number = new Span(String.valueOf(place));
        number.addClassName("podium-number");
        step.add(number);
        stepContainer.add(step);

        return stepContainer;
    }

    private Div createPaceListRow(RacePaceStats stat, int pos, String carType, double bestPace) {
        Div row = new Div();
        row.addClassName("results-list-row");
        DriverResult dr = findDriverResultByName(currentEvent, stat.getDriverName());
        Integer teamId = dr != null ? dr.getTeamId() : null;
        row.getStyle().set("--team-color", getTeamColor(teamId, carType));

        Span posSpan = new Span(String.valueOf(pos));
        posSpan.addClassName("results-list-pos");

        Div colorBar = new Div();
        colorBar.addClassName("results-list-color-bar");

        Span nameSpan = new Span(stat.getDriverName());
        nameSpan.addClassName("results-list-name");

        Span teamSpan = new Span(stat.getTeamName() != null ? stat.getTeamName() : "");
        teamSpan.addClassName("results-list-team");

        // Performance badges (S1, S2, S3)
        Div perfCols = new Div();
        perfCols.addClassName("results-list-perf-cols");
        perfCols.add(createPosterPerformanceBadge(stat.getS1Performance()));
        perfCols.add(createPosterPerformanceBadge(stat.getS2Performance()));
        perfCols.add(createPosterPerformanceBadge(stat.getS3Performance()));

        String timeText = stat.getPureRacePace() == bestPace
                ? formatLapTime((float) stat.getPureRacePace())
                : String.format("+%.3fs", stat.getPureRacePace() - bestPace);
        Span timeSpan = new Span(timeText);
        timeSpan.addClassName("results-list-time");

        row.add(posSpan, colorBar, nameSpan, teamSpan, perfCols, timeSpan);
        return row;
    }

    private Span createPosterPerformanceBadge(double perf) {
        Span span = new Span(String.format("%.1f", perf));
        span.addClassName("poster-perf-badge");
        if (perf >= 9.0) {
            span.addClassName("poster-perf-purple");
        } else if (perf >= 7.0) {
            span.addClassName("poster-perf-green");
        } else if (perf >= 4.0) {
            span.addClassName("poster-perf-yellow");
        } else {
            span.addClassName("poster-perf-red");
        }
        return span;
    }

    private Div createConsistencyPoster(SessionResult session, List<ConsistencyStats> stats) {
        String carType = getCarTypeForEvent();
        Div body = new Div();
        body.addClassName("results-poster-body");

        Div podiumContainer = new Div();
        podiumContainer.addClassName("results-podium-container");

        ConsistencyStats first = stats.size() > 0 ? stats.get(0) : null;
        ConsistencyStats second = stats.size() > 1 ? stats.get(1) : null;
        ConsistencyStats third = stats.size() > 2 ? stats.get(2) : null;

        podiumContainer.add(createConsistencyPodiumStep(second, 2, carType));
        podiumContainer.add(createConsistencyPodiumStep(first, 1, carType));
        podiumContainer.add(createConsistencyPodiumStep(third, 3, carType));
        body.add(podiumContainer);

        Div listContainer = new Div();
        listContainer.addClassName("results-list-container");

        if (stats.size() > 3) {
            List<ConsistencyStats> remaining = stats.subList(3, stats.size());
            int mid = (remaining.size() + 1) / 2;

            Div leftCol = new Div();
            leftCol.addClassName("results-list-column");
            for (int i = 0; i < mid; i++) {
                leftCol.add(createConsistencyListRow(remaining.get(i), i + 4, carType));
            }

            Div rightCol = new Div();
            rightCol.addClassName("results-list-column");
            for (int i = mid; i < remaining.size(); i++) {
                rightCol.add(createConsistencyListRow(remaining.get(i), i + 4, carType));
            }

            listContainer.add(leftCol, rightCol);
        }
        body.add(listContainer);

        List<SessionResult> sessions = getOrderedSessions();
        String sessionName = getDynamicSessionName(session.getSessionType(), sessions.stream().map(SessionResult::getSessionType).toList());
        String title = currentEvent.getEventName() + " " + sessionName + " - Consistency";

        return createBasePoster(title, body, "consistency-poster");
    }

    private Div createConsistencyPodiumStep(ConsistencyStats stat, int place, String carType) {
        Div stepContainer = new Div();
        stepContainer.addClassName("podium-step-container");
        stepContainer.addClassName("place-" + place);

        if (stat != null) {
            DriverResult dr = findDriverResultByName(currentEvent, stat.getDriverName());
            Integer teamId = dr != null ? dr.getTeamId() : null;
            stepContainer.getStyle().set("--team-color", getTeamColor(teamId, carType));

            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");

            Span name = new Span(stat.getDriverName());
            name.addClassName("podium-driver-name");

            Span team = new Span(stat.getTeamName() != null ? stat.getTeamName() : "");
            team.addClassName("podium-team-name");

            Span time = new Span(String.format("Rating: %.1f", stat.getRating()));
            time.addClassName("podium-time");

            driverInfo.add(name, team, time);
            stepContainer.add(driverInfo);
        } else {
            stepContainer.getStyle().set("--team-color", "#3e404b");
            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");
            Span name = new Span("VACANT");
            name.addClassName("podium-driver-name");
            name.getStyle().set("color", "#3e404b").set("font-style", "italic");
            driverInfo.add(name);
            stepContainer.add(driverInfo);
        }

        Div step = new Div();
        step.addClassName("podium-step");
        step.addClassName("step-" + place);
        Span number = new Span(String.valueOf(place));
        number.addClassName("podium-number");
        step.add(number);
        stepContainer.add(step);

        return stepContainer;
    }

    private Div createConsistencyListRow(ConsistencyStats stat, int pos, String carType) {
        Div row = new Div();
        row.addClassName("results-list-row");
        DriverResult dr = findDriverResultByName(currentEvent, stat.getDriverName());
        Integer teamId = dr != null ? dr.getTeamId() : null;
        row.getStyle().set("--team-color", getTeamColor(teamId, carType));

        Span posSpan = new Span(String.valueOf(pos));
        posSpan.addClassName("results-list-pos");

        Div colorBar = new Div();
        colorBar.addClassName("results-list-color-bar");

        Span nameSpan = new Span(stat.getDriverName());
        nameSpan.addClassName("results-list-name");

        Span teamSpan = new Span(stat.getTeamName() != null ? stat.getTeamName() : "");
        teamSpan.addClassName("results-list-team");

        Span ratingSpan = new Span(String.format("%.1f", stat.getRating()));
        ratingSpan.addClassName("results-list-rating");

        Span diffSpan = new Span(String.format("%.3fs", stat.getAvgDiff()));
        diffSpan.addClassName("results-list-diff");

        row.add(posSpan, colorBar, nameSpan, teamSpan, ratingSpan, diffSpan);
        return row;
    }

    private Div createStintsPoster(SessionResult session, List<LongestStintStats> stats) {
        String carType = getCarTypeForEvent();
        Div body = new Div();
        body.addClassName("results-poster-body");

        Div podiumContainer = new Div();
        podiumContainer.addClassName("results-podium-container");

        LongestStintStats first = stats.size() > 0 ? stats.get(0) : null;
        LongestStintStats second = stats.size() > 1 ? stats.get(1) : null;
        LongestStintStats third = stats.size() > 2 ? stats.get(2) : null;

        podiumContainer.add(createStintsPodiumStep(second, 2, carType));
        podiumContainer.add(createStintsPodiumStep(first, 1, carType));
        podiumContainer.add(createStintsPodiumStep(third, 3, carType));
        body.add(podiumContainer);

        Div listContainer = new Div();
        listContainer.addClassName("results-list-container");

        if (stats.size() > 3) {
            List<LongestStintStats> remaining = stats.subList(3, stats.size());
            int mid = (remaining.size() + 1) / 2;

            Div leftCol = new Div();
            leftCol.addClassName("results-list-column");
            for (int i = 0; i < mid; i++) {
                leftCol.add(createStintsListRow(remaining.get(i), i + 4, carType));
            }

            Div rightCol = new Div();
            rightCol.addClassName("results-list-column");
            for (int i = mid; i < remaining.size(); i++) {
                rightCol.add(createStintsListRow(remaining.get(i), i + 4, carType));
            }

            listContainer.add(leftCol, rightCol);
        }
        body.add(listContainer);

        List<SessionResult> sessions = getOrderedSessions();
        String sessionName = getDynamicSessionName(session.getSessionType(), sessions.stream().map(SessionResult::getSessionType).toList());
        String title = currentEvent.getEventName() + " " + sessionName + " - Tyre Stints";

        return createBasePoster(title, body, "stints-poster");
    }

    private Div createStintsPodiumStep(LongestStintStats stat, int place, String carType) {
        Div stepContainer = new Div();
        stepContainer.addClassName("podium-step-container");
        stepContainer.addClassName("place-" + place);

        if (stat != null) {
            DriverResult dr = findDriverResultByName(currentEvent, stat.getDriverName());
            Integer teamId = dr != null ? dr.getTeamId() : null;
            stepContainer.getStyle().set("--team-color", getTeamColor(teamId, carType));

            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");

            Span name = new Span(stat.getDriverName());
            name.addClassName("podium-driver-name");

            Span team = new Span(stat.getTeamName() != null ? stat.getTeamName() : "");
            team.addClassName("podium-team-name");

            Span time = new Span(stat.getLaps() + " Laps (" + stat.getTyreCompound() + ")");
            time.addClassName("podium-time");

            driverInfo.add(name, team, time);
            stepContainer.add(driverInfo);
        } else {
            stepContainer.getStyle().set("--team-color", "#3e404b");
            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");
            Span name = new Span("VACANT");
            name.addClassName("podium-driver-name");
            name.getStyle().set("color", "#3e404b").set("font-style", "italic");
            driverInfo.add(name);
            stepContainer.add(driverInfo);
        }

        Div step = new Div();
        step.addClassName("podium-step");
        step.addClassName("step-" + place);
        Span number = new Span(String.valueOf(place));
        number.addClassName("podium-number");
        step.add(number);
        stepContainer.add(step);

        return stepContainer;
    }

    private Div createStintsListRow(LongestStintStats stat, int pos, String carType) {
        Div row = new Div();
        row.addClassName("results-list-row");
        DriverResult dr = findDriverResultByName(currentEvent, stat.getDriverName());
        Integer teamId = dr != null ? dr.getTeamId() : null;
        row.getStyle().set("--team-color", getTeamColor(teamId, carType));

        Span posSpan = new Span(String.valueOf(pos));
        posSpan.addClassName("results-list-pos");

        Div colorBar = new Div();
        colorBar.addClassName("results-list-color-bar");

        Span nameSpan = new Span(stat.getDriverName());
        nameSpan.addClassName("results-list-name");

        Span teamSpan = new Span(stat.getTeamName() != null ? stat.getTeamName() : "");
        teamSpan.addClassName("results-list-team");

        Div tyreStintContainer = new Div();
        tyreStintContainer.addClassName("results-list-tyre-stint");

        Span tyreBadge = new Span();
        tyreBadge.addClassName("tyre-badge");
        tyreBadge.setText(stat.getTyreCompound().substring(0, 1));
        switch (stat.getTyreCompound()) {
            case "Soft" -> tyreBadge.addClassName("tyre-soft");
            case "Medium" -> tyreBadge.addClassName("tyre-medium");
            case "Hard" -> tyreBadge.addClassName("tyre-hard");
            case "Inter" -> tyreBadge.addClassName("tyre-inter");
            case "Wet" -> tyreBadge.addClassName("tyre-wet");
            default -> tyreBadge.addClassName("tyre-unknown");
        }
        tyreBadge.getStyle().set("width", "16px").set("height", "16px").set("font-size", "9px").set("line-height", "16px").set("margin-right", "8px");

        Span lapsSpan = new Span(stat.getLaps() + " laps");
        lapsSpan.getStyle().set("font-size", "12px").set("font-weight", "800");

        tyreStintContainer.add(tyreBadge, lapsSpan);

        Span timeSpan = new Span(formatLapTime((float) stat.getAvgLapTime()));
        timeSpan.addClassName("results-list-time");

        row.add(posSpan, colorBar, nameSpan, teamSpan, tyreStintContainer, timeSpan);
        return row;
    }

    private Div createPodiumStep(DriverResult dr, int place, String carType, boolean isQualifying) {
        Div stepContainer = new Div();
        stepContainer.addClassName("podium-step-container");
        stepContainer.addClassName("place-" + place);

        if (dr != null) {
            stepContainer.getStyle().set("--team-color", getTeamColor(dr.getTeamId(), carType));

            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");

            Span name = new Span(dr.getDriverName());
            name.addClassName("podium-driver-name");

            Span team = new Span(dr.getTeamName() != null ? dr.getTeamName() : "");
            team.addClassName("podium-team-name");

            String timeText = "";
            if (place == 1) {
                if (isQualifying) {
                    timeText = formatLapTime(dr.getBestLapTime() != null ? dr.getBestLapTime() : 0.0f);
                } else {
                    float totalTime = dr.getTotalTime() != null ? dr.getTotalTime().floatValue() : 0.0f;
                    if (totalTime > 0) {
                        timeText = formatLapTime(totalTime);
                    } else {
                        timeText = formatLapTime(dr.getBestLapTime() != null ? dr.getBestLapTime() : 0.0f);
                    }
                }
            } else {
                timeText = dr.getGapToLeader() != null && !dr.getGapToLeader().isEmpty() ? dr.getGapToLeader() : "";
                if (timeText.isEmpty() && dr.getBestLapTime() != null && dr.getBestLapTime() > 0) {
                    timeText = formatLapTime(dr.getBestLapTime());
                }
            }

            Integer status = dr.getResultStatus();
            if (status != null) {
                if (status == 4) name.setText(name.getText() + " (DNF)");
                else if (status == 5) name.setText(name.getText() + " (DSQ)");
                else if (status == 6) name.setText(name.getText() + " (NC)");
                else if (status == 7) name.setText(name.getText() + " (RET)");
            }

            Span time = new Span(timeText);
            time.addClassName("podium-time");

            driverInfo.add(name, team, time);
            stepContainer.add(driverInfo);
        } else {
            stepContainer.getStyle().set("--team-color", "#3e404b");
            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");
            Span name = new Span("VACANT");
            name.addClassName("podium-driver-name");
            name.getStyle().set("color", "#3e404b");
            name.getStyle().set("font-style", "italic");
            driverInfo.add(name);
            stepContainer.add(driverInfo);
        }

        Div step = new Div();
        step.addClassName("podium-step");
        step.addClassName("step-" + place);

        Span number = new Span(String.valueOf(place));
        number.addClassName("podium-number");
        step.add(number);

        stepContainer.add(step);
        return stepContainer;
    }

    private Div createListRow(DriverResult dr, int pos, String carType) {
        Div row = new Div();
        row.addClassName("results-list-row");
        row.getStyle().set("--team-color", getTeamColor(dr.getTeamId(), carType));

        Span posSpan = new Span(String.valueOf(pos));
        posSpan.addClassName("results-list-pos");

        Div colorBar = new Div();
        colorBar.addClassName("results-list-color-bar");

        String nameText = dr.getDriverName();
        Integer status = dr.getResultStatus();
        if (status != null) {
            if (status == 4) nameText += " (DNF)";
            else if (status == 5) nameText += " (DSQ)";
            else if (status == 6) nameText += " (NC)";
            else if (status == 7) nameText += " (RET)";
        }
        Span nameSpan = new Span(nameText);
        nameSpan.addClassName("results-list-name");

        Span teamSpan = new Span(dr.getTeamName() != null ? dr.getTeamName() : "");
        teamSpan.addClassName("results-list-team");

        String timeText = dr.getGapToLeader() != null && !dr.getGapToLeader().isEmpty() ? dr.getGapToLeader() : "";
        if (timeText.isEmpty() && dr.getBestLapTime() != null && dr.getBestLapTime() > 0) {
            timeText = formatLapTime(dr.getBestLapTime());
        }
        Span timeSpan = new Span(timeText);
        timeSpan.addClassName("results-list-time");

        row.add(posSpan, colorBar, nameSpan, teamSpan, timeSpan);
        return row;
    }

    public static String getDownloadInfographicJs() {
        return "window.downloadInfographic = function(selector, filename, buttonEl) {\n" +
                "    if (!selector || !filename) return;\n" +
                "    const originalText = buttonEl ? buttonEl.innerHTML : '';\n" +
                "    const originalDisabled = buttonEl ? buttonEl.disabled : false;\n" +
                "    if (buttonEl) {\n" +
                "        buttonEl.disabled = true;\n" +
                "        buttonEl.classList.add('infographic-loading-btn');\n" +
                "        const spinnerSvg = `<svg class=\"btn-spinner\" viewBox=\"0 0 50 50\" style=\"width: 16px; height: 16px; margin-right: 8px; animation: spin 1s linear infinite; display: inline-block; vertical-align: middle;\"><circle cx=\"25\" cy=\"25\" r=\"20\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"5\" stroke-dasharray=\"80, 200\" stroke-linecap=\"round\"></circle></svg>`;\n" +
                "        buttonEl.innerHTML = spinnerSvg + ' Generating...';\n" +
                "    }\n" +
                "    let container = document.getElementById('infographic-toast-container');\n" +
                "    if (!container) {\n" +
                "        container = document.createElement('div');\n" +
                "        container.id = 'infographic-toast-container';\n" +
                "        Object.assign(container.style, {\n" +
                "            position: 'fixed',\n" +
                "            top: '20px',\n" +
                "            right: '20px',\n" +
                "            zIndex: '99999',\n" +
                "            display: 'flex',\n" +
                "            flexDirection: 'column',\n" +
                "            gap: '10px',\n" +
                "            pointerEvents: 'none'\n" +
                "        });\n" +
                "        document.body.appendChild(container);\n" +
                "    }\n" +
                "    const toastId = 'toast-' + Math.random().toString(36).substr(2, 9);\n" +
                "    const toast = document.createElement('div');\n" +
                "    toast.id = toastId;\n" +
                "    toast.className = 'infographic-toast infographic-toast-loading';\n" +
                "    Object.assign(toast.style, {\n" +
                "        background: 'rgba(18, 19, 24, 0.85)',\n" +
                "        backdropFilter: 'blur(12px)',\n" +
                "        borderLeft: '4px solid #ffd700',\n" +
                "        borderTop: '1px solid rgba(255,255,255,0.08)',\n" +
                "        borderRight: '1px solid rgba(255,255,255,0.04)',\n" +
                "        borderBottom: '1px solid rgba(255,255,255,0.04)',\n" +
                "        padding: '16px 20px',\n" +
                "        borderRadius: '8px',\n" +
                "        boxShadow: '0 10px 30px rgba(0,0,0,0.5)',\n" +
                "        color: 'white',\n" +
                "        fontFamily: 'system-ui, -apple-system, sans-serif',\n" +
                "        minWidth: '280px',\n" +
                "        display: 'flex',\n" +
                "        alignItems: 'center',\n" +
                "        gap: '15px',\n" +
                "        opacity: '0',\n" +
                "        transform: 'translateX(50px)',\n" +
                "        transition: 'all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275)',\n" +
                "        pointerEvents: 'auto'\n" +
                "    });\n" +
                "    toast.innerHTML = `\n" +
                "        <div class=\"toast-spinner-wrapper\" style=\"position: relative; width: 28px; height: 28px; flex-shrink: 0;\">\n" +
                "            <svg viewBox=\"0 0 50 50\" style=\"width: 100%; height: 100%; animation: spin 1s linear infinite;\">\n" +
                "                <circle cx=\"25\" cy=\"25\" r=\"20\" fill=\"none\" stroke=\"#ffd700\" stroke-width=\"4\" stroke-dasharray=\"80, 200\" stroke-linecap=\"round\"></circle>\n" +
                "            </svg>\n" +
                "        </div>\n" +
                "        <div class=\"toast-text-wrapper\" style=\"flex-grow: 1;\">\n" +
                "            <div style=\"font-weight: 800; font-size: 14px; text-transform: uppercase; letter-spacing: 0.5px;\">Generating Image</div>\n" +
                "            <div style=\"font-size: 12px; color: #a0a5b5; margin-top: 2px;\">Converting telemetry info...</div>\n" +
                "        </div>\n" +
                "    `;\n" +
                "    container.appendChild(toast);\n" +
                "    toast.offsetHeight;\n" +
                "    toast.style.opacity = '1';\n" +
                "    toast.style.transform = 'translateX(0)';\n" +
                "    const generate = () => {\n" +
                "        const el = document.querySelector(selector);\n" +
                "        if (!el) {\n" +
                "            updateToast(toast, 'Error', 'Infographic element not found.', '#ff3333');\n" +
                "            restoreButton();\n" +
                "            return;\n" +
                "        }\n" +
                "        const scale = (selector.includes('consistency') || selector.includes('stints') || selector.includes('pace')) ? 2 : 1.5;\n" +
                "        setTimeout(() => {\n" +
                "            html2canvas(el, { useCORS: true, backgroundColor: null, scale: scale }).then(canvas => {\n" +
                "                try {\n" +
                "                    const link = document.createElement('a');\n" +
                "                    link.download = filename;\n" +
                "                    link.href = canvas.toDataURL('image/png');\n" +
                "                    link.click();\n" +
                "                    updateToast(toast, 'Success', 'Infographic downloaded!', '#33ff33');\n" +
                "                    setTimeout(() => { dismissToast(toast); }, 2500);\n" +
                "                } catch (err) {\n" +
                "                    updateToast(toast, 'Error', 'Failed to save image.', '#ff3333');\n" +
                "                    setTimeout(() => { dismissToast(toast); }, 4000);\n" +
                "                }\n" +
                "                restoreButton();\n" +
                "            }).catch(err => {\n" +
                "                updateToast(toast, 'Error', 'Render failed: ' + err.message, '#ff3333');\n" +
                "                setTimeout(() => { dismissToast(toast); }, 4000);\n" +
                "                restoreButton();\n" +
                "            });\n" +
                "        }, 100);\n" +
                "    };\n" +
                "    const restoreButton = () => {\n" +
                "        if (buttonEl) {\n" +
                "            buttonEl.disabled = originalDisabled;\n" +
                "            buttonEl.innerHTML = originalText;\n" +
                "            buttonEl.classList.remove('infographic-loading-btn');\n" +
                "        }\n" +
                "    };\n" +
                "    const updateToast = (toastElement, title, message, color) => {\n" +
                "        toastElement.style.borderLeftColor = color;\n" +
                "        const iconSvg = title === 'Success'\n" +
                "            ? `<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"${color}\" stroke-width=\"3\" stroke-linecap=\"round\" stroke-linejoin=\"round\" style=\"width: 28px; height: 28px;\"><polyline points=\"20 6 9 17 4 12\"></polyline></svg>`\n" +
                "            : `<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"${color}\" stroke-width=\"3\" stroke-linecap=\"round\" stroke-linejoin=\"round\" style=\"width: 28px; height: 28px;\"><circle cx=\"12\" cy=\"12\" r=\"10\"></circle><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"></line><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"></line></svg>`;\n" +
                "        toastElement.innerHTML = `\n" +
                "            <div style=\"flex-shrink: 0; display: flex; align-items: center; justify-content: center;\">\n" +
                "                ${iconSvg}\n" +
                "            </div>\n" +
                "            <div class=\"toast-text-wrapper\" style=\"flex-grow: 1;\">\n" +
                "                <div style=\"font-weight: 800; font-size: 14px; text-transform: uppercase; letter-spacing: 0.5px; color: ${color};\">${title}</div>\n" +
                "                <div style=\"font-size: 12px; color: #a0a5b5; margin-top: 2px;\">${message}</div>\n" +
                "            </div>\n" +
                "        `;\n" +
                "    };\n" +
                "    const dismissToast = (toastElement) => {\n" +
                "        toastElement.style.opacity = '0';\n" +
                "        toastElement.style.transform = 'translateY(-20px) scale(0.9)';\n" +
                "        setTimeout(() => {\n" +
                "            if (toastElement.parentNode) {\n" +
                "                toastElement.parentNode.removeChild(toastElement);\n" +
                "            }\n" +
                "        }, 300);\n" +
                "    };\n" +
                "    if (!window.html2canvas) {\n" +
                "        const script = document.createElement('script');\n" +
                "        script.src = 'https://unpkg.com/html2canvas@1.4.1/dist/html2canvas.min.js';\n" +
                "        script.onload = generate;\n" +
                "        script.onerror = () => {\n" +
                "            updateToast(toast, 'Error', 'Failed to load rendering engine.', '#ff3333');\n" +
                "            restoreButton();\n" +
                "            setTimeout(() => { dismissToast(toast); }, 4000);\n" +
                "        };\n" +
                "        document.head.appendChild(script);\n" +
                "    } else {\n" +
                "        generate();\n" +
                "    }\n" +
                "};\n" +
                "if (!document.getElementById('infographic-toast-animation')) {\n" +
                "    const style = document.createElement('style');\n" +
                "    style.id = 'infographic-toast-animation';\n" +
                "    style.innerHTML = `@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`;\n" +
                "    document.head.appendChild(style);\n" +
                "}";
    }
}
