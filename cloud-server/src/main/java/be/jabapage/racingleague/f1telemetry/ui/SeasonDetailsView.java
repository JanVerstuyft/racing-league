package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.TeamStanding;
import be.jabapage.racingleague.f1telemetry.repository.DriverStandingRepository;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamStandingRepository;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.SessionPointConfig;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.SessionPointConfigRepository;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.data.binder.Binder;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;

@AnonymousAllowed
@PageTitle("Season Details | F1 Telemetry")
@Route(value = "season")
public class SeasonDetailsView extends VerticalLayout implements HasUrlParameter<Long> {

    private final LeagueRepository leagueRepository;
    private final EventRepository eventRepository;
    private final DriverStandingRepository driverStandingRepository;
    private final TeamStandingRepository teamStandingRepository;
    private final DriverMappingRepository driverMappingRepository;
    private final SessionPointConfigRepository sessionPointConfigRepository;
    private final SecurityService securityService;
    private final TelemetryProcessingService telemetryProcessingService;
    
    private League league;
    private final H2 seasonName = new H2();
    private final Checkbox hideAiCheckbox = new Checkbox("Hide AI Drivers");
    private final Checkbox showTyreWearCheckbox = new Checkbox("Show Tyre Wear on Live Leaderboard");
    private final Checkbox showErsCheckbox = new Checkbox("Show ERS on Live Leaderboard");
    private final Grid<Event> eventGrid = new Grid<>(Event.class, false);
    private final Grid<DriverStanding> driverGrid = new Grid<>(DriverStanding.class, false);
    private final Grid<TeamStanding> teamGrid = new Grid<>(TeamStanding.class, false);
    private final Grid<DriverMapping> mappingGrid = new Grid<>(DriverMapping.class, false);
    private final Grid<SessionPointConfig> pointsGrid = new Grid<>(SessionPointConfig.class, false);

    private final VerticalLayout eventsLayout = new VerticalLayout();
    private final VerticalLayout standingsLayout = new VerticalLayout();
    private final VerticalLayout driverStandingsContent = new VerticalLayout();
    private final VerticalLayout teamStandingsContent = new VerticalLayout();
    private final VerticalLayout driversLayout = new VerticalLayout();
    private final VerticalLayout pointsLayout = new VerticalLayout();
    private final VerticalLayout settingsLayout = new VerticalLayout();
    private final Button addManualDriverBtn = new Button("Add Manual Driver");
    private final Button deleteSelectedMappingsBtn = new Button("Delete Selected");
    
    // Points UI Components
    private final Tabs sessionTypeTabs = new Tabs();
    private final Button addSessionTypeBtn = new Button("Add Session Type");
    private final Button savePointsBtn = new Button("Save & Recalculate");
    private final Button deleteSessionBtn = new Button("Remove All Overrides for Session");
    private final com.vaadin.flow.component.textfield.IntegerField fastestLapPointsField = new com.vaadin.flow.component.textfield.IntegerField("Fastest Lap Bonus");
    private final com.vaadin.flow.component.textfield.IntegerField noPenaltyPointsField = new com.vaadin.flow.component.textfield.IntegerField("No Penalties Bonus");
    private Integer selectedSessionType = null;
    private final List<SessionPointConfig> currentEditingConfigs = new ArrayList<>();
    private boolean pointsChanged = false;
    
    private final Button recalculateBtn = new Button("Recalculate Standings");
    private final Button addManualWeekendBtn = new Button("Add Manual Weekend");
    private Grid.Column<Event> actionsColumn;
    private boolean isInitializing = false;

