package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.repository.*;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.ui.tabs.*;
import com.vaadin.flow.component.combobox.ComboBox;
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
public class SeasonDetailsView extends VerticalLayout implements HasUrlParameter<Long>, BeforeEnterObserver {

    private final LeagueRepository leagueRepository;
    private final TierRepository tierRepository;
    private final SecurityService securityService;
    
    private League league;
    private Tier selectedTier;
    private Long targetTierId;
    private boolean initialLoad = true;
    private final H2 seasonName = new H2();
    private final ComboBox<Tier> tierComboBox = new ComboBox<>("Tier");
    private final RouterLink liveDashboardLink = new RouterLink("Live Dashboard", LeaderboardView.class, 0L);

    private final RaceWeekendsTab raceWeekendsTab;
    private final StandingsTab standingsTab;
    private final DriversTab driversTab;
    private final PointsTab pointsTab;
    private final SettingsTab settingsTab;
    private final TiersTab tiersTab;

    public SeasonDetailsView(LeagueRepository leagueRepository, TierRepository tierRepository, EventRepository eventRepository,
                             DriverStandingRepository driverStandingRepository,
                             TeamStandingRepository teamStandingRepository,
                             DriverMappingRepository driverMappingRepository,
                             SessionPointConfigRepository sessionPointConfigRepository,
                             TelemetryProcessingService telemetryProcessingService,
                             SecurityService securityService) {
        this.leagueRepository = leagueRepository;
        this.tierRepository = tierRepository;
        this.securityService = securityService;

        this.raceWeekendsTab = new RaceWeekendsTab(eventRepository, telemetryProcessingService, securityService);
        this.standingsTab = new StandingsTab(driverStandingRepository, teamStandingRepository, telemetryProcessingService, securityService);
        this.driversTab = new DriversTab(driverMappingRepository, tierRepository, telemetryProcessingService, securityService);
        this.pointsTab = new PointsTab(sessionPointConfigRepository, telemetryProcessingService, securityService, this::updateData);
        this.settingsTab = new SettingsTab(leagueRepository, telemetryProcessingService, securityService, this::updateData);
        this.tiersTab = new TiersTab(tierRepository, telemetryProcessingService, securityService, this::refreshTiers);

        setSizeFull();

        tierComboBox.setItemLabelGenerator(Tier::getName);
        tierComboBox.setPlaceholder("Season Overall");
        tierComboBox.setClearButtonVisible(true);
        tierComboBox.addValueChangeListener(e -> {
            selectedTier = e.getValue();
            updateLiveLink();
            updateData();
        });

        HorizontalLayout nav = new HorizontalLayout();
        if (securityService.getAuthenticatedUser().isPresent()) {
            nav.add(new RouterLink("All Seasons", SeasonListView.class));
        } else {
            nav.add(new RouterLink("Login", LoginView.class));
        }
        nav.add(new RouterLink("Documentation", DocumentationView.class));
        nav.setSpacing(true);

        liveDashboardLink.setVisible(false);
        HorizontalLayout header = new HorizontalLayout(seasonName, tierComboBox, liveDashboardLink);
        header.setAlignItems(Alignment.BASELINE);
        header.setSpacing(true);

        // Top level tabs
        Tabs mainTabs = new Tabs(new Tab("Race Weekends"), new Tab("Standings"), new Tab("Drivers"), new Tab("Points"), new Tab("Settings"), new Tab("Tiers"));
        mainTabs.addSelectedChangeListener(e -> {
            String label = e.getSelectedTab().getLabel();
            raceWeekendsTab.setVisible(label.equals("Race Weekends"));
            standingsTab.setVisible(label.equals("Standings"));
            driversTab.setVisible(label.equals("Drivers"));
            pointsTab.setVisible(label.equals("Points"));
            settingsTab.setVisible(label.equals("Settings"));
            tiersTab.setVisible(label.equals("Tiers"));
        });

        raceWeekendsTab.setVisible(true);
        standingsTab.setVisible(false);
        driversTab.setVisible(false);
        pointsTab.setVisible(false);
        settingsTab.setVisible(false);
        tiersTab.setVisible(false);

        add(nav, header, mainTabs, raceWeekendsTab, standingsTab, driversTab, pointsTab, settingsTab, tiersTab);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        java.util.Map<String, java.util.List<String>> params = event.getLocation().getQueryParameters().getParameters();
        if (params.containsKey("tier")) {
            try {
                targetTierId = Long.parseLong(params.get("tier").get(0));
            } catch (NumberFormatException ignored) {}
        }
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        leagueRepository.findById(parameter).ifPresentOrElse(l -> {
            this.league = l;
            seasonName.setText("Season: " + league.getName());
            
            raceWeekendsTab.setLeague(league);
            standingsTab.setLeague(league);
            driversTab.setLeague(league);
            pointsTab.setLeague(league);
            settingsTab.setLeague(league);
            tiersTab.setLeague(league);

            refreshTiers();
            if (initialLoad) {
                if (targetTierId != null) {
                    final Long lookFor = targetTierId;
                    league.getTiers().stream()
                            .filter(t -> t.getId().equals(lookFor))
                            .findFirst()
                            .ifPresent(tierComboBox::setValue);
                } else if (!league.getTiers().isEmpty()) {
                    tierComboBox.setValue(league.getTiers().get(0));
                }
                initialLoad = false;
            }
        }, () -> {
            event.forwardTo(SeasonListView.class);
        });
    }

    private void refreshTiers() {
        if (league == null) return;
        leagueRepository.findByIdWithTiers(league.getId()).ifPresent(l -> {
            this.league = l;
            tierComboBox.setItems(league.getTiers());
            
            if (selectedTier != null && !league.getTiers().contains(selectedTier)) {
                // Previously selected tier was deleted, fallback to first available or null
                selectedTier = league.getTiers().isEmpty() ? null : league.getTiers().get(0);
                tierComboBox.setValue(selectedTier);
            } else {
                updateLiveLink();
                updateData();
            }
        });
    }

    private void updateLiveLink() {
        if (selectedTier != null) {
            liveDashboardLink.setRoute(LeaderboardView.class, selectedTier.getId());
            liveDashboardLink.setVisible(true);
        } else {
            liveDashboardLink.setVisible(false);
        }
    }

    private void updateData() {
        if (league == null) return;
        raceWeekendsTab.setTier(selectedTier);
        standingsTab.setTier(selectedTier);
        driversTab.setTier(selectedTier);
        
        driversTab.updateData();
        pointsTab.updateData();
        settingsTab.updateData();
        tiersTab.updateData();
    }
}
