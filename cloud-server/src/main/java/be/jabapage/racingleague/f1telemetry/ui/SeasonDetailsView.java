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
    private final VerticalLayout driversLayout = new VerticalLayout();
    
    private final Button recalculateBtn = new Button("Recalculate Standings");
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

        Tabs tabs = new Tabs(new Tab("Race Weekends"), new Tab("Standings"), new Tab("Drivers"));
        tabs.addSelectedChangeListener(e -> {
            String label = e.getSelectedTab().getLabel();
            eventsLayout.setVisible(label.equals("Race Weekends"));
            standingsLayout.setVisible(label.equals("Standings"));
            driversLayout.setVisible(label.equals("Drivers"));
        });

        eventsLayout.add(new H3("Race Weekends"), eventGrid);
        
        recalculateBtn.addClickListener(e -> {
            if (league != null) {
                telemetryProcessingService.recalculateStandings(league.getId());
                updateData();
                Notification.show("Standings recalculated!");
            }
        });
        
        standingsLayout.add(new HorizontalLayout(new H3("Driver Standings"), recalculateBtn), 
                driverGrid, new H3("Team Standings"), teamGrid);
        standingsLayout.setVisible(false);

        driversLayout.add(new H3("Driver Name Overrides"), 
                new Span("Drivers are automatically discovered when they join a session. Edit the 'Display Name' to override how they appear in the leaderboard and standings."), 
                mappingGrid);
        driversLayout.setVisible(false);

        add(header, tabs, eventsLayout, standingsLayout, driversLayout);
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
            Span name = new Span(ds.getDriverName());
            if (ds.isAi()) {
                Span badge = new Span("AI");
                badge.getElement().getThemeList().add("badge contrast small");
                badge.getStyle().set("margin-left", "var(--lumo-space-s)");
                return new HorizontalLayout(name, badge);
            }
            return name;
        }).setHeader("Driver");

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
        
        Grid.Column<DriverMapping> overrideColumn = mappingGrid.addColumn(DriverMapping::getOverriddenName).setHeader("Display Name");

        Binder<DriverMapping> binder = new Binder<>(DriverMapping.class);
        Editor<DriverMapping> editor = mappingGrid.getEditor();
        editor.setBinder(binder);
        editor.setBuffered(true);

        TextField overrideField = new TextField();
        overrideField.setWidthFull();
        binder.forField(overrideField).bind(DriverMapping::getOverriddenName, DriverMapping::setOverriddenName);
        overrideColumn.setEditorComponent(overrideField);

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
        
        HorizontalLayout actions = new HorizontalLayout(saveButton, cancelButton);
        actions.setPadding(false);
        mappingGrid.addComponentColumn(item -> {
            Button editButton = new Button("Edit");
            editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editButton.addClickListener(e -> {
                if (editor.isOpen()) editor.cancel();
                mappingGrid.getEditor().editItem(item);
            });
            editButton.setVisible(securityService.getAuthenticatedUser().isPresent());
            return editButton;
        }).setEditorComponent(actions);

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