    public SeasonDetailsView(LeagueRepository leagueRepository, EventRepository eventRepository,
                             DriverStandingRepository driverStandingRepository,
                             TeamStandingRepository teamStandingRepository,
                             DriverMappingRepository driverMappingRepository,
                             SessionPointConfigRepository sessionPointConfigRepository,
                             TelemetryProcessingService telemetryProcessingService,
                             SecurityService securityService) {
        this.leagueRepository = leagueRepository;
        this.eventRepository = eventRepository;
        this.driverStandingRepository = driverStandingRepository;
        this.teamStandingRepository = teamStandingRepository;
        this.driverMappingRepository = driverMappingRepository;
        this.sessionPointConfigRepository = sessionPointConfigRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;

        setSizeFull();
        configureGrids();

        HorizontalLayout nav = new HorizontalLayout();
        if (securityService.getAuthenticatedUser().isPresent()) {
            nav.add(new RouterLink("All Seasons", SeasonListView.class));
        } else {
            nav.add(new RouterLink("Login", LoginView.class));
        }
        nav.add(new RouterLink("Documentation", DocumentationView.class));
        nav.setSpacing(true);

        HorizontalLayout header = new HorizontalLayout(seasonName);
        header.setAlignItems(Alignment.BASELINE);
        header.setSpacing(true);

        // Top level tabs
        Tabs mainTabs = new Tabs(new Tab("Race Weekends"), new Tab("Standings"), new Tab("Drivers"), new Tab("Points"), new Tab("Settings"));
        mainTabs.addSelectedChangeListener(e -> {
            String label = e.getSelectedTab().getLabel();
            eventsLayout.setVisible(label.equals("Race Weekends"));
            standingsLayout.setVisible(label.equals("Standings"));
            driversLayout.setVisible(label.equals("Drivers"));
            pointsLayout.setVisible(label.equals("Points"));
            settingsLayout.setVisible(label.equals("Settings"));
        });

        eventsLayout.add(new HorizontalLayout(new H3("Race Weekends"), addManualWeekendBtn), eventGrid);

        // Points Layout
        pointsLayout.add(new H3("Points Configuration Overrides"));
        pointsLayout.add(new Span("By default, the standard F1 point system is used for Race sessions. Use this section to overrule points for any session type."));
        
        HorizontalLayout pointsHeader = new HorizontalLayout(sessionTypeTabs, addSessionTypeBtn);
        pointsHeader.setAlignItems(Alignment.END);
        pointsHeader.setWidthFull();
        pointsHeader.setFlexGrow(1, sessionTypeTabs);

        pointsLayout.add(pointsHeader);
        
        pointsGrid.setWidthFull();
        
        VerticalLayout bonusSidebar = new VerticalLayout();
        bonusSidebar.setWidthFull();
        bonusSidebar.setPadding(false);
        bonusSidebar.setSpacing(true);
        
        bonusSidebar.add(new H3("Session Bonuses"));
        Span bonusNote = new Span("General bonuses awarded only to drivers who finish in a point-scoring position (standard for Races).");
        bonusNote.getStyle().set("font-size", "var(--lumo-font-size-s)");
        bonusNote.getStyle().set("color", "var(--lumo-secondary-text-color)");
        bonusSidebar.add(bonusNote, fastestLapPointsField, noPenaltyPointsField);
        
        fastestLapPointsField.setStepButtonsVisible(true);
        fastestLapPointsField.setWidthFull();
        noPenaltyPointsField.setStepButtonsVisible(true);
        noPenaltyPointsField.setWidthFull();
        
        fastestLapPointsField.addValueChangeListener(e -> pointsChanged = true);
        noPenaltyPointsField.addValueChangeListener(e -> pointsChanged = true);

        HorizontalLayout gridAndBonus = new HorizontalLayout(pointsGrid, bonusSidebar);
        gridAndBonus.setWidthFull();
        gridAndBonus.setAlignItems(Alignment.START);
        gridAndBonus.setFlexGrow(1, pointsGrid);
        gridAndBonus.setFlexGrow(1, bonusSidebar);
        pointsLayout.add(gridAndBonus);
        
        HorizontalLayout pointsActions = new HorizontalLayout(savePointsBtn, deleteSessionBtn);
        pointsLayout.add(pointsActions);
        pointsLayout.setVisible(false);

        sessionTypeTabs.addSelectedChangeListener(e -> {
            if (e.getSelectedTab() == null) return;
            
            Integer type = (Integer) ComponentUtil.getData(e.getSelectedTab(), Integer.class);
            loadPointsForSessionType(type);
        });

        savePointsBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        savePointsBtn.addClickListener(e -> saveCurrentPoints());
        
        deleteSessionBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        deleteSessionBtn.addClickListener(e -> deleteCurrentSessionPoints());

        addSessionTypeBtn.addClickListener(e -> showAddSessionTypeDialog());

        // Settings Layout
        settingsLayout.add(new H3("Season Settings"));
        settingsLayout.add(hideAiCheckbox, showTyreWearCheckbox, showErsCheckbox);
        settingsLayout.setVisible(false);

        hideAiCheckbox.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setHideAi(e.getValue());
                leagueRepository.save(league);
                telemetryProcessingService.refreshHideAiSetting(league.getId());
                updateData();
                Notification.show("AI visibility updated", 3000, Notification.Position.TOP_CENTER);
            }
        });

        showTyreWearCheckbox.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setShowTyreWear(e.getValue());
                leagueRepository.save(league);
                Notification.show("Tyre wear visibility updated", 3000, Notification.Position.TOP_CENTER);
            }
        });

        showErsCheckbox.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setShowErs(e.getValue());
                leagueRepository.save(league);
                Notification.show("ERS visibility updated", 3000, Notification.Position.TOP_CENTER);
            }
        });
        
        recalculateBtn.addClickListener(e -> {
            if (league != null) {
                telemetryProcessingService.recalculateStandings(league.getId());
                updateData();
                Notification.show("Standings recalculated!", 3000, Notification.Position.TOP_CENTER);
            }
        });

        addManualWeekendBtn.addClickListener(e -> {
            if (league == null) return;
            
            com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();
            dialog.setHeaderTitle("Add Manual Weekend");
            
            com.vaadin.flow.component.combobox.ComboBox<Integer> trackCombo = new com.vaadin.flow.component.combobox.ComboBox<>("Track");
            trackCombo.setItems(java.util.stream.IntStream.rangeClosed(0, 41).boxed().toList());
            trackCombo.setItemLabelGenerator(id -> TelemetryProcessingService.TRACK_NAMES.getOrDefault(id, "Track " + id));
            trackCombo.setWidthFull();

            TextField nameField = new TextField("Event Name (e.g. Belgian Grand Prix)");
            nameField.setWidthFull();

            trackCombo.addValueChangeListener(ev -> {
                if (ev.getValue() != null && (nameField.getValue() == null || nameField.getValue().isEmpty())) {
                    nameField.setValue(TelemetryProcessingService.TRACK_NAMES.getOrDefault(ev.getValue(), "") + " Grand Prix");
                }
            });

            VerticalLayout dialogLayout = new VerticalLayout(trackCombo, nameField);
            dialog.add(dialogLayout);

            Button saveBtn = new Button("Add", ev -> {
                if (trackCombo.getValue() == null || nameField.getValue().isEmpty()) {
                    Notification.show("Please fill in all fields", 3000, Notification.Position.TOP_CENTER);
                    return;
                }
                Event newEvent = new Event();
                newEvent.setLeague(league);
                newEvent.setTrackId(String.valueOf(trackCombo.getValue()));
                newEvent.setEventName(nameField.getValue());
                eventRepository.save(newEvent);
                updateData();
                dialog.close();
                Notification.show("Manual weekend added", 3000, Notification.Position.TOP_CENTER);
            });
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            
            Button cancelBtn = new Button("Cancel", ev -> dialog.close());
            dialog.getFooter().add(cancelBtn, saveBtn);
            dialog.open();
        });

        // Inner tabs for Standings
        Tabs standingsTabs = new Tabs(new Tab("Drivers"), new Tab("Teams"));
        standingsTabs.addSelectedChangeListener(e -> {
            String label = e.getSelectedTab().getLabel();
            driverStandingsContent.setVisible(label.equals("Drivers"));
            teamStandingsContent.setVisible(label.equals("Teams"));
        });

        driverGrid.setSelectionMode(Grid.SelectionMode.NONE);

        driverStandingsContent.add(new HorizontalLayout(new H3("Driver Standings"), recalculateBtn),
                driverGrid);
        driverStandingsContent.setPadding(false);        
        teamStandingsContent.add(new H3("Team Standings"), teamGrid);
        teamStandingsContent.setVisible(false);
        teamStandingsContent.setPadding(false);

        standingsLayout.add(standingsTabs, driverStandingsContent, teamStandingsContent);
        standingsLayout.setVisible(false);

        deleteSelectedMappingsBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteSelectedMappingsBtn.setEnabled(false);
        mappingGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        mappingGrid.addSelectionListener(e -> deleteSelectedMappingsBtn.setEnabled(!e.getAllSelectedItems().isEmpty()));

        deleteSelectedMappingsBtn.addClickListener(e -> {
            var selected = mappingGrid.getSelectedItems();
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Delete " + selected.size() + " Mappings?");
            dialog.setText("Are you sure you want to delete the selected driver mappings?");
            dialog.setCancelable(true);
            dialog.setConfirmText("Delete");
            dialog.setConfirmButtonTheme("error primary");
            dialog.addConfirmListener(ev -> {
                Notification deletingNote = new Notification("Deleting mappings...");
                deletingNote.setPosition(Notification.Position.TOP_CENTER);
                deletingNote.setDuration(0);
                deletingNote.open();
                try {
                    driverMappingRepository.deleteAll(selected);
                    updateData();
                    deletingNote.close();
                    Notification.show(selected.size() + " mappings deleted", 3000, Notification.Position.TOP_CENTER);
                } catch (Exception ex) {
                    deletingNote.close();
                    Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                }
            });
            dialog.open();
        });

        driversLayout.add(new HorizontalLayout(new H3("Driver Name Overrides"), addManualDriverBtn, deleteSelectedMappingsBtn), 
                new Span("Drivers are automatically discovered when they join a session. Edit the 'Display Name' to override how they appear in the leaderboard and standings."), 
                mappingGrid);
        driversLayout.setVisible(false);

        addManualDriverBtn.addClickListener(e -> {
            if (league == null) return;
            com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();
            dialog.setHeaderTitle("Add Manual Driver");

            TextField nameField = new TextField("Display Name");
            nameField.setWidthFull();

            TextField telemetryNameField = new TextField("Telemetry Name (Optional)");
            telemetryNameField.setWidthFull();

            com.vaadin.flow.component.textfield.IntegerField raceNumField = new com.vaadin.flow.component.textfield.IntegerField("Race #");
            raceNumField.setWidthFull();

            VerticalLayout dialogLayout = new VerticalLayout(nameField, telemetryNameField, raceNumField);
            dialog.add(dialogLayout);

            Button saveBtn = new Button("Add", ev -> {
                if (nameField.getValue().isEmpty()) {
                    Notification.show("Please enter a name", 3000, Notification.Position.TOP_CENTER);
                    return;
                }
                DriverMapping mapping = new DriverMapping();
                mapping.setLeague(league);
                mapping.setOverriddenName(nameField.getValue());
                mapping.setTelemetryName(telemetryNameField.getValue().isEmpty() ? nameField.getValue() : telemetryNameField.getValue());
                mapping.setRaceNumber(raceNumField.getValue() != null ? raceNumField.getValue() : 0);
                mapping.setDriverId(255); // Use 255 for manual drivers
                driverMappingRepository.save(mapping);
                updateData();
                dialog.close();
                Notification.show("Manual driver added", 3000, Notification.Position.TOP_CENTER);
            });
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
            dialog.open();
        });

        add(nav, header, mainTabs, eventsLayout, standingsLayout, driversLayout, pointsLayout, settingsLayout);
    }

    private void configureGrids() {
        pointsGrid.addColumn(SessionPointConfig::getPosition).setHeader("Pos").setWidth("70px").setFlexGrow(0);
        pointsGrid.addComponentColumn(config -> {
            com.vaadin.flow.component.textfield.IntegerField field = new com.vaadin.flow.component.textfield.IntegerField();
            field.setValue(config.getPoints());
            field.setStepButtonsVisible(true);
            field.addValueChangeListener(e -> {
                config.setPoints(e.getValue() != null ? e.getValue() : 0);
                pointsChanged = true;
            });
            field.setWidthFull();
            return field;
        }).setHeader("Points Awarded");
        pointsGrid.setSelectionMode(Grid.SelectionMode.NONE);

        eventGrid.addColumn(Event::getEventName).setHeader("Event");
        eventGrid.addColumn(event -> {
            return event.getSessionResults().stream()
                    .map(SessionResult::getSessionType)
                    .map(type -> TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(type, "S" + type))
                    .distinct()
                    .collect(Collectors.joining(", "));
        }).setHeader("Sessions");

        actionsColumn = eventGrid.addComponentColumn(event -> {
            HorizontalLayout actions = new HorizontalLayout();
            RouterLink resultsLink = new RouterLink("Results", EventResultsView.class, event.getId());
            actions.add(resultsLink);

            if (securityService.getAuthenticatedUser().isPresent()) {
                Button deleteBtn = new Button("Delete", e -> {
                    ConfirmDialog dialog = new ConfirmDialog();
                    dialog.setHeader("Delete Weekend?");
                    dialog.setText("Are you sure you want to delete the weekend '" + event.getEventName() + "'? Standings will be automatically recalculated.");
                    dialog.setCancelable(true);
                    dialog.setConfirmText("Delete");
                    dialog.setConfirmButtonTheme("error primary");
                    dialog.addConfirmListener(ev -> {
                        Notification deletingNote = new Notification("Deleting weekend...");
                        deletingNote.setPosition(Notification.Position.TOP_CENTER);
                        deletingNote.setDuration(0);
                        deletingNote.open();
                        try {
                            eventRepository.delete(event);
                            telemetryProcessingService.recalculateStandings(league.getId());
                            updateData();
                            deletingNote.close();
                            Notification.show("Weekend deleted and standings recalculated", 3000, Notification.Position.TOP_CENTER);
                        } catch (Exception ex) {
                            deletingNote.close();
                            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                        }
                    });                    dialog.open();
                });
                deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
                actions.add(deleteBtn);
            }

            actions.setAlignItems(FlexComponent.Alignment.CENTER);
            return actions;
        }).setHeader("Actions");

        driverGrid.addComponentColumn(ds -> {
            HorizontalLayout nameLayout = new HorizontalLayout();
            nameLayout.setAlignItems(Alignment.CENTER);
            nameLayout.setSpacing(false);

            if (ds.getRaceNumber() != null && ds.getRaceNumber() > 0) {
                Span raceNum = new Span("#" + ds.getRaceNumber());
                raceNum.getStyle().set("color", "var(--lumo-secondary-text-color)");
                raceNum.getStyle().set("font-size", "0.8em");
                raceNum.getStyle().set("margin-right", "var(--lumo-space-s)");
                nameLayout.add(raceNum);
            }

            Span name = new Span(ds.getDriverName());
            nameLayout.add(name);

            if (ds.isAi()) {
                Span badge = new Span("AI");
                badge.getElement().getThemeList().add("badge contrast small");
                badge.getStyle().set("margin-left", "var(--lumo-space-s)");
                nameLayout.add(badge);
            }
            return nameLayout;
        }).setHeader("Driver").setSortable(true).setComparator(DriverStanding::getDriverName);

        driverGrid.addColumn(DriverStanding::getTeamName).setHeader("Team");

        driverGrid.addColumn(ds -> ds.getPoints() != null ? ds.getPoints() : 0).setHeader("Points").setSortable(true);
        driverGrid.addColumn(ds -> ds.getWins() != null ? ds.getWins() : 0).setHeader("Wins");

        teamGrid.addColumn(TeamStanding::getTeamName).setHeader("Team");
        teamGrid.addColumn(ts -> ts.getPoints() != null ? ts.getPoints() : 0).setHeader("Points").setSortable(true);

        configureMappingGrid();
    }

    private void configureMappingGrid() {
        mappingGrid.addColumn(DriverMapping::getTelemetryName).setHeader("Telemetry Name");
        mappingGrid.addColumn(DriverMapping::getRaceNumber).setHeader("Race #");
        mappingGrid.addColumn(DriverMapping::getDriverId).setHeader("Driver ID");
        
        Grid.Column<DriverMapping> reserveColumn = mappingGrid.addComponentColumn(item -> {
            Checkbox cb = new Checkbox(item.isReserve());
            cb.setReadOnly(true);
            return cb;
        }).setHeader("Reserve");

        Grid.Column<DriverMapping> overrideColumn = mappingGrid.addColumn(DriverMapping::getOverriddenName).setHeader("Display Name");

        Binder<DriverMapping> binder = new Binder<>(DriverMapping.class);
        Editor<DriverMapping> editor = mappingGrid.getEditor();
        editor.setBinder(binder);
        editor.setBuffered(true);

        TextField overrideField = new TextField();
        overrideField.setWidthFull();
        binder.forField(overrideField).bind(DriverMapping::getOverriddenName, DriverMapping::setOverriddenName);
        overrideColumn.setEditorComponent(overrideField);

        Checkbox reserveField = new Checkbox();
        binder.forField(reserveField).bind(DriverMapping::getReserve, DriverMapping::setReserve);
        reserveColumn.setEditorComponent(reserveField);

        Button saveButton = new Button("Save", e -> {
            try {
                DriverMapping item = editor.getItem();
                editor.save();
                driverMappingRepository.save(item);
                telemetryProcessingService.refreshDriverMappings(league.getId());
                telemetryProcessingService.recalculateStandings(league.getId());
                Notification.show("Driver name and standings updated!", 3000, Notification.Position.TOP_CENTER);
                updateData();
            } finally {
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        
        Button cancelButton = new Button("Cancel", e -> editor.cancel());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        
        mappingGrid.addComponentColumn(item -> {
            HorizontalLayout actions = new HorizontalLayout();
            
            Button editButton = new Button("Edit");
            editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editButton.addClickListener(e -> {
                if (editor.isOpen()) editor.cancel();
                mappingGrid.getEditor().editItem(item);
            });
            
            Button deleteBtn = new Button("Delete", e -> {
                ConfirmDialog dialog = new ConfirmDialog();
                dialog.setHeader("Delete Driver Mapping?");
                dialog.setText("Are you sure you want to delete the mapping for '" + item.getTelemetryName() + "'?");
                dialog.setCancelable(true);
                dialog.setConfirmText("Delete");
                dialog.setConfirmButtonTheme("error primary");
                dialog.addConfirmListener(ev -> {
                    Notification deletingNote = new Notification("Deleting...");
                    deletingNote.setPosition(Notification.Position.TOP_CENTER);
                    deletingNote.setDuration(0);
                    deletingNote.open();
                    try {
                        driverMappingRepository.delete(item);
                        updateData();
                        deletingNote.close();
                        Notification.show("Driver mapping deleted", 3000, Notification.Position.TOP_CENTER);
                    } catch (Exception ex) {
                        deletingNote.close();
                        Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                    }
                });
                dialog.open();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            actions.add(editButton, deleteBtn);
            actions.setVisible(securityService.getAuthenticatedUser().isPresent());
            return actions;
        }).setEditorComponent(new HorizontalLayout(saveButton, cancelButton));

        mappingGrid.setItems(java.util.Collections.emptyList());
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        isInitializing = true;
        try {
            league = leagueRepository.findById(parameter).orElseThrow();
            seasonName.setText("Season: " + league.getName());
            hideAiCheckbox.setValue(league.isHideAi());
            showTyreWearCheckbox.setValue(league.isShowTyreWear());
            showErsCheckbox.setValue(league.isShowErs());
            updateData();
            
            // Hide admin-only features if not logged in
            boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
            recalculateBtn.setVisible(loggedIn);
            addManualWeekendBtn.setVisible(loggedIn);
            addManualDriverBtn.setVisible(loggedIn);
            deleteSelectedMappingsBtn.setVisible(loggedIn);
            addSessionTypeBtn.setVisible(loggedIn);
            savePointsBtn.setVisible(loggedIn);
            deleteSessionBtn.setVisible(loggedIn);
            hideAiCheckbox.setVisible(loggedIn);
            showTyreWearCheckbox.setVisible(loggedIn);
            showErsCheckbox.setVisible(loggedIn);
        } finally {
            isInitializing = false;
        }
    }

    private void updateData() {
        if (league == null) return;

        eventGrid.setItems(eventRepository.findByLeague(league));
        
        List<DriverStanding> standings = driverStandingRepository.findByLeague(league);
        if (league.isHideAi()) {
            standings = standings.stream().filter(s -> !s.isAi()).toList();
        }
        
        driverGrid.setItems(standings.stream()
                .sorted(Comparator.comparing((DriverStanding ds) -> ds.getPoints() != null ? ds.getPoints() : 0).reversed())
                .collect(java.util.stream.Collectors.toList()));
        
        teamGrid.setItems(teamStandingRepository.findByLeague(league).stream()
                .sorted(Comparator.comparing((TeamStanding ts) -> ts.getPoints() != null ? ts.getPoints() : 0).reversed())
                .collect(java.util.stream.Collectors.toList()));
        
        mappingGrid.setItems(driverMappingRepository.findByLeague(league).stream()
                .sorted(Comparator.comparing(m -> m.getOverriddenName() != null ? m.getOverriddenName() : m.getTelemetryName()))
                .collect(Collectors.toList()));

        refreshPointsTabs();
    }

    private void refreshPointsTabs() {
        sessionTypeTabs.removeAll();
        List<SessionPointConfig> allConfigs = sessionPointConfigRepository.findByLeague(league);
        java.util.Set<Integer> configuredTypes = allConfigs.stream()
                .map(SessionPointConfig::getSessionType)
                .collect(java.util.stream.Collectors.toSet());
        
        if (selectedSessionType != null) {
            configuredTypes.add(selectedSessionType);
        }
        
        configuredTypes.stream().sorted().forEach(type -> {
            Tab tab = new Tab(TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(type, "Session " + type));
            ComponentUtil.setData(tab, Integer.class, type);
            sessionTypeTabs.add(tab);
        });
        
        if (selectedSessionType != null) {
            for (int i = 0; i < sessionTypeTabs.getComponentCount(); i++) {
                Tab t = (Tab) sessionTypeTabs.getComponentAt(i);
                if (selectedSessionType.equals(ComponentUtil.getData(t, Integer.class))) {
                    sessionTypeTabs.setSelectedTab(t);
                    // loadPointsForSessionType(selectedSessionType); // Already called by dialog or listener
                    return;
                }
            }
        }
        
        if (sessionTypeTabs.getComponentCount() > 0) {
            sessionTypeTabs.setSelectedIndex(0);
            Tab selected = sessionTypeTabs.getSelectedTab();
            Integer type = (Integer) ComponentUtil.getData(selected, Integer.class);
            loadPointsForSessionType(type);
        } else {
            pointsGrid.setItems(java.util.Collections.emptyList());
            selectedSessionType = null;
        }
    }

    private void loadPointsForSessionType(Integer type) {
        selectedSessionType = type;
        List<SessionPointConfig> dbConfigs = sessionPointConfigRepository.findByLeague(league).stream()
                .filter(c -> c.getSessionType().equals(type))
                .sorted(Comparator.comparing(SessionPointConfig::getPosition))
                .toList();
        
        currentEditingConfigs.clear();
        if (dbConfigs.isEmpty()) {
            fastestLapPointsField.setValue(0);
            noPenaltyPointsField.setValue(0);
            // Generate defaults for 1-20
            boolean isRace = type >= 15 && type <= 17;
            int[] racePoints = {25, 18, 15, 12, 10, 8, 6, 4, 2, 1};
            for (int p = 1; p <= 20; p++) {
                SessionPointConfig c = new SessionPointConfig();
                c.setLeague(league);
                c.setSessionType(type);
                c.setPosition(p);
                c.setFastestLapPoints(0);
                c.setNoPenaltyPoints(0);
                int points = 0;
                if (isRace && p <= 10) points = racePoints[p-1];
                c.setPoints(points);
                currentEditingConfigs.add(c);
            }
        } else {
            SessionPointConfig first = dbConfigs.get(0);
            fastestLapPointsField.setValue(first.getFastestLapPoints() != null ? first.getFastestLapPoints() : 0);
            noPenaltyPointsField.setValue(first.getNoPenaltyPoints() != null ? first.getNoPenaltyPoints() : 0);
            
            // Use existing DB configs
            dbConfigs.forEach(db -> {
                SessionPointConfig clone = new SessionPointConfig();
                clone.setId(db.getId());
                clone.setLeague(league);
                clone.setSessionType(db.getSessionType());
                clone.setPosition(db.getPosition());
                clone.setPoints(db.getPoints());
                clone.setFastestLapPoints(db.getFastestLapPoints());
                clone.setNoPenaltyPoints(db.getNoPenaltyPoints());
                currentEditingConfigs.add(clone);
            });
            // Ensure 1-20 are present
            java.util.Set<Integer> existingPos = currentEditingConfigs.stream().map(SessionPointConfig::getPosition).collect(Collectors.toSet());
            for (int p = 1; p <= 20; p++) {
                if (!existingPos.contains(p)) {
                    SessionPointConfig c = new SessionPointConfig();
                    c.setLeague(league);
                    c.setSessionType(type);
                    c.setPosition(p);
                    c.setPoints(0);
                    c.setFastestLapPoints(fastestLapPointsField.getValue());
                    c.setNoPenaltyPoints(noPenaltyPointsField.getValue());
                    currentEditingConfigs.add(c);
                }
            }
            currentEditingConfigs.sort(Comparator.comparing(SessionPointConfig::getPosition));
        }
        
        pointsGrid.setItems(currentEditingConfigs);
        pointsChanged = false;
    }

    private void showAddSessionTypeDialog() {
        com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();
        dialog.setHeaderTitle("Add Points for Session Type");
        
        java.util.Set<Integer> configuredTypes = sessionPointConfigRepository.findByLeague(league).stream()
                .map(SessionPointConfig::getSessionType)
                .collect(Collectors.toSet());
        
        ComboBox<Integer> typeCombo = new ComboBox<>("Session Type");
        typeCombo.setItems(TelemetryProcessingService.SESSION_TYPE_NAMES.keySet().stream()
                .filter(t -> !configuredTypes.contains(t))
                .sorted().toList());
        typeCombo.setItemLabelGenerator(t -> TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(t, "Session " + t));
        typeCombo.setWidthFull();
        
        dialog.add(new VerticalLayout(new Span("Choose a session type to configure custom points. It will be initialized with defaults."), typeCombo));
        
        Button addBtn = new Button("Add", e -> {
            if (typeCombo.getValue() != null) {
                selectedSessionType = typeCombo.getValue();
                loadPointsForSessionType(selectedSessionType); // This populates defaults in currentEditingConfigs
                refreshPointsTabs(); // This will create the tab and select it
                dialog.close();
            }
        });
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), addBtn);
        dialog.open();
    }

    private void saveCurrentPoints() {
        if (selectedSessionType == null) return;
        
        List<SessionPointConfig> currentInDb = sessionPointConfigRepository.findByLeague(league).stream()
                .filter(c -> c.getSessionType().equals(selectedSessionType))
                .toList();

        if (currentInDb.isEmpty()) {
            Notification.show("Warning: No saved overrides exist for this session type yet. Saving current view.", 5000, Notification.Position.TOP_CENTER);
        }

        // Delete existing for this session type
        List<SessionPointConfig> toDelete = sessionPointConfigRepository.findByLeague(league).stream()
                .filter(c -> c.getSessionType().equals(selectedSessionType))
                .toList();
        sessionPointConfigRepository.deleteAll(toDelete);
        
        // Save current editing configs
        currentEditingConfigs.forEach(c -> {
            c.setId(null); // Ensure they are treated as new
            c.setFastestLapPoints(fastestLapPointsField.getValue() != null ? fastestLapPointsField.getValue() : 0);
            c.setNoPenaltyPoints(noPenaltyPointsField.getValue() != null ? noPenaltyPointsField.getValue() : 0);
        });
        sessionPointConfigRepository.saveAll(currentEditingConfigs);
        
        telemetryProcessingService.recalculateStandings(league.getId());
        updateData();
        Notification.show("Points saved and standings recalculated", 3000, Notification.Position.TOP_CENTER);
        pointsChanged = false;
    }

    private void deleteCurrentSessionPoints() {
        if (selectedSessionType == null) return;
        
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Remove All Overrides?");
        dialog.setText("Are you sure you want to remove all custom point overrides for " + 
                TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(selectedSessionType, "this session") + "? It will revert to defaults.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Remove");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            List<SessionPointConfig> toDelete = sessionPointConfigRepository.findByLeague(league).stream()
                    .filter(c -> c.getSessionType().equals(selectedSessionType))
                    .toList();
            sessionPointConfigRepository.deleteAll(toDelete);
            telemetryProcessingService.recalculateStandings(league.getId());
            selectedSessionType = null;
            updateData();
            Notification.show("Overrides removed", 3000, Notification.Position.TOP_CENTER);
        });
        dialog.open();
    }
}
