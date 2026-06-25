package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.LeagueLogo;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import lombok.Getter;
import lombok.Setter;
import be.jabapage.racingleague.f1telemetry.repository.DriverStandingRepository;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamStandingRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueLogoRepository;
import be.jabapage.racingleague.f1telemetry.repository.ChampionshipTeamStandingRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.SessionPointConfigRepository;
import be.jabapage.racingleague.f1telemetry.repository.ExtraPointRuleRepository;
import be.jabapage.racingleague.f1telemetry.repository.ManualPenaltyRepository;
import be.jabapage.racingleague.f1telemetry.ui.season.RaceWeekendsTab;
import be.jabapage.racingleague.f1telemetry.ui.season.CalendarTab;
import be.jabapage.racingleague.f1telemetry.ui.season.StandingsTab;
import be.jabapage.racingleague.f1telemetry.ui.season.DriversTab;
import be.jabapage.racingleague.f1telemetry.ui.season.PointsTab;
import be.jabapage.racingleague.f1telemetry.ui.season.TiersTab;
import be.jabapage.racingleague.f1telemetry.ui.season.SettingsTab;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@AnonymousAllowed
@PageTitle("Season Details | F1 Telemetry")
@Route(value = "season")
public class SeasonDetailsView extends VerticalLayout implements HasUrlParameter<Long> {

    private final LeagueRepository leagueRepository;
    private final TierRepository tierRepository;
    private final EventRepository eventRepository;
    private final LeagueLogoRepository leagueLogoRepository;
    private final SecurityService securityService;

    private League league;
    private List<Tier> leagueTiers = new ArrayList<>();
    private Tier selectedTier;

    private final HorizontalLayout logoContainer = new HorizontalLayout();
    private final H2 seasonName = new H2();
    private final ComboBox<Tier> tierSelector = new ComboBox<>("Active Tier");

    private final Tab raceWeekendsTab = new Tab("Race Weekends");
    private final Tab calendarTab = new Tab("Calendar");
    private final Tab standingsTab = new Tab("Standings");
    private final Tab driversTab = new Tab("Drivers");
    private final Tab pointsTab = new Tab("Points");
    private final Tab tiersTab = new Tab("Tiers");
    private final Tab settingsTab = new Tab("Settings");
    private final Tabs mainTabs = new Tabs(raceWeekendsTab, calendarTab, standingsTab, driversTab, pointsTab, tiersTab, settingsTab);

    private final RaceWeekendsTab raceWeekendsTabContent;
    private final CalendarTab calendarTabContent;
    private final StandingsTab standingsTabContent;
    private final DriversTab driversTabContent;
    private final PointsTab pointsTabContent;
    private final TiersTab tiersTabContent;
    private final SettingsTab settingsTabContent;

    private boolean isInitializing = false;
    private List<Event> currentEvents = new ArrayList<>();

