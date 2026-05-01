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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.data.binder.Binder;

import com.vaadin.flow.component.checkbox.Checkbox;

@AnonymousAllowed
@PageTitle("Season Details | F1 Telemetry")
@Route(value = "season")
public class SeasonDetailsView extends VerticalLayout implements HasUrlParameter<Long> {

    private final LeagueRepository leagueRepository;
    private final EventRepository eventRepository;
    private final DriverStandingRepository driverStandingRepository;
    private final TeamStandingRepository teamStandingRepository;
    private final DriverMappingRepository driverMappingRepository;
    private final SecurityService securityService;
    private final TelemetryProcessingService telemetryProcessingService;
    
    private League league;
    private final H2 seasonName = new H2();
    private final Checkbox hideAiCheckbox = new Checkbox("Hide AI Drivers");
    private final Grid<Event> eventGrid = new Grid<>(Event.class, false);
    private final Grid<DriverStanding> driverGrid = new Grid<>(DriverStanding.class, false);
    private final Grid<TeamStanding> teamGrid = new Grid<>(TeamStanding.class, false);
    private final Grid<DriverMapping> mappingGrid = new Grid<>(DriverMapping.class, false);

    private final VerticalLayout eventsLayout = new VerticalLayout();
    private final VerticalLayout standingsLayout = new VerticalLayout();
    private final VerticalLayout driverStandingsContent = new VerticalLayout();
    private final VerticalLayout teamStandingsContent = new VerticalLayout();
    private final VerticalLayout driversLayout = new VerticalLayout();
    private final Button addManualDriverBtn = new Button("Add Manual Driver");
    private final Button deleteSelectedStandingsBtn = new Button("Delete Selected");
    private final Button deleteSelectedMappingsBtn = new Button("Delete Selected");
    
    private final Button recalculateBtn = new Button("Recalculate Standings");
    private final Button addManualWeekendBtn = new Button("Add Manual Weekend");
    private Grid.Column<Event> actionsColumn;

    public SeasonDetailsView(LeagueRepository leagueRepository, EventRepository eventRepository,
                             DriverStandingRepository driverStandingRepository,
                             TeamStandingRepository teamStandingRepository,
                             DriverMappingRepository driverMappingRepository,
                             TelemetryProcessingService telemetryProcessingService,
                             SecurityService securityService) {
        this.leagueRepository = leagueRepository;
        this.eventRepository = eventRepository;
        this.driverStandingRepository = driverStandingRepository;
        this.teamStandingRepository = teamStandingRepository;
        this.driverMappingRepository = driverMappingRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;

        setSizeFull();
        configureGrids();

        HorizontalLayout nav = new HorizontalLayout(
                new RouterLink("All Seasons", SeasonListView.class),
                new RouterLink("Documentation", DocumentationView.class)
        );
        nav.setSpacing(true);

        hideAiCheckbox.addValueChangeListener(e -> {
            if (league != null) {
                league.setHideAi(e.getValue());
                leagueRepository.save(league);
                telemetryProcessingService.refreshHideAiSetting(league.getId());
                updateData();
                Notification.show("AI visibility updated");
            }
        });

        HorizontalLayout header = new HorizontalLayout(seasonName, hideAiCheckbox);
        header.setAlignItems(Alignment.BASELINE);
        header.setSpacing(true);

        // Top level tabs
        Tabs mainTabs = new Tabs(new Tab("Race Weekends"), new Tab("Standings"), new Tab("Drivers"));
        mainTabs.addSelectedChangeListener(e -> {
            String label = e.getSelectedTab().getLabel();
            eventsLayout.setVisible(label.equals("Race Weekends"));
            standingsLayout.setVisible(label.equals("Standings"));
            driversLayout.setVisible(label.equals("Drivers"));
        });

        eventsLayout.add(new HorizontalLayout(new H3("Race Weekends"), addManualWeekendBtn), eventGrid);
        
        recalculateBtn.addClickListener(e -> {
            if (league != null) {
                telemetryProcessingService.recalculateStandings(league.getId());
                updateData();
                Notification.show("Standings recalculated!");
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
                    Notification.show("Please fill in all fields");
                    return;
                }
                Event newEvent = new Event();
                newEvent.setLeague(league);
                newEvent.setTrackId(String.valueOf(trackCombo.getValue()));
                newEvent.setEventName(nameField.getValue());
                eventRepository.save(newEvent);
                updateData();
                dialog.close();
                Notification.show("Manual weekend added");
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

        deleteSelectedStandingsBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteSelectedStandingsBtn.setEnabled(false);
        driverGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        driverGrid.addSelectionListener(e -> deleteSelectedStandingsBtn.setEnabled(!e.getAllSelectedItems().isEmpty()));
        
        deleteSelectedStandingsBtn.addClickListener(e -> {
            var selected = driverGrid.getSelectedItems();
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Delete " + selected.size() + " Drivers?");
            dialog.setText("Are you sure you want to delete the selected drivers from the standings?");
            dialog.setCancelable(true);
            dialog.setConfirmText("Delete");
            dialog.setConfirmButtonTheme("error primary");
            dialog.addConfirmListener(ev -> {
                driverStandingRepository.deleteAll(selected);
                updateData();
                Notification.show(selected.size() + " drivers removed from standings");
            });
            dialog.open();
        });

        driverStandingsContent.add(new HorizontalLayout(new H3("Driver Standings"), recalculateBtn, deleteSelectedStandingsBtn), 
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
                driverMappingRepository.deleteAll(selected);
                updateData();
                Notification.show(selected.size() + " mappings deleted");
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
                    Notification.show("Please enter a name");
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
                Notification.show("Manual driver added");
            });
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
            dialog.open();
        });

