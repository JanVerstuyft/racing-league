package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.repository.*;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.ui.tabs.*;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@AnonymousAllowed
@PageTitle("Season Details | F1 Telemetry")
@Route(value = "season")
public class SeasonDetailsView extends VerticalLayout implements HasUrlParameter<Long> {

    private final LeagueRepository leagueRepository;
    private final SecurityService securityService;
    
    private League league;
    private final H2 seasonName = new H2();

    private final RaceWeekendsTab raceWeekendsTab;
    private final StandingsTab standingsTab;
    private final DriversTab driversTab;
    private final PointsTab pointsTab;
    private final SettingsTab settingsTab;

    public SeasonDetailsView(LeagueRepository leagueRepository, EventRepository eventRepository,
                             DriverStandingRepository driverStandingRepository,
                             TeamStandingRepository teamStandingRepository,
                             DriverMappingRepository driverMappingRepository,
                             SessionPointConfigRepository sessionPointConfigRepository,
                             TelemetryProcessingService telemetryProcessingService,
                             SecurityService securityService) {
        this.leagueRepository = leagueRepository;
        this.securityService = securityService;

        this.raceWeekendsTab = new RaceWeekendsTab(eventRepository, telemetryProcessingService, securityService);
        this.standingsTab = new StandingsTab(driverStandingRepository, teamStandingRepository, telemetryProcessingService, securityService);
        this.driversTab = new DriversTab(driverMappingRepository, telemetryProcessingService, securityService);
        this.pointsTab = new PointsTab(sessionPointConfigRepository, telemetryProcessingService, securityService);
        this.settingsTab = new SettingsTab(leagueRepository, telemetryProcessingService, securityService, this::updateData);

        setSizeFull();

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
            raceWeekendsTab.setVisible(label.equals("Race Weekends"));
            standingsTab.setVisible(label.equals("Standings"));
            driversTab.setVisible(label.equals("Drivers"));
            pointsTab.setVisible(label.equals("Points"));
            settingsTab.setVisible(label.equals("Settings"));
        });

        raceWeekendsTab.setVisible(true);
        standingsTab.setVisible(false);
        driversTab.setVisible(false);
        pointsTab.setVisible(false);
        settingsTab.setVisible(false);

        add(nav, header, mainTabs, raceWeekendsTab, standingsTab, driversTab, pointsTab, settingsTab);
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        league = leagueRepository.findById(parameter).orElseThrow();
        seasonName.setText("Season: " + league.getName());
        
        raceWeekendsTab.setLeague(league);
        standingsTab.setLeague(league);
        driversTab.setLeague(league);
        pointsTab.setLeague(league);
        settingsTab.setLeague(league);
    }

    private void updateData() {
        if (league == null) return;
        raceWeekendsTab.updateData();
        standingsTab.updateData();
        driversTab.updateData();
        pointsTab.updateData();
        settingsTab.updateData();
    }
}