    public SeasonDetailsView(LeagueRepository leagueRepository,
                             TierRepository tierRepository,
                             EventRepository eventRepository,
                             DriverStandingRepository driverStandingRepository,
                             TeamStandingRepository teamStandingRepository,
                             DriverMappingRepository driverMappingRepository,
                             SessionPointConfigRepository sessionPointConfigRepository,
                             ExtraPointRuleRepository extraPointRuleRepository,
                             ManualPenaltyRepository manualPenaltyRepository,
                             LeagueLogoRepository leagueLogoRepository,
                             TeamMappingRepository teamMappingRepository,
                             TelemetryProcessingService telemetryProcessingService,
                             SecurityService securityService,
                             ChampionshipTeamStandingRepository championshipTeamStandingRepository) {
        this.leagueRepository = leagueRepository;
        this.tierRepository = tierRepository;
        this.eventRepository = eventRepository;
        this.leagueLogoRepository = leagueLogoRepository;
        this.securityService = securityService;

        setSizeFull();

        raceWeekendsTabContent = new RaceWeekendsTab(eventRepository, telemetryProcessingService, securityService, this::updateData);
        calendarTabContent = new CalendarTab(leagueLogoRepository);
        standingsTabContent = new StandingsTab(driverStandingRepository, teamStandingRepository, championshipTeamStandingRepository, manualPenaltyRepository, telemetryProcessingService, securityService, this::updateData);
        driversTabContent = new DriversTab(driverMappingRepository, teamMappingRepository, tierRepository, telemetryProcessingService, securityService, this::updateData);
        pointsTabContent = new PointsTab(sessionPointConfigRepository, extraPointRuleRepository, tierRepository, telemetryProcessingService, securityService, this::updateData);
        tiersTabContent = new TiersTab(tierRepository, securityService, this::updateData, this::refreshTiersList);
        settingsTabContent = new SettingsTab(leagueRepository, leagueLogoRepository, telemetryProcessingService, securityService, this::updateData, this::updateLogo);

        HorizontalLayout nav = new HorizontalLayout();
        nav.add(new RouterLink("League Hub", LeagueHubView.class));
        if (securityService.getAuthenticatedUser().isPresent()) {
            nav.add(new RouterLink("My Seasons", SeasonListView.class));
        } else {
            nav.add(new RouterLink("Login", LoginView.class));
        }
        nav.add(new RouterLink("Documentation", DocumentationView.class));
        nav.setSpacing(true);

        tierSelector.setItemLabelGenerator(Tier::getName);
        tierSelector.setWidth("250px");
        tierSelector.addValueChangeListener(e -> {
            if (e.getValue() != null && !isInitializing) {
                selectedTier = e.getValue();
                updateData();
            }
        });

        logoContainer.setAlignItems(Alignment.CENTER);
        HorizontalLayout header = new HorizontalLayout(logoContainer, seasonName, tierSelector);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(true);

        mainTabs.addSelectedChangeListener(e -> {
            Tab selectedTab = e.getSelectedTab();
            raceWeekendsTabContent.setVisible(raceWeekendsTab.equals(selectedTab));
            calendarTabContent.setVisible(calendarTab.equals(selectedTab));
            standingsTabContent.setVisible(standingsTab.equals(selectedTab));
            driversTabContent.setVisible(driversTab.equals(selectedTab));
            pointsTabContent.setVisible(pointsTab.equals(selectedTab));
            tiersTabContent.setVisible(tiersTab.equals(selectedTab));
            settingsTabContent.setVisible(settingsTab.equals(selectedTab));
        });

        calendarTabContent.setVisible(false);
        standingsTabContent.setVisible(false);
        driversTabContent.setVisible(false);
        pointsTabContent.setVisible(false);
        tiersTabContent.setVisible(false);
        settingsTabContent.setVisible(false);

        add(nav, header, mainTabs, raceWeekendsTabContent, calendarTabContent, standingsTabContent, driversTabContent, pointsTabContent, tiersTabContent, settingsTabContent);
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        isInitializing = true;
        try {
            league = leagueRepository.findByIdWithUser(parameter).orElseThrow();
            seasonName.setText("Season: " + league.getName());
            
            updateLogo();
            updateTierSelector();
            
            boolean isOwner = securityService.getAuthenticatedUserEntity()
                    .map(user -> league.getUser() != null && league.getUser().getId().equals(user.getId()))
                    .orElse(false);
            settingsTab.setVisible(isOwner);
            
            if (!isOwner && mainTabs.getSelectedTab() == settingsTab) {
                mainTabs.setSelectedTab(raceWeekendsTab);
            }
            
            refreshTiersList();
            updateData();
        } finally {
            isInitializing = false;
        }
    }

    private void updateTierSelector() {
        if (league == null) return;
        leagueTiers = tierRepository.findByLeagueOrderByNameAsc(league);
        tierSelector.setItems(leagueTiers);
        
        if (selectedTier != null && !leagueTiers.contains(selectedTier)) {
            selectedTier = null;
        }
        
        if (selectedTier == null && !leagueTiers.isEmpty()) {
            selectedTier = leagueTiers.get(0);
        }
        
        tierSelector.setValue(selectedTier);
    }