        add(nav, header, mainTabs, eventsLayout, standingsLayout, driversLayout);
    }

    private void configureGrids() {
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
                        eventRepository.delete(event);
                        telemetryProcessingService.recalculateStandings(league.getId());
                        updateData();
                        Notification.show("Weekend deleted and standings recalculated");
                    });
                    dialog.open();
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

        driverGrid.addComponentColumn(ds -> {
            Button deleteBtn = new Button("Delete", e -> {
                ConfirmDialog dialog = new ConfirmDialog();
                dialog.setHeader("Delete Driver?");
                dialog.setText("Are you sure you want to delete '" + ds.getDriverName() + "' from the standings? This will remove them from the list, but their results in individual races will remain until you recalculate standings.");
                dialog.setCancelable(true);
                dialog.setConfirmText("Delete");
                dialog.setConfirmButtonTheme("error primary");
                dialog.addConfirmListener(ev -> {
                    driverStandingRepository.delete(ds);
                    updateData();
                    Notification.show("Driver removed from standings");
                });
                dialog.open();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteBtn.setVisible(securityService.getAuthenticatedUser().isPresent());
            return deleteBtn;
        }).setHeader("Actions");

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
            DriverMapping item = editor.getItem();
            editor.save();
            driverMappingRepository.save(item);
            telemetryProcessingService.refreshDriverMappings(league.getId());
            telemetryProcessingService.recalculateStandings(league.getId());
            Notification.show("Driver name and standings updated!");
            updateData();
        });
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
                    driverMappingRepository.delete(item);
                    updateData();
                    Notification.show("Driver mapping deleted");
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
        league = leagueRepository.findById(parameter).orElseThrow();
        seasonName.setText("Season: " + league.getName());
        hideAiCheckbox.setValue(league.isHideAi());
        updateData();
        
        // Hide admin-only features if not logged in
        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        recalculateBtn.setVisible(loggedIn);
        addManualWeekendBtn.setVisible(loggedIn);
        addManualDriverBtn.setVisible(loggedIn);
        deleteSelectedStandingsBtn.setVisible(loggedIn);
        deleteSelectedMappingsBtn.setVisible(loggedIn);
        hideAiCheckbox.setVisible(loggedIn);
    }

    private void updateData() {
        eventGrid.setItems(eventRepository.findByLeague(league));
        
        List<DriverStanding> standings = driverStandingRepository.findByLeague(league);
        if (league.isHideAi()) {
            standings = standings.stream().filter(s -> !s.isAi()).collect(Collectors.toList());
        }
        
        driverGrid.setItems(standings.stream()
                .sorted(Comparator.comparing((DriverStanding ds) -> ds.getPoints() != null ? ds.getPoints() : 0).reversed())
                .collect(java.util.stream.Collectors.toList()));
        
        teamGrid.setItems(teamStandingRepository.findByLeague(league).stream()
                .sorted(Comparator.comparing((TeamStanding ts) -> ts.getPoints() != null ? ts.getPoints() : 0).reversed())
                .collect(java.util.stream.Collectors.toList()));
        mappingGrid.setItems(driverMappingRepository.findByLeague(league).stream()
                .sorted(Comparator.comparing(DriverMapping::getTelemetryName))
                .collect(Collectors.toList()));
    }
}