    private void refreshTiersList() {
        updateTierSelector();
        if (league != null) {
            tiersTabContent.update(league);
        }
    }

    private void updateData() {
        if (league == null || selectedTier == null) return;

        this.currentEvents = eventRepository.findByTier(selectedTier);
        
        raceWeekendsTabContent.update(selectedTier, currentEvents);
        calendarTabContent.update(league, selectedTier, currentEvents);
        standingsTabContent.update(league, selectedTier, leagueTiers);
        driversTabContent.update(league, selectedTier, leagueTiers);
        pointsTabContent.update(league);
        tiersTabContent.update(league);

        boolean isOwner = securityService.getAuthenticatedUserEntity()
                .map(user -> league.getUser() != null && league.getUser().getId().equals(user.getId()))
                .orElse(false);
        settingsTabContent.update(league, isOwner);
    }

    private void updateLogo() {
        logoContainer.removeAll();
        if (league != null && league.getHasLogo()) {
            StreamResource resource = new StreamResource("logo-" + league.getId() + "-" + System.currentTimeMillis() + ".png",
                    () -> {
                        byte[] logoBytes = leagueLogoRepository.findById(league.getId())
                                .map(LeagueLogo::getLogo)
                                .orElse(new byte[0]);
                        return new ByteArrayInputStream(logoBytes);
                    });
            Image logoImg = new Image(resource, "logo");
            logoImg.setHeight("50px");
            logoContainer.add(logoImg);
        }
        if (league != null && league.getLogoBackgroundColor() != null) {
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

    @Override
    protected void onAttach(com.vaadin.flow.component.AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        updateLogo();
        attachEvent.getUI().getPage().executeJs(EventResultsView.getDownloadInfographicJs());
    }

    @Override
    protected void onDetach(com.vaadin.flow.component.DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        detachEvent.getUI().getPage().executeJs(
            "document.documentElement.style.removeProperty('--lumo-base-color'); document.body.style.backgroundColor = '';"
        );
    }

    @Getter
    @Setter
    public static class PenaltyStandingRow {
        private String driverName;
        private String teamName;
        private String country;
        private Integer raceNumber;
        private long incidentCount;
        private long totalPointsDeducted;
        private long totalGivenSeconds;
    }

    public static String getTrackCountryFlag(String trackId) {
        if (trackId == null) return "❓";
        try {
            int id = Integer.parseInt(trackId);
            return switch(id) {
                case 0, 36 -> "🇦🇺";
                case 1 -> "🇫🇷";
                case 2 -> "🇨🇳";
                case 3, 21 -> "🇧🇭";
                case 4, 35, 42 -> "🇪🇸";
                case 5 -> "🇲🇨";
                case 6 -> "🇨🇦";
                case 7, 22, 38, 39 -> "🇬🇧";
                case 8 -> "🇩🇪";
                case 9 -> "🇭🇺";
                case 10 -> "🇧🇪";
                case 11, 27, 33 -> "🇮🇹";
                case 12 -> "🇸🇬";
                case 13, 24 -> "🇯🇵";
                case 14 -> "🇦🇪";
                case 15, 23, 30, 31 -> "🇺🇸";
                case 16, 41 -> "🇧🇷";
                case 17, 40 -> "🇦🇹";
                case 18 -> "🇷🇺";
                case 19 -> "🇲🇽";
                case 20 -> "🇦🇿";
                case 25 -> "🇻🇳";
                case 26 -> "🇳🇱";
                case 28, 34 -> "🇵🇹";
                case 29 -> "🇸🇦";
                case 32 -> "🇶🇦";
                case 37 -> "🇿🇦";
                default -> "❓";
            };
        } catch (NumberFormatException e) {
            return "❓";
        }
    }
}
