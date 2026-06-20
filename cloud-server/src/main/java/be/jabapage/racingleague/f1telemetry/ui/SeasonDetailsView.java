package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import lombok.Getter;
import lombok.Setter;
import be.jabapage.racingleague.f1telemetry.entity.TeamStanding;
import be.jabapage.racingleague.f1telemetry.entity.LeagueLogo;
import be.jabapage.racingleague.f1telemetry.entity.TeamMapping;
import be.jabapage.racingleague.f1telemetry.repository.DriverStandingRepository;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamStandingRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueLogoRepository;
import be.jabapage.racingleague.f1telemetry.entity.ChampionshipTeamStanding;
import be.jabapage.racingleague.f1telemetry.repository.ChampionshipTeamStandingRepository;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import java.time.LocalDate;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Input;
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
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.SessionPointConfig;
import be.jabapage.racingleague.f1telemetry.entity.ExtraPointRule;
import be.jabapage.racingleague.f1telemetry.entity.ManualPenalty;
import be.jabapage.racingleague.f1telemetry.util.ImageColorExtractor;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.SessionPointConfigRepository;
import be.jabapage.racingleague.f1telemetry.repository.ExtraPointRuleRepository;
import be.jabapage.racingleague.f1telemetry.repository.ManualPenaltyRepository;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.component.dialog.Dialog;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.server.StreamResource;
import java.io.ByteArrayInputStream;

@AnonymousAllowed
@PageTitle("Season Details | F1 Telemetry")
@Route(value = "season")
public class SeasonDetailsView extends VerticalLayout implements HasUrlParameter<Long> {

    private final LeagueRepository leagueRepository;
    private final TierRepository tierRepository;
    private final EventRepository eventRepository;
    private final DriverStandingRepository driverStandingRepository;
    private final TeamStandingRepository teamStandingRepository;
    private final DriverMappingRepository driverMappingRepository;
    private final SessionPointConfigRepository sessionPointConfigRepository;
    private final ExtraPointRuleRepository extraPointRuleRepository;
    private final ManualPenaltyRepository manualPenaltyRepository;
    private final LeagueLogoRepository leagueLogoRepository;
    private final TeamMappingRepository teamMappingRepository;
    private final SecurityService securityService;
    private final TelemetryProcessingService telemetryProcessingService;
    private final ChampionshipTeamStandingRepository championshipTeamStandingRepository;
    
    private League league;
    private List<Tier> leagueTiers = new ArrayList<>();
    private Tier selectedTier;

    private final HorizontalLayout logoContainer = new HorizontalLayout();
    private final HorizontalLayout logoUploadLayout = new HorizontalLayout();
    private final H2 seasonName = new H2();
    private final TextField seasonNameField = new TextField("Season Name");
    private final ComboBox<Tier> tierSelector = new ComboBox<>("Active Tier");
    private final Checkbox hideAiCheckbox = new Checkbox("Hide AI Drivers");
    private final Checkbox showTyreWearCheckbox = new Checkbox("Show Tyre Wear on Live Leaderboard");
    private final Checkbox showErsCheckbox = new Checkbox("Show ERS on Live Leaderboard");
    private final com.vaadin.flow.component.textfield.IntegerField minLapsPctField = new com.vaadin.flow.component.textfield.IntegerField("Minimum Laps Percentage for Stats (%)");
    private final ComboBox<String> carTypeCombo = new ComboBox<>("Car Type");
    private final TextField hexColorField = new TextField("Background Color (Hex)");
    private final Input colorPicker = new Input();
    private final TextField hexAccentColorField = new TextField("Ribbon Accent Color (Hex)");
    private final Input accentColorPicker = new Input();
    private final TextField youtubeField = new TextField("YouTube Handle");
    private final TextField tiktokField = new TextField("TikTok Handle");
    private final TextField xField = new TextField("X (Twitter) Handle");
    private final TextField instagramField = new TextField("Instagram Handle");
    private final TextField twitchField = new TextField("Twitch Handle");

    private final Checkbox useChampionshipTeamsCheckbox = new Checkbox("Enable Championship Teams");
    private final TextField teamANameField = new TextField("Championship Team A Name");
    private final TextField teamBNameField = new TextField("Championship Team B Name");
    private final Grid<ChampionshipTeamStanding> champTeamGrid = new Grid<>(ChampionshipTeamStanding.class, false);
    private final Grid<ChampionshipTeamStanding> leagueChampTeamGrid = new Grid<>(ChampionshipTeamStanding.class, false);
    private final VerticalLayout champTeamStandingsContent = new VerticalLayout();
    private final VerticalLayout leagueChampTeamStandingsContent = new VerticalLayout();
    private final Tab champTeamsTab = new Tab("Championship Teams");
    private final Tab leagueChampTeamsTab = new Tab("League Championship Teams");
    private final Tabs standingsTabs = new Tabs();
    private Grid.Column<DriverMapping> champTeamColumn;

    private final Tab raceWeekendsTab = new Tab("Race Weekends");
    private final Tab calendarTab = new Tab("Calendar");
    private final Tab standingsTab = new Tab("Standings");
    private final Tab driversTab = new Tab("Drivers");
    private final Tab pointsTab = new Tab("Points");
    private final Tab tiersTab = new Tab("Tiers");
    private final Tab settingsTab = new Tab("Settings");
    private final Tabs mainTabs = new Tabs(raceWeekendsTab, calendarTab, standingsTab, driversTab, pointsTab, tiersTab, settingsTab);

    private final HorizontalLayout tiersToolbar = new HorizontalLayout();
    private final H3 tiersTitle = new H3("Manage Tiers");
    private Grid.Column<Tier> tokenColumn;
    private Grid.Column<Tier> tierActionsColumn;

    private final VerticalLayout generalSettingsContent = new VerticalLayout();
    private final VerticalLayout uiTweaksSettingsContent = new VerticalLayout();
    
    private final Grid<Event> eventGrid = new Grid<>(Event.class, false);
    private final Grid<DriverStanding> driverGrid = new Grid<>(DriverStanding.class, false);
    private final Grid<TeamStanding> teamGrid = new Grid<>(TeamStanding.class, false);
    private final Grid<TeamStanding> leagueTeamGrid = new Grid<>(TeamStanding.class, false);
    private final Grid<PenaltyStandingRow> penaltiesGrid = new Grid<>();
    private final Grid<PenaltyStandingRow> leaguePenaltiesGrid = new Grid<>();
    private final Grid<DriverMapping> mappingGrid = new Grid<>(DriverMapping.class, false);
    private final Grid<SessionPointConfig> pointsGrid = new Grid<>(SessionPointConfig.class, false);
    private final Grid<Tier> tierGrid = new Grid<>(Tier.class, false);

    private final VerticalLayout eventsLayout = new VerticalLayout();
    private final VerticalLayout calendarLayout = new VerticalLayout();
    private final VerticalLayout standingsLayout = new VerticalLayout();
    private final VerticalLayout driverStandingsContent = new VerticalLayout();
    private final VerticalLayout teamStandingsContent = new VerticalLayout();
    private final VerticalLayout leagueTeamStandingsContent = new VerticalLayout();
    private final VerticalLayout penaltiesContent = new VerticalLayout();
    private final VerticalLayout leaguePenaltiesContent = new VerticalLayout();
    private final VerticalLayout driversLayout = new VerticalLayout();
    private final VerticalLayout pointsLayout = new VerticalLayout();
    private final VerticalLayout tiersLayout = new VerticalLayout();
    private final VerticalLayout settingsLayout = new VerticalLayout();
    
    private final Button addManualDriverBtn = new Button("Add Manual Driver");
    private final Button deleteSelectedMappingsBtn = new Button("Delete Selected");
    private final ComboBox<Tier> tierEditorField = new ComboBox<>();
    private final ComboBox<TeamMapping> teamEditorCombo = new ComboBox<>();
    private final ComboBox<TeamMapping> teamEditorField = new ComboBox<>();
    private final ComboBox<String> champTeamEditorField = new ComboBox<>();
    
    // Points UI Components
    private final Tabs sessionTypeTabs = new Tabs();
    private final Button addSessionTypeBtn = new Button("Add Session Type");
    private final Button savePointsBtn = new Button("Save & Recalculate");
    private final Button deleteSessionBtn = new Button("Remove All Overrides for Session");
    private final Grid<ExtraPointRule> extraPointRulesGrid = new Grid<>(ExtraPointRule.class, false);
    private Grid.Column<ExtraPointRule> extraRulesActionColumn;
    private Grid.Column<ExtraPointRule> extraRulesExpressionColumn;
    private final Button addExtraRuleBtn = new Button("Add Extra Point Rule");
    private Integer selectedSessionType = null;
    private final List<SessionPointConfig> currentEditingConfigs = new ArrayList<>();
    private boolean pointsChanged = false;
    
    private final Button recalculateBtn = new Button("Recalculate Standings");
    private final Button addManualWeekendBtn = new Button("Add Manual Weekend");
    private Grid.Column<Event> actionsColumn;
    private boolean isInitializing = false;
    private List<Event> currentEvents = new java.util.ArrayList<>();

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
        this.driverStandingRepository = driverStandingRepository;
        this.teamStandingRepository = teamStandingRepository;
        this.driverMappingRepository = driverMappingRepository;
        this.sessionPointConfigRepository = sessionPointConfigRepository;
        this.extraPointRuleRepository = extraPointRuleRepository;
        this.manualPenaltyRepository = manualPenaltyRepository;
        this.leagueLogoRepository = leagueLogoRepository;
        this.teamMappingRepository = teamMappingRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;
        this.championshipTeamStandingRepository = championshipTeamStandingRepository;

        setSizeFull();
        configureGrids();

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

        // Top level tabs
        mainTabs.addSelectedChangeListener(e -> {
            Tab selectedTab = e.getSelectedTab();
            eventsLayout.setVisible(raceWeekendsTab.equals(selectedTab));
            calendarLayout.setVisible(calendarTab.equals(selectedTab));
            standingsLayout.setVisible(standingsTab.equals(selectedTab));
            driversLayout.setVisible(driversTab.equals(selectedTab));
            pointsLayout.setVisible(pointsTab.equals(selectedTab));
            tiersLayout.setVisible(tiersTab.equals(selectedTab));
            settingsLayout.setVisible(settingsTab.equals(selectedTab));
        });
        calendarLayout.setVisible(false);

        eventsLayout.add(new HorizontalLayout(new H3("Race Weekends"), addManualWeekendBtn), eventGrid);

        // Tiers management layout
        TextField addTierNameField = new TextField("Tier Name");
        Button addTierBtn = new Button("Add Tier");
        addTierBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addTierBtn.addClickListener(ev -> {
            if (league == null || addTierNameField.getValue().isEmpty()) return;
            Tier t = new Tier();
            t.setName(addTierNameField.getValue());
            t.setToken(UUID.randomUUID().toString());
            t.setLeague(league);
            tierRepository.save(t);
            addTierNameField.clear();
            updateTierSelector();
            refreshTiersList();
            Notification.show("Tier added", 3000, Notification.Position.TOP_CENTER);
        });

        tiersToolbar.add(addTierNameField, addTierBtn);
        tiersToolbar.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

        tiersLayout.add(tiersTitle, tiersToolbar, tierGrid);
        tiersLayout.setVisible(false);

        // Points Layout
        pointsLayout.add(new H3("Points Configuration Overrides"));
        pointsLayout.add(new Span("By default, the standard F1 point system is used for Race sessions. Use this section to overrule points for any session type."));
        
        HorizontalLayout pointsHeader = new HorizontalLayout(sessionTypeTabs, addSessionTypeBtn);
        pointsHeader.setAlignItems(Alignment.END);
        pointsHeader.setWidthFull();
        pointsHeader.setFlexGrow(1, sessionTypeTabs);

        pointsLayout.add(pointsHeader);
        
        pointsGrid.setWidth("550px");
        
        VerticalLayout bonusSidebar = new VerticalLayout();
        bonusSidebar.setWidthFull();
        bonusSidebar.setPadding(false);
        bonusSidebar.setSpacing(true);
        
        bonusSidebar.add(new H3("Extra Point Rules"));
        extraPointRulesGrid.setWidthFull();
        extraPointRulesGrid.setMinHeight("250px");
        bonusSidebar.add(extraPointRulesGrid, addExtraRuleBtn);
        
        addExtraRuleBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addExtraRuleBtn.addClickListener(e -> showAddExtraPointRuleDialog());

        HorizontalLayout gridAndBonus = new HorizontalLayout(pointsGrid, bonusSidebar);
        gridAndBonus.setWidthFull();
        gridAndBonus.setAlignItems(Alignment.START);
        gridAndBonus.setFlexGrow(0, pointsGrid);
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

        useChampionshipTeamsCheckbox.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setUseChampionshipTeams(e.getValue());
                leagueRepository.save(league);
                teamANameField.setEnabled(e.getValue());
                teamBNameField.setEnabled(e.getValue());
                updateData();
                Notification.show("Championship Teams toggle updated", 3000, Notification.Position.TOP_CENTER);
            }
        });

        teamANameField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setTeamAName(e.getValue());
                leagueRepository.save(league);
                updateData();
            }
        });

        teamBNameField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setTeamBName(e.getValue());
                leagueRepository.save(league);
                updateData();
            }
        });

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
        
        seasonNameField.setWidth("300px");
        seasonNameField.addValueChangeListener(e -> {
            if (league != null && !isInitializing && e.getValue() != null && !e.getValue().isEmpty()) {
                league.setName(e.getValue());
                leagueRepository.save(league);
                seasonName.setText("Season: " + e.getValue());
                Notification.show("Season name updated", 3000, Notification.Position.TOP_CENTER);
            }
        });

        minLapsPctField.setMin(0);
        minLapsPctField.setMax(100);
        minLapsPctField.setStepButtonsVisible(true);
        minLapsPctField.setWidth("300px");
        minLapsPctField.addValueChangeListener(e -> {
            if (league != null && !isInitializing && e.getValue() != null) {
                league.setMinLapsPct(e.getValue());
                leagueRepository.save(league);
                Notification.show("Minimum laps percentage updated", 3000, Notification.Position.TOP_CENTER);
            }
        });

        carTypeCombo.setItems(java.util.List.of("F1 25", "F1 26"));
        carTypeCombo.setWidth("300px");
        carTypeCombo.addValueChangeListener(e -> {
            if (league != null && !isInitializing && e.getValue() != null) {
                league.setCarType(e.getValue());
                leagueRepository.save(league);
                updateData();
                Notification.show("Car Type updated", 3000, Notification.Position.TOP_CENTER);
            }
        });

        // Logo Upload Config
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("image/png", "image/jpeg", "image/gif");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(1048576); // 1MB
        upload.setDropLabel(new Span("Drop logo image here (max 1MB)"));
        upload.setUploadButton(new Button("Upload Logo"));
        
        upload.addSucceededListener(ev -> {
            try {
                byte[] bytes = buffer.getInputStream().readAllBytes();
                LeagueLogo logo = leagueLogoRepository.findById(league.getId())
                        .orElseGet(() -> {
                            LeagueLogo lLogo = new LeagueLogo();
                            lLogo.setLeagueId(league.getId());
                            return lLogo;
                        });
                logo.setLogo(bytes);
                leagueLogoRepository.save(logo);
                
                String bgColor = ImageColorExtractor.extractBackgroundColor(bytes);
                league.setLogoBackgroundColor(bgColor);
                league.setHasLogo(true);
                league = leagueRepository.save(league);
                
                // Sync UI fields
                isInitializing = true;
                if (bgColor != null) {
                    hexColorField.setValue(bgColor);
                    colorPicker.setValue(bgColor);
                } else {
                    hexColorField.setValue("");
                    colorPicker.setValue("#ffffff");
                }
                isInitializing = false;
                
                updateLogo();
                Notification.show("Logo uploaded successfully!", 3000, Notification.Position.TOP_CENTER);
            } catch (java.io.IOException ex) {
                Notification.show("Failed to upload logo: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
            }
        });

        upload.addFileRejectedListener(ev -> {
            Notification notification = Notification.show("Upload failed: " + ev.getErrorMessage(), 5000, Notification.Position.TOP_CENTER);
            notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
        });

        Button removeLogoBtn = new Button("Remove Logo", ev -> {
            if (league != null) {
                leagueLogoRepository.deleteById(league.getId());
                league.setHasLogo(false);
                league.setLogoBackgroundColor(null);
                league = leagueRepository.save(league);
                
                // Sync UI fields
                isInitializing = true;
                hexColorField.setValue("");
                colorPicker.setValue("#ffffff");
                isInitializing = false;
                
                updateLogo();
                Notification.show("Logo removed successfully!", 3000, Notification.Position.TOP_CENTER);
            }
        });
        removeLogoBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);

        logoUploadLayout.add(upload, removeLogoBtn);
        logoUploadLayout.setAlignItems(Alignment.CENTER);
        logoUploadLayout.setSpacing(true);
        logoUploadLayout.setVisible(false);

        // Color Picker Config & Sync
        colorPicker.setType("color");
        colorPicker.getStyle().set("width", "50px");
        colorPicker.getStyle().set("height", "38px");
        colorPicker.getStyle().set("padding", "0");
        colorPicker.getStyle().set("border", "1px solid var(--lumo-contrast-30pct)");
        colorPicker.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        colorPicker.getStyle().set("cursor", "pointer");

        colorPicker.addValueChangeListener(e -> {
            String color = e.getValue();
            if (color != null && !color.isEmpty() && !color.equalsIgnoreCase(hexColorField.getValue())) {
                hexColorField.setValue(color);
                saveBackgroundColor(color);
            }
        });

        hexColorField.addValueChangeListener(e -> {
            String val = e.getValue();
            if (val != null && val.matches("^#[0-9a-fA-F]{6}$")) {
                if (!val.equalsIgnoreCase(colorPicker.getValue())) {
                    colorPicker.setValue(val);
                }
                saveBackgroundColor(val);
            } else if (val == null || val.isEmpty()) {
                saveBackgroundColor(null);
            }
        });

        // Accent Color Picker Config & Sync
        accentColorPicker.setType("color");
        accentColorPicker.getStyle().set("width", "50px");
        accentColorPicker.getStyle().set("height", "38px");
        accentColorPicker.getStyle().set("padding", "0");
        accentColorPicker.getStyle().set("border", "1px solid var(--lumo-contrast-30pct)");
        accentColorPicker.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        accentColorPicker.getStyle().set("cursor", "pointer");

        accentColorPicker.addValueChangeListener(e -> {
            String color = e.getValue();
            if (color != null && !color.isEmpty() && !color.equalsIgnoreCase(hexAccentColorField.getValue())) {
                hexAccentColorField.setValue(color);
                saveAccentColor(color);
            }
        });

        hexAccentColorField.addValueChangeListener(e -> {
            String val = e.getValue();
            if (val != null && val.matches("^#[0-9a-fA-F]{6}$")) {
                if (!val.equalsIgnoreCase(accentColorPicker.getValue())) {
                    accentColorPicker.setValue(val);
                }
                saveAccentColor(val);
            } else if (val == null || val.isEmpty()) {
                saveAccentColor(null);
            }
        });

        // Social Handles Listeners
        youtubeField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setYoutubeHandle(e.getValue());
                leagueRepository.save(league);
            }
        });
        tiktokField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setTiktokHandle(e.getValue());
                leagueRepository.save(league);
            }
        });
        xField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setXHandle(e.getValue());
                leagueRepository.save(league);
            }
        });
        instagramField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setInstagramHandle(e.getValue());
                leagueRepository.save(league);
            }
        });
        twitchField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setTwitchHandle(e.getValue());
                leagueRepository.save(league);
            }
        });

        // Settings Layout sub-tabs
        Tab generalSettingsTab = new Tab("Season Settings");
        Tab uiTweaksTab = new Tab("UI Tweaks");
        Tabs settingsTabs = new Tabs(generalSettingsTab, uiTweaksTab);
        
        generalSettingsContent.setPadding(true);
        generalSettingsContent.setSpacing(true);
        HorizontalLayout teamNamesLayout = new HorizontalLayout(teamANameField, teamBNameField);
        teamNamesLayout.setSpacing(true);
        generalSettingsContent.add(
            seasonNameField,
            hideAiCheckbox,
            minLapsPctField,
            carTypeCombo,
            new H4("Championship Teams"),
            useChampionshipTeamsCheckbox,
            teamNamesLayout
        );
        
        uiTweaksSettingsContent.setPadding(true);
        uiTweaksSettingsContent.setSpacing(true);
        uiTweaksSettingsContent.add(
            showTyreWearCheckbox, 
            showErsCheckbox, 
            new H4("League Logo & Colors"),
            logoUploadLayout,
            new HorizontalLayout(hexColorField, colorPicker),
            new HorizontalLayout(hexAccentColorField, accentColorPicker),
            new H4("Lineup Social Media Handles"),
            youtubeField,
            tiktokField,
            xField,
            instagramField,
            twitchField
        );
        uiTweaksSettingsContent.setVisible(false);

        settingsTabs.addSelectedChangeListener(event -> {
            boolean isGeneral = event.getSelectedTab().equals(generalSettingsTab);
            generalSettingsContent.setVisible(isGeneral);
            uiTweaksSettingsContent.setVisible(!isGeneral);
        });

        settingsLayout.add(new H3("Settings"), settingsTabs, generalSettingsContent, uiTweaksSettingsContent);
        settingsLayout.setVisible(false);
        
        recalculateBtn.addClickListener(e -> {
            if (selectedTier != null) {
                telemetryProcessingService.recalculateStandings(selectedTier.getId());
                updateData();
                Notification.show("Standings recalculated!", 3000, Notification.Position.TOP_CENTER);
            }
        });

         addManualWeekendBtn.addClickListener(e -> {
            if (selectedTier == null) return;
            
            com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();
            dialog.setHeaderTitle("Add Manual Weekend");
            
            com.vaadin.flow.component.combobox.ComboBox<Integer> trackCombo = new com.vaadin.flow.component.combobox.ComboBox<>("Track");
            trackCombo.setItems(java.util.stream.IntStream.rangeClosed(0, 42).boxed().toList());
            trackCombo.setItemLabelGenerator(id -> TelemetryProcessingService.TRACK_NAMES.getOrDefault(id, "Track " + id));
            trackCombo.setWidthFull();

            TextField nameField = new TextField("Event Name (e.g. Belgian Grand Prix)");
            nameField.setWidthFull();

            DatePicker plannedDatePicker = new DatePicker("Planned Date");
            plannedDatePicker.setWidthFull();

            trackCombo.addValueChangeListener(ev -> {
                if (ev.getValue() != null && (nameField.getValue() == null || nameField.getValue().isEmpty())) {
                    nameField.setValue(TelemetryProcessingService.TRACK_NAMES.getOrDefault(ev.getValue(), "") + " Grand Prix");
                }
            });

            VerticalLayout dialogLayout = new VerticalLayout(trackCombo, nameField, plannedDatePicker);
            dialog.add(dialogLayout);

            Button saveBtn = new Button("Add", ev -> {
                if (trackCombo.getValue() == null || nameField.getValue().isEmpty()) {
                    Notification.show("Please fill in all fields", 3000, Notification.Position.TOP_CENTER);
                    return;
                }
                Event newEvent = new Event();
                newEvent.setTier(selectedTier);
                newEvent.setTrackId(String.valueOf(trackCombo.getValue()));
                newEvent.setEventName(nameField.getValue());
                newEvent.setPlannedDate(plannedDatePicker.getValue());
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
        Tab penaltiesTab = new Tab("Penalties");
        Tab leaguePenaltiesTab = new Tab("League Penalties");
        standingsTabs.add(new Tab("Drivers"), new Tab("Teams"), new Tab("League Teams"), champTeamsTab, leagueChampTeamsTab, penaltiesTab, leaguePenaltiesTab);
        standingsTabs.addSelectedChangeListener(e -> {
            String label = e.getSelectedTab().getLabel();
            driverStandingsContent.setVisible(label.equals("Drivers"));
            teamStandingsContent.setVisible(label.equals("Teams"));
            leagueTeamStandingsContent.setVisible(label.equals("League Teams"));
            champTeamStandingsContent.setVisible(label.equals("Championship Teams"));
            leagueChampTeamStandingsContent.setVisible(label.equals("League Championship Teams"));
            penaltiesContent.setVisible(label.equals("Penalties"));
            leaguePenaltiesContent.setVisible(label.equals("League Penalties"));
        });

        driverGrid.setSelectionMode(Grid.SelectionMode.NONE);

        driverStandingsContent.add(new HorizontalLayout(new H3("Driver Standings"), recalculateBtn),
                driverGrid);
        driverStandingsContent.setPadding(false);        
        
        teamStandingsContent.add(new H3("Team Standings"), teamGrid);
        teamStandingsContent.setVisible(false);
        teamStandingsContent.setPadding(false);

        leagueTeamStandingsContent.add(new H3("League Team Standings (Over All Tiers)"), leagueTeamGrid);
        leagueTeamStandingsContent.setVisible(false);
        leagueTeamStandingsContent.setPadding(false);

        champTeamStandingsContent.add(new H3("Championship Team Standings"), champTeamGrid);
        champTeamStandingsContent.setVisible(false);
        champTeamStandingsContent.setPadding(false);

        leagueChampTeamStandingsContent.add(new H3("League Championship Team Standings (Over All Tiers)"), leagueChampTeamGrid);
        leagueChampTeamStandingsContent.setVisible(false);
        leagueChampTeamStandingsContent.setPadding(false);

        penaltiesContent.add(new H3("Incident & Penalties Standings (This Tier)"), penaltiesGrid);
        penaltiesContent.setVisible(false);
        penaltiesContent.setPadding(false);

        leaguePenaltiesContent.add(new H3("Incident & Penalties Standings (League Wide)"), leaguePenaltiesGrid);
        leaguePenaltiesContent.setVisible(false);
        leaguePenaltiesContent.setPadding(false);

        standingsLayout.add(standingsTabs, driverStandingsContent, teamStandingsContent, leagueTeamStandingsContent, champTeamStandingsContent, leagueChampTeamStandingsContent, penaltiesContent, leaguePenaltiesContent);
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

            ComboBox<String> countryCombo = new ComboBox<>("Country");
            countryCombo.setItems(CountryProvider.getCountryNames());
            countryCombo.setValue("Unknown");
            countryCombo.setWidthFull();

            ComboBox<Tier> manualTierField = new ComboBox<>("Assign to Tier");
            manualTierField.setItems(tierRepository.findByLeagueOrderByNameAsc(league));
            manualTierField.setItemLabelGenerator(Tier::getName);
            manualTierField.setWidthFull();
            manualTierField.setValue(selectedTier);

            Checkbox reserveField = new Checkbox("Reserve");
            
            ComboBox<TeamMapping> teamCombo = new ComboBox<>("Team");
            teamCombo.setItems(getTeamsForLeague());
            teamCombo.setItemLabelGenerator(TeamMapping::getTeamName);
            teamCombo.setWidthFull();

            ComboBox<String> champTeamCombo = new ComboBox<>("Championship Team");
            champTeamCombo.setItems("A", "B", "None");
            champTeamCombo.setItemLabelGenerator(val -> {
                if ("A".equals(val)) {
                    return league != null && league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
                } else if ("B".equals(val)) {
                    return league != null && league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
                }
                return "None";
            });
            champTeamCombo.setValue("None");
            champTeamCombo.setWidthFull();
            boolean teamsEnabled = Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
                && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
                && league.getTeamBName() != null && !league.getTeamBName().isEmpty();
            champTeamCombo.setVisible(teamsEnabled);

            reserveField.addValueChangeListener(ev -> {
                if (ev.getValue()) {
                    teamCombo.setValue(null);
                    teamCombo.setEnabled(false);
                    champTeamCombo.setValue("None");
                    champTeamCombo.setEnabled(false);
                } else {
                    teamCombo.setEnabled(true);
                    champTeamCombo.setEnabled(true);
                }
            });

            VerticalLayout dialogLayout = new VerticalLayout(nameField, telemetryNameField, raceNumField, countryCombo, manualTierField, reserveField, teamCombo, champTeamCombo);
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
                mapping.setCountry(countryCombo.getValue() != null ? countryCombo.getValue() : "Unknown");
                mapping.setDriverId(255); // Use 255 for manual drivers
                mapping.setTier(manualTierField.getValue());
                mapping.setReserve(reserveField.getValue());
                mapping.setTeamId(teamCombo.getValue() != null ? teamCombo.getValue().getTeamId() : null);
                if (teamsEnabled && !"None".equals(champTeamCombo.getValue()) && !mapping.isReserve()) {
                    mapping.setChampionshipTeam(champTeamCombo.getValue());
                } else {
                    mapping.setChampionshipTeam(null);
                }

                checkTeamCapacityAndSave(mapping, () -> {
                    if (!validateChampionshipTeamConstraints(mapping)) {
                        updateData();
                        return;
                    }
                    driverMappingRepository.save(mapping);
                    telemetryProcessingService.refreshDriverMappings(league.getId());
                    // Recalculate standings for all tiers
                    List<Tier> tiers = tierRepository.findByLeague(league);
                    for (Tier t : tiers) {
                        telemetryProcessingService.recalculateStandings(t.getId());
                    }
                    updateData();
                    dialog.close();
                    Notification.show("Manual driver added", 3000, Notification.Position.TOP_CENTER);
                });
            });
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
            dialog.open();
        });

        add(nav, header, mainTabs, eventsLayout, calendarLayout, standingsLayout, driversLayout, pointsLayout, tiersLayout, settingsLayout);
    }

    private void configureGrids() {
        pointsGrid.addColumn(SessionPointConfig::getPosition).setHeader("Pos").setWidth("70px").setFlexGrow(0);
        pointsGrid.addComponentColumn(config -> {
            com.vaadin.flow.component.textfield.IntegerField field = new com.vaadin.flow.component.textfield.IntegerField();
            field.setValue(config.getPoints());
            field.setStepButtonsVisible(true);
            boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
            field.setReadOnly(!loggedIn);
            field.addValueChangeListener(e -> {
                if (loggedIn) {
                    config.setPoints(e.getValue() != null ? e.getValue() : 0);
                    pointsChanged = true;
                }
            });
            field.setWidthFull();
            return field;
        }).setHeader("Points Awarded");
        pointsGrid.setSelectionMode(Grid.SelectionMode.NONE);

        extraPointRulesGrid.addColumn(ExtraPointRule::getRuleName).setHeader("Name").setAutoWidth(true);
        extraPointRulesGrid.addColumn(r -> r.getMetric().getDisplayName()).setHeader("Metric").setAutoWidth(true);
        extraRulesExpressionColumn = extraPointRulesGrid.addComponentColumn(r -> {
            Span span = new Span(r.getMetricExpression());
            span.getElement().setProperty("title", r.getMetricExpression());
            span.getStyle().set("white-space", "nowrap");
            span.getStyle().set("overflow", "hidden");
            span.getStyle().set("text-overflow", "ellipsis");
            span.getStyle().set("display", "block");
            span.getStyle().set("max-width", "100%");
            return span;
        }).setHeader("Expression").setWidth("200px").setFlexGrow(1);
        extraPointRulesGrid.addColumn(r -> {
            if (r.getRuleType() == ExtraPointRule.RuleType.THRESHOLD_BELOW || r.getRuleType() == ExtraPointRule.RuleType.THRESHOLD_ABOVE) {
                return r.getRuleType().getDisplayName() + ": " + r.getThresholdValue();
            }
            return r.getRuleType().getDisplayName();
        }).setHeader("Condition").setAutoWidth(true);
        extraPointRulesGrid.addColumn(r -> r.getPoints() + " pt" + (r.getPoints() == 1 ? "" : "s")).setHeader("Points").setWidth("80px").setFlexGrow(0);
        extraRulesActionColumn = extraPointRulesGrid.addComponentColumn(r -> {
            Button delBtn = new Button("Delete", ev -> {
                extraPointRuleRepository.delete(r);
                List<Tier> tiers = tierRepository.findByLeague(league);
                for (Tier t : tiers) {
                    telemetryProcessingService.recalculateStandings(t.getId());
                }
                updateData();
                Notification.show("Rule deleted and standings recalculated", 3000, Notification.Position.TOP_CENTER);
            });
            delBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            return delBtn;
        }).setHeader("Action").setWidth("100px").setFlexGrow(0);
        extraPointRulesGrid.setSelectionMode(Grid.SelectionMode.NONE);

        eventGrid.addComponentColumn(event -> new RouterLink(event.getEventName(), EventResultsView.class, event.getId()))
                .setHeader("Event")
                .setSortable(true)
                .setComparator(Comparator.comparing(Event::getEventName));
        eventGrid.addColumn(event -> event.getPlannedDate() != null ? event.getPlannedDate().toString() : "TBD")
                .setHeader("Planned Date")
                .setSortable(true)
                .setComparator(Comparator.comparing(event -> event.getPlannedDate() != null ? event.getPlannedDate() : LocalDate.MIN));
        eventGrid.addColumn(event -> {
            java.util.Set<Integer> types = event.getSessionResults().stream()
                    .map(SessionResult::getSessionType)
                    .collect(Collectors.toSet());
            Map<Integer, Integer> sortOrder = Map.ofEntries(
                    Map.entry(1, 1), Map.entry(2, 2), Map.entry(3, 3), Map.entry(4, 4),
                    Map.entry(5, 5), Map.entry(6, 6), Map.entry(7, 7), Map.entry(8, 8), Map.entry(9, 9),
                    Map.entry(10, 10), Map.entry(11, 11), Map.entry(12, 12), Map.entry(13, 13), Map.entry(14, 14),
                    Map.entry(15, 15), Map.entry(16, 16), Map.entry(17, 17),
                    Map.entry(18, 18), Map.entry(19, 19)
            );
            return event.getSessionResults().stream()
                    .map(SessionResult::getSessionType)
                    .distinct()
                    .sorted(Comparator.comparingInt(type -> sortOrder.getOrDefault(type, 99)))
                    .map(type -> EventResultsView.getDynamicSessionName(type, types))
                    .collect(Collectors.joining(", "));
        }).setHeader("Sessions");

        eventGrid.addComponentColumn(event -> {
            Span statusBadge = new Span();
            if (Boolean.TRUE.equals(event.getFinalized())) {
                statusBadge.setText("Final");
                statusBadge.getElement().getThemeList().add("badge success");
            } else {
                statusBadge.setText("Provisional");
                statusBadge.getElement().getThemeList().add("badge error");
            }
            return statusBadge;
        }).setHeader("Status").setAutoWidth(true).setFlexGrow(0);

        actionsColumn = eventGrid.addComponentColumn(event -> {
            HorizontalLayout actions = new HorizontalLayout();
            RouterLink resultsLink = new RouterLink("Results", EventResultsView.class, event.getId());
            actions.add(resultsLink);

            if (securityService.getAuthenticatedUser().isPresent()) {
                RouterLink penaltiesLink = new RouterLink("Penalties", EventPenaltiesView.class, event.getId());
                actions.add(penaltiesLink);

                List<Event> eventsList = this.currentEvents != null ? this.currentEvents : java.util.Collections.emptyList();
                int index = eventsList.indexOf(event);

                Button moveUpBtn = new Button();
                moveUpBtn.setIcon(com.vaadin.flow.component.icon.VaadinIcon.ARROW_UP.create());
                moveUpBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                moveUpBtn.setEnabled(index > 0);
                moveUpBtn.setTooltipText("Move Up");
                moveUpBtn.addClickListener(e -> moveEventUp(event));

                Button moveDownBtn = new Button();
                moveDownBtn.setIcon(com.vaadin.flow.component.icon.VaadinIcon.ARROW_DOWN.create());
                moveDownBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                moveDownBtn.setEnabled(index >= 0 && index < eventsList.size() - 1);
                moveDownBtn.setTooltipText("Move Down");
                moveDownBtn.addClickListener(e -> moveEventDown(event));

                actions.add(moveUpBtn, moveDownBtn);

                Button toggleFinalizedBtn = new Button();
                toggleFinalizedBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
                if (Boolean.TRUE.equals(event.getFinalized())) {
                    toggleFinalizedBtn.setText("Reopen");
                    toggleFinalizedBtn.setIcon(com.vaadin.flow.component.icon.VaadinIcon.UNLOCK.create());
                    toggleFinalizedBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                } else {
                    toggleFinalizedBtn.setText("Mark Final");
                    toggleFinalizedBtn.setIcon(com.vaadin.flow.component.icon.VaadinIcon.LOCK.create());
                    toggleFinalizedBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
                }

                toggleFinalizedBtn.addClickListener(e -> {
                    boolean newStatus = !Boolean.TRUE.equals(event.getFinalized());
                    String statusWord = newStatus ? "final" : "provisional";
                    ConfirmDialog dialog = new ConfirmDialog();
                    dialog.setHeader("Mark Event as " + statusWord.substring(0, 1).toUpperCase() + statusWord.substring(1) + "?");
                    dialog.setText("Are you sure you want to mark '" + event.getEventName() + "' as " + statusWord + "? Standings will be automatically recalculated.");
                    dialog.setCancelable(true);
                    dialog.setConfirmText("Yes");
                    dialog.addConfirmListener(ev -> {
                        event.setFinalized(newStatus);
                        eventRepository.save(event);
                        telemetryProcessingService.recalculateStandings(event.getTier().getId());
                        updateData();
                        Notification.show("Event marked as " + statusWord + " and standings recalculated", 3000, Notification.Position.TOP_CENTER);
                    });
                    dialog.open();
                });
                actions.add(toggleFinalizedBtn);
                
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
                            telemetryProcessingService.deleteEvent(event.getId());
                            updateData();
                            deletingNote.close();
                            Notification.show("Weekend deleted and standings recalculated", 3000, Notification.Position.TOP_CENTER);
                        } catch (Exception ex) {
                            deletingNote.close();
                            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                        }
                    });
                    dialog.open();
                });
                deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
                
                Button editBtn = new Button("Edit", e -> showEditEventDialog(event));
                editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
                actions.add(editBtn, deleteBtn);
            }

            actions.setAlignItems(FlexComponent.Alignment.CENTER);
            return actions;
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);

        driverGrid.addComponentColumn(ds -> {
            HorizontalLayout nameLayout = new HorizontalLayout();
            nameLayout.setAlignItems(Alignment.CENTER);
            nameLayout.setSpacing(false);

            Span flagSpan = new Span(CountryProvider.getFlagByName(ds.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            nameLayout.add(flagSpan);

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

        leagueTeamGrid.addColumn(TeamStanding::getTeamName).setHeader("Team");
        leagueTeamGrid.addColumn(ts -> ts.getPoints() != null ? ts.getPoints() : 0).setHeader("Points").setSortable(true);

        configurePenaltyGrid(penaltiesGrid);
        configurePenaltyGrid(leaguePenaltiesGrid);

        // Configure TierGrid
        tierGrid.addColumn(Tier::getName).setHeader("Tier Name").setAutoWidth(true);
        tokenColumn = tierGrid.addComponentColumn(t -> {
            Span tokenSpan = new Span(t.getToken());
            tokenSpan.getStyle().set("font-family", "monospace");
            tokenSpan.getStyle().set("font-size", "0.8em");
            Button copyBtn = new Button("Copy", e -> {
                getElement().executeJs("navigator.clipboard.writeText($0)", t.getToken());
                Notification.show("Token copied to clipboard", 3000, Notification.Position.TOP_CENTER);
            });
            copyBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            
            HorizontalLayout l = new HorizontalLayout(tokenSpan, copyBtn);
            l.setAlignItems(Alignment.CENTER);
            return l;
        }).setHeader("Telemetry Token").setAutoWidth(true);
        
        tierGrid.addComponentColumn(t -> {
            RouterLink liveLink = new RouterLink("Live Dashboard", LeaderboardView.class, t.getId());
            return liveLink;
        }).setHeader("Live");

        tierActionsColumn = tierGrid.addComponentColumn(t -> {
            HorizontalLayout actions = new HorizontalLayout();
            
            TextField renameField = new TextField();
            renameField.setValue(t.getName());
            renameField.setVisible(false);
            
            Button renameBtn = new Button("Rename");
            renameBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            
            Button saveBtn = new Button("Save");
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            saveBtn.setVisible(false);
            
            renameBtn.addClickListener(e -> {
                renameField.setVisible(true);
                saveBtn.setVisible(true);
                renameBtn.setVisible(false);
            });
            
            saveBtn.addClickListener(e -> {
                if (!renameField.getValue().isEmpty()) {
                    t.setName(renameField.getValue());
                    tierRepository.save(t);
                    updateTierSelector();
                    refreshTiersList();
                    renameField.setVisible(false);
                    saveBtn.setVisible(false);
                    renameBtn.setVisible(true);
                    Notification.show("Tier renamed!", 3000, Notification.Position.TOP_CENTER);
                }
            });
            
            Button deleteBtn = new Button("Delete", e -> {
                ConfirmDialog cd = new ConfirmDialog();
                cd.setHeader("Delete Tier?");
                cd.setText("Are you sure you want to delete '" + t.getName() + "'? All race weekends and standings in this tier will be lost.");
                cd.setCancelable(true);
                cd.setConfirmText("Delete");
                cd.setConfirmButtonTheme("error primary");
                cd.addConfirmListener(event -> {
                    tierRepository.delete(t);
                    updateTierSelector();
                    refreshTiersList();
                    Notification.show("Tier deleted", 3000, Notification.Position.TOP_CENTER);
                });
                cd.open();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            
            actions.add(renameField, renameBtn, saveBtn, deleteBtn);
            actions.setAlignItems(Alignment.CENTER);
            actions.setVisible(securityService.getAuthenticatedUser().isPresent());
            return actions;
        }).setHeader("Actions");

        champTeamGrid.addColumn(cts -> {
            if ("A".equals(cts.getChampionshipTeam())) {
                return league != null && league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
            } else if ("B".equals(cts.getChampionshipTeam())) {
                return league != null && league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
            }
            return cts.getChampionshipTeam();
        }).setHeader("Championship Team");
        champTeamGrid.addColumn(ChampionshipTeamStanding::getPoints).setHeader("Points").setSortable(true);

        leagueChampTeamGrid.addColumn(cts -> {
            if ("A".equals(cts.getChampionshipTeam())) {
                return league != null && league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
            } else if ("B".equals(cts.getChampionshipTeam())) {
                return league != null && league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
            }
            return cts.getChampionshipTeam();
        }).setHeader("Championship Team");
        leagueChampTeamGrid.addColumn(ChampionshipTeamStanding::getPoints).setHeader("Points").setSortable(true);

        configureMappingGrid();
    }

    private void showEditEventDialog(Event event) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Weekend");

        ComboBox<Integer> trackCombo = new ComboBox<>("Track");
        trackCombo.setItems(java.util.stream.IntStream.rangeClosed(0, 42).boxed().toList());
        trackCombo.setItemLabelGenerator(id -> TelemetryProcessingService.TRACK_NAMES.getOrDefault(id, "Track " + id));
        trackCombo.setWidthFull();
        if (event.getTrackId() != null) {
            try {
                trackCombo.setValue(Integer.parseInt(event.getTrackId()));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        TextField nameField = new TextField("Event Name");
        nameField.setWidthFull();
        nameField.setValue(event.getEventName() != null ? event.getEventName() : "");

        DatePicker plannedDatePicker = new DatePicker("Planned Date");
        plannedDatePicker.setWidthFull();
        plannedDatePicker.setValue(event.getPlannedDate());

        trackCombo.addValueChangeListener(ev -> {
            if (ev.getValue() != null && (nameField.getValue() == null || nameField.getValue().isEmpty())) {
                nameField.setValue(TelemetryProcessingService.TRACK_NAMES.getOrDefault(ev.getValue(), "") + " Grand Prix");
            }
        });

        VerticalLayout dialogLayout = new VerticalLayout(trackCombo, nameField, plannedDatePicker);
        dialog.add(dialogLayout);

        Button saveBtn = new Button("Save", ev -> {
            if (trackCombo.getValue() == null || nameField.getValue().isEmpty()) {
                Notification.show("Please fill in all fields", 3000, Notification.Position.TOP_CENTER);
                return;
            }
            event.setTrackId(String.valueOf(trackCombo.getValue()));
            event.setEventName(nameField.getValue());
            event.setPlannedDate(plannedDatePicker.getValue());
            eventRepository.save(event);
            updateData();
            dialog.close();
            Notification.show("Weekend updated", 3000, Notification.Position.TOP_CENTER);
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", ev -> dialog.close());
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void configureMappingGrid() {
        mappingGrid.addComponentColumn(m -> {
            Span flagSpan = new Span(CountryProvider.getFlagByName(m.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            Span name = new Span(m.getTelemetryName());
            HorizontalLayout row = new HorizontalLayout(flagSpan, name);
            row.setAlignItems(Alignment.CENTER);
            row.setSpacing(false);
            return row;
        }).setHeader("Telemetry Name");
        mappingGrid.addColumn(DriverMapping::getRaceNumber).setHeader("Race #");
        mappingGrid.addColumn(DriverMapping::getDriverId).setHeader("Driver ID");
        
        Grid.Column<DriverMapping> reserveColumn = mappingGrid.addComponentColumn(item -> {
            Checkbox cb = new Checkbox(item.isReserve());
            cb.setReadOnly(true);
            return cb;
        }).setHeader("Reserve");

        Grid.Column<DriverMapping> teamColumn = mappingGrid.addColumn(m -> {
            if (m.getTeamId() == null) return "None";
            return teamMappingRepository.findByCarType(getCarTypeForLeague()).stream()
                    .filter(t -> Objects.equals(t.getTeamId(), m.getTeamId()))
                    .map(TeamMapping::getTeamName)
                    .findFirst()
                    .orElse("Team " + m.getTeamId());
        }).setHeader("Team");

        champTeamColumn = mappingGrid.addColumn(m -> {
            if ("A".equals(m.getChampionshipTeam())) {
                return league != null && league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
            } else if ("B".equals(m.getChampionshipTeam())) {
                return league != null && league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
            }
            return "None";
        }).setHeader("Championship Team");

        Grid.Column<DriverMapping> overrideColumn = mappingGrid.addColumn(DriverMapping::getOverriddenName).setHeader("Display Name");
        Grid.Column<DriverMapping> countryColumn = mappingGrid.addColumn(DriverMapping::getCountry).setHeader("Country");

        // Column for Tier assignment
        Grid.Column<DriverMapping> tiersColumn = mappingGrid.addColumn(m -> {
            return m.getTier() != null ? m.getTier().getName() : "";
        }).setHeader("Tier");

        Binder<DriverMapping> binder = new Binder<>(DriverMapping.class);
        Editor<DriverMapping> editor = mappingGrid.getEditor();
        editor.setBinder(binder);
        editor.setBuffered(true);

        champTeamEditorField.setItems("A", "B", "None");
        champTeamEditorField.setItemLabelGenerator(val -> {
            if ("A".equals(val)) {
                return league != null && league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
            } else if ("B".equals(val)) {
                return league != null && league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
            }
            return "None";
        });
        champTeamEditorField.setWidthFull();

        binder.forField(champTeamEditorField)
            .withConverter(new com.vaadin.flow.data.converter.Converter<String, String>() {
                @Override
                public com.vaadin.flow.data.binder.Result<String> convertToModel(String value, com.vaadin.flow.data.binder.ValueContext context) {
                    if ("None".equals(value)) return com.vaadin.flow.data.binder.Result.ok(null);
                    return com.vaadin.flow.data.binder.Result.ok(value);
                }

                @Override
                public String convertToPresentation(String value, com.vaadin.flow.data.binder.ValueContext context) {
                    return value == null ? "None" : value;
                }
            })
            .bind(DriverMapping::getChampionshipTeam, DriverMapping::setChampionshipTeam);
        champTeamColumn.setEditorComponent(champTeamEditorField);

        TextField overrideField = new TextField();
        overrideField.setWidthFull();
        binder.forField(overrideField).bind(DriverMapping::getOverriddenName, DriverMapping::setOverriddenName);
        overrideColumn.setEditorComponent(overrideField);

        ComboBox<String> countryField = new ComboBox<>();
        countryField.setItems(CountryProvider.getCountryNames());
        countryField.setWidthFull();
        binder.forField(countryField).bind(DriverMapping::getCountry, DriverMapping::setCountry);
        countryColumn.setEditorComponent(countryField);

        Checkbox reserveField = new Checkbox();
        binder.forField(reserveField).bind(DriverMapping::getReserve, DriverMapping::setReserve);
        reserveColumn.setEditorComponent(reserveField);

        teamEditorField.setItemLabelGenerator(TeamMapping::getTeamName);
        teamEditorField.setWidthFull();

        binder.forField(teamEditorField)
            .withConverter(new com.vaadin.flow.data.converter.Converter<TeamMapping, Integer>() {
                @Override
                public com.vaadin.flow.data.binder.Result<Integer> convertToModel(TeamMapping value, com.vaadin.flow.data.binder.ValueContext context) {
                    return com.vaadin.flow.data.binder.Result.ok(value != null ? value.getTeamId() : null);
                }

                @Override
                public TeamMapping convertToPresentation(Integer value, com.vaadin.flow.data.binder.ValueContext context) {
                    if (value == null) return null;
                    String name = teamMappingRepository.findByCarType(getCarTypeForLeague()).stream()
                        .filter(t -> Objects.equals(t.getTeamId(), value))
                        .map(TeamMapping::getTeamName)
                        .findFirst()
                        .orElse(null);
                    if (name == null) return null;
                    return getTeamsForLeague().stream()
                        .filter(t -> Objects.equals(t.getTeamName(), name))
                        .findFirst()
                        .orElse(null);
                }
            })
            .bind(DriverMapping::getTeamId, DriverMapping::setTeamId);
        teamColumn.setEditorComponent(teamEditorField);

        reserveField.addValueChangeListener(ev -> {
            if (ev.getValue()) {
                teamEditorField.setValue(null);
                teamEditorField.setEnabled(false);
                champTeamEditorField.setValue("None");
                champTeamEditorField.setEnabled(false);
            } else {
                teamEditorField.setEnabled(true);
                champTeamEditorField.setEnabled(true);
            }
        });

        editor.addOpenListener(ev -> {
            DriverMapping item = ev.getItem();
            if (item.isReserve()) {
                teamEditorField.setValue(null);
                teamEditorField.setEnabled(false);
                champTeamEditorField.setValue("None");
                champTeamEditorField.setEnabled(false);
            } else {
                teamEditorField.setEnabled(true);
                champTeamEditorField.setEnabled(true);
            }
        });

        tierEditorField.setItemLabelGenerator(Tier::getName);
        tierEditorField.setWidthFull();
        binder.forField(tierEditorField).bind(DriverMapping::getTier, DriverMapping::setTier);
        tiersColumn.setEditorComponent(tierEditorField);

        Button saveButton = new Button("Save", e -> {
            try {
                DriverMapping item = editor.getItem();
                editor.save();
                
                if (item.isReserve()) {
                    item.setChampionshipTeam(null);
                }
                
                checkTeamCapacityAndSave(item, () -> {
                    if (!validateChampionshipTeamConstraints(item)) {
                        editor.cancel();
                        updateData();
                        return;
                    }
                    driverMappingRepository.save(item);
                    telemetryProcessingService.refreshDriverMappings(league.getId());
                    
                    // Recalculate standings for all tiers of the league to ensure consistency
                    List<Tier> tiers = tierRepository.findByLeague(league);
                    for (Tier t : tiers) {
                        telemetryProcessingService.recalculateStandings(t.getId());
                    }
                    
                    Notification.show("Driver and standings updated!", 3000, Notification.Position.TOP_CENTER);
                    updateData();
                });
            } finally {
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        
        Button cancelButton = new Button("Cancel", e -> {
            editor.cancel();
            updateData();
        });
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        
        mappingGrid.addComponentColumn(item -> {
            HorizontalLayout actions = new HorizontalLayout();
            
            Button editButton = new Button("Edit");
            editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editButton.addClickListener(e -> {
                if (editor.isOpen()) editor.cancel();
                mappingGrid.getEditor().editItem(item);
            });

            Button copyBtn = new Button("Copy");
            copyBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
            copyBtn.addClickListener(e -> {
                if (editor.isOpen()) editor.cancel();
                showCopyDriverDialog(item);
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

            actions.add(editButton, copyBtn, deleteBtn);
            actions.setVisible(securityService.getAuthenticatedUser().isPresent());
            return actions;
        }).setEditorComponent(new HorizontalLayout(saveButton, cancelButton));

        mappingGrid.setItems(java.util.Collections.emptyList());
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        isInitializing = true;
        try {
            league = leagueRepository.findByIdWithUser(parameter).orElseThrow();
            seasonName.setText("Season: " + league.getName());
            seasonNameField.setValue(league.getName() != null ? league.getName() : "");
            hideAiCheckbox.setValue(league.isHideAi());
            showTyreWearCheckbox.setValue(league.isShowTyreWear());
            showErsCheckbox.setValue(league.isShowErs());
            minLapsPctField.setValue(league.getMinLapsPct() != null ? league.getMinLapsPct() : 60);
            carTypeCombo.setValue(league.getCarType() != null ? league.getCarType() : "F1 25");
            
            useChampionshipTeamsCheckbox.setValue(Boolean.TRUE.equals(league.getUseChampionshipTeams()));
            teamANameField.setValue(league.getTeamAName() != null ? league.getTeamAName() : "");
            teamBNameField.setValue(league.getTeamBName() != null ? league.getTeamBName() : "");
            teamANameField.setEnabled(Boolean.TRUE.equals(league.getUseChampionshipTeams()));
            teamBNameField.setEnabled(Boolean.TRUE.equals(league.getUseChampionshipTeams()));
            
            if (league.getLogoBackgroundColor() != null) {
                hexColorField.setValue(league.getLogoBackgroundColor());
                colorPicker.setValue(league.getLogoBackgroundColor());
            } else {
                hexColorField.setValue("");
                colorPicker.setValue("#ffffff");
            }

            if (league.getAccentColor() != null) {
                hexAccentColorField.setValue(league.getAccentColor());
                accentColorPicker.setValue(league.getAccentColor());
            } else {
                hexAccentColorField.setValue("");
                accentColorPicker.setValue("#eef30d");
            }
            
            youtubeField.setValue(league.getYoutubeHandle() != null ? league.getYoutubeHandle() : "");
            tiktokField.setValue(league.getTiktokHandle() != null ? league.getTiktokHandle() : "");
            xField.setValue(league.getXHandle() != null ? league.getXHandle() : "");
            instagramField.setValue(league.getInstagramHandle() != null ? league.getInstagramHandle() : "");
            twitchField.setValue(league.getTwitchHandle() != null ? league.getTwitchHandle() : "");
            
            updateLogo();
            
            boolean isOwner = securityService.getAuthenticatedUserEntity()
                    .map(user -> league.getUser() != null && league.getUser().getId().equals(user.getId()))
                    .orElse(false);
            logoUploadLayout.setVisible(isOwner);
            
            updateTierSelector();
            
            // Hide admin-only features if not logged in
            boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
            recalculateBtn.setVisible(loggedIn);
            addManualWeekendBtn.setVisible(loggedIn);
            addManualDriverBtn.setVisible(loggedIn);
            deleteSelectedMappingsBtn.setVisible(loggedIn);
            addSessionTypeBtn.setVisible(loggedIn);
            savePointsBtn.setVisible(loggedIn);
            deleteSessionBtn.setVisible(loggedIn);
            addExtraRuleBtn.setVisible(loggedIn);
            if (extraRulesActionColumn != null) {
                extraRulesActionColumn.setVisible(loggedIn);
            }
            if (extraRulesExpressionColumn != null) {
                extraRulesExpressionColumn.setVisible(loggedIn);
            }
            seasonNameField.setVisible(loggedIn);
            hideAiCheckbox.setVisible(loggedIn);
            showTyreWearCheckbox.setVisible(loggedIn);
            showErsCheckbox.setVisible(loggedIn);
            minLapsPctField.setVisible(loggedIn);
            carTypeCombo.setVisible(loggedIn);
            useChampionshipTeamsCheckbox.setVisible(loggedIn);
            teamANameField.setVisible(loggedIn);
            teamBNameField.setVisible(loggedIn);
            
            settingsTab.setVisible(loggedIn);
            tiersToolbar.setVisible(loggedIn);
            if (tokenColumn != null) {
                tokenColumn.setVisible(loggedIn);
            }
            if (tierActionsColumn != null) {
                tierActionsColumn.setVisible(loggedIn);
            }
            tiersTitle.setText(loggedIn ? "Manage Tiers" : "Tiers");
            
            if (!loggedIn && mainTabs.getSelectedTab() == settingsTab) {
                mainTabs.setSelectedTab(raceWeekendsTab);
            }
            
            tiersLayout.setVisible(false);
            
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
        tierEditorField.setItems(leagueTiers);
    }

    private void refreshTiersList() {
        if (league != null) {
            tierGrid.setItems(tierRepository.findByLeagueOrderByNameAsc(league));
        }
    }

    private void updateData() {
        if (league == null || selectedTier == null) return;

        teamEditorCombo.setItems(getTeamsForLeague());
        teamEditorField.setItems(getTeamsForLeague());

        this.currentEvents = eventRepository.findByTier(selectedTier);
        eventGrid.setItems(this.currentEvents);
        
        List<DriverStanding> standings = driverStandingRepository.findByTier(selectedTier);
        if (league.isHideAi()) {
            standings = standings.stream().filter(s -> !s.isAi()).toList();
        }
        
        driverGrid.setItems(standings.stream()
                .sorted(Comparator.comparing((DriverStanding ds) -> ds.getPoints() != null ? ds.getPoints() : 0).reversed())
                .collect(java.util.stream.Collectors.toList()));
        
        teamGrid.setItems(teamStandingRepository.findByTier(selectedTier).stream()
                .sorted(Comparator.comparing((TeamStanding ts) -> ts.getPoints() != null ? ts.getPoints() : 0).reversed())
                .collect(java.util.stream.Collectors.toList()));
        
        mappingGrid.setItems(driverMappingRepository.findByTier(selectedTier).stream()
                .sorted(Comparator.comparing(m -> m.getOverriddenName() != null ? m.getOverriddenName() : m.getTelemetryName()))
                .collect(Collectors.toList()));

        // Update League-wide Team Standings (dynamic aggregation over all tiers)
        List<TeamStanding> allTiersTeamStandings = new ArrayList<>();
        for (Tier tier : leagueTiers) {
            allTiersTeamStandings.addAll(teamStandingRepository.findByTier(tier));
        }
        java.util.Map<String, Integer> leagueTeamPoints = allTiersTeamStandings.stream()
                .collect(Collectors.groupingBy(
                        TeamStanding::getTeamName,
                        Collectors.summingInt(ts -> ts.getPoints() != null ? ts.getPoints() : 0)
                ));
        List<TeamStanding> leagueTeamStandings = leagueTeamPoints.entrySet().stream()
                .map(entry -> {
                    TeamStanding ts = new TeamStanding();
                    ts.setTeamName(entry.getKey());
                    ts.setPoints(entry.getValue());
                    return ts;
                })
                .sorted(Comparator.comparing((TeamStanding ts) -> ts.getPoints() != null ? ts.getPoints() : 0).reversed())
                .collect(Collectors.toList());
        leagueTeamGrid.setItems(leagueTeamStandings);

        updatePenaltiesData();
        updateLeaguePenaltiesData();

        boolean teamsEnabled = league != null && Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
            && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
            && league.getTeamBName() != null && !league.getTeamBName().isEmpty();
        
        if (champTeamColumn != null) {
            champTeamColumn.setVisible(teamsEnabled);
        }
        champTeamsTab.setVisible(teamsEnabled);
        leagueChampTeamsTab.setVisible(teamsEnabled);

        if (!teamsEnabled) {
            String selectedLabel = standingsTabs.getSelectedTab() != null ? standingsTabs.getSelectedTab().getLabel() : "";
            if (selectedLabel.equals("Championship Teams") || selectedLabel.equals("League Championship Teams")) {
                standingsTabs.setSelectedIndex(0); // Select Drivers
            }
        } else {
            // Populate tier-level Championship Team standings
            List<ChampionshipTeamStanding> ctsList = championshipTeamStandingRepository.findByTier(selectedTier);
            ctsList.sort(Comparator.comparing((ChampionshipTeamStanding cts) -> cts.getPoints() != null ? cts.getPoints() : 0).reversed());
            champTeamGrid.setItems(ctsList);

            // Populate league-wide Championship Team standings (aggregate over all tiers)
            List<ChampionshipTeamStanding> allTiersCts = new ArrayList<>();
            for (Tier tier : leagueTiers) {
                allTiersCts.addAll(championshipTeamStandingRepository.findByTier(tier));
            }
            Map<String, Integer> leagueCtsPoints = allTiersCts.stream()
                .collect(Collectors.groupingBy(
                    ChampionshipTeamStanding::getChampionshipTeam,
                    Collectors.summingInt(cts -> cts.getPoints() != null ? cts.getPoints() : 0)
                ));
            List<ChampionshipTeamStanding> leagueCtsStandings = leagueCtsPoints.entrySet().stream()
                .map(entry -> {
                    ChampionshipTeamStanding cts = new ChampionshipTeamStanding();
                    cts.setChampionshipTeam(entry.getKey());
                    cts.setPoints(entry.getValue());
                    return cts;
                })
                .sorted(Comparator.comparing((ChampionshipTeamStanding cts) -> cts.getPoints() != null ? cts.getPoints() : 0).reversed())
                .collect(Collectors.toList());
            leagueChampTeamGrid.setItems(leagueCtsStandings);
        }

        refreshPointsTabs();
        updateCalendarView();
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
            // Generate defaults for 1-20
            boolean isRace = (type >= 15 && type <= 17);
            boolean isSprint = type == 19;
            int[] racePoints = {25, 18, 15, 12, 10, 8, 6, 4, 2, 1};
            int[] sprintPoints = {8, 7, 6, 5, 4, 3, 2, 1};
            for (int p = 1; p <= 20; p++) {
                SessionPointConfig c = new SessionPointConfig();
                c.setLeague(league);
                c.setSessionType(type);
                c.setPosition(p);
                int points = 0;
                if (isRace && p <= 10) points = racePoints[p-1];
                if (isSprint && p <= 8) points = sprintPoints[p-1];
                c.setPoints(points);
                currentEditingConfigs.add(c);
            }
        } else {
            // Use existing DB configs
            dbConfigs.forEach(db -> {
                SessionPointConfig clone = new SessionPointConfig();
                clone.setId(db.getId());
                clone.setLeague(league);
                clone.setSessionType(db.getSessionType());
                clone.setPosition(db.getPosition());
                clone.setPoints(db.getPoints());
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
                    currentEditingConfigs.add(c);
                }
            }
            currentEditingConfigs.sort(Comparator.comparing(SessionPointConfig::getPosition));
        }
        
        pointsGrid.setItems(currentEditingConfigs);
        pointsChanged = false;
        List<ExtraPointRule> extraRules = extraPointRuleRepository.findByLeagueAndSessionType(league, type);
        extraPointRulesGrid.setItems(extraRules);
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
        });
        sessionPointConfigRepository.saveAll(currentEditingConfigs);
        
        // Recalculate standings for all tiers in this league
        List<Tier> tiers = tierRepository.findByLeague(league);
        for (Tier t : tiers) {
            telemetryProcessingService.recalculateStandings(t.getId());
        }
        
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
            
            // Recalculate standings for all tiers in this league
            List<Tier> tiers = tierRepository.findByLeague(league);
            for (Tier t : tiers) {
                telemetryProcessingService.recalculateStandings(t.getId());
            }

            selectedSessionType = null;
            updateData();
            Notification.show("Overrides removed", 3000, Notification.Position.TOP_CENTER);
        });
        dialog.open();
    }

    private void showAddExtraPointRuleDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Extra Point Rule");

        TextField nameField = new TextField("Rule Name");
        nameField.setRequired(true);
        nameField.setWidthFull();

        ComboBox<ExtraPointRule.Metric> metricCombo = new ComboBox<>("Metric");
        metricCombo.setItems(ExtraPointRule.Metric.values());
        metricCombo.setItemLabelGenerator(ExtraPointRule.Metric::getDisplayName);
        metricCombo.setWidthFull();
        metricCombo.setRequired(true);

        TextField metricExpressionField = new TextField("SpEL Expression");
        metricExpressionField.setWidthFull();
        metricExpressionField.setRequired(true);

        ComboBox<ExtraPointRule.RuleType> ruleTypeCombo = new ComboBox<>("Rule Type");
        ruleTypeCombo.setItems(ExtraPointRule.RuleType.values());
        ruleTypeCombo.setItemLabelGenerator(ExtraPointRule.RuleType::getDisplayName);
        ruleTypeCombo.setWidthFull();
        ruleTypeCombo.setRequired(true);

        com.vaadin.flow.component.textfield.NumberField thresholdField = new com.vaadin.flow.component.textfield.NumberField("Threshold Value");
        thresholdField.setWidthFull();
        thresholdField.setVisible(false);

        com.vaadin.flow.component.textfield.IntegerField pointsField = new com.vaadin.flow.component.textfield.IntegerField("Points");
        pointsField.setValue(1);
        pointsField.setStepButtonsVisible(true);
        pointsField.setWidthFull();

        Checkbox mustFinishCb = new Checkbox("Must finish session", true);
        Checkbox onlyForPointScorersCb = new Checkbox("Only for point scorers (e.g. top 10)", true);
        Checkbox excludeAiCb = new Checkbox("Exclude AI drivers", true);

        metricCombo.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                if (nameField.getValue().isEmpty()) {
                    nameField.setValue(e.getValue().getDisplayName());
                }
                if (e.getValue() == ExtraPointRule.Metric.CUSTOM) {
                    metricExpressionField.setReadOnly(false);
                    metricExpressionField.setValue("");
                } else {
                    metricExpressionField.setValue(e.getValue().getDefaultExpression());
                    metricExpressionField.setReadOnly(true);
                }
                switch (e.getValue()) {
                    case PLACES_GAINED:
                        ruleTypeCombo.setValue(ExtraPointRule.RuleType.HIGHEST_VALUE);
                        break;
                    case FASTEST_LAP:
                        ruleTypeCombo.setValue(ExtraPointRule.RuleType.LOWEST_VALUE);
                        break;
                    case PENALTIES:
                    case WARNINGS:
                    case PENALTIES_AND_WARNINGS:
                        ruleTypeCombo.setValue(ExtraPointRule.RuleType.THRESHOLD_BELOW);
                        thresholdField.setValue(0.0);
                        break;
                    case GAP_TO_PREVIOUS:
                        ruleTypeCombo.setValue(ExtraPointRule.RuleType.LOWEST_VALUE);
                        break;
                    case CUSTOM:
                        break;
                }
            }
        });

        ruleTypeCombo.addValueChangeListener(e -> {
            boolean isThreshold = e.getValue() == ExtraPointRule.RuleType.THRESHOLD_BELOW || e.getValue() == ExtraPointRule.RuleType.THRESHOLD_ABOVE;
            thresholdField.setVisible(isThreshold);
        });

        VerticalLayout dialogLayout = new VerticalLayout(
            nameField, metricCombo, metricExpressionField, ruleTypeCombo, thresholdField, pointsField,
            mustFinishCb, onlyForPointScorersCb, excludeAiCb
        );
        dialog.add(dialogLayout);

        Button saveBtn = new Button("Add", ev -> {
            if (nameField.getValue().isEmpty() || metricCombo.getValue() == null || metricExpressionField.getValue().isEmpty() || ruleTypeCombo.getValue() == null) {
                Notification.show("Please fill in all required fields", 3000, Notification.Position.TOP_CENTER);
                return;
            }

            String exprStr = metricExpressionField.getValue();
            try {
                org.springframework.expression.Expression expression = 
                    new org.springframework.expression.spel.standard.SpelExpressionParser().parseExpression(exprStr);
                
                // Create dummy DriverResult objects with non-null property values to dry-run evaluate the expression
                DriverResult dummyDriver = new DriverResult();
                dummyDriver.setPosition(1);
                dummyDriver.setGridPosition(2);
                dummyDriver.setBestLapTime(90.0f);
                dummyDriver.setPenalties(0);
                dummyDriver.setWarnings(0);
                dummyDriver.setTotalTime(3600.0);
                dummyDriver.setNumLaps(50);
                dummyDriver.setAi(false);
                dummyDriver.setDriverName("Test");
                dummyDriver.setTelemetryName("Test");
                dummyDriver.setResultStatus(3);
                
                DriverResult prevDriver = new DriverResult();
                prevDriver.setPosition(1);
                prevDriver.setGridPosition(2);
                prevDriver.setBestLapTime(91.0f);
                prevDriver.setPenalties(0);
                prevDriver.setWarnings(0);
                prevDriver.setTotalTime(3599.0);
                prevDriver.setNumLaps(50);
                prevDriver.setAi(false);
                prevDriver.setDriverName("Prev");
                prevDriver.setTelemetryName("Prev");
                prevDriver.setResultStatus(3);
                
                SessionResult dummySession = new SessionResult();
                dummySession.setSessionType(20);
                dummySession.setTrackId("austria");

                org.springframework.expression.spel.support.StandardEvaluationContext context = 
                    new org.springframework.expression.spel.support.StandardEvaluationContext(dummyDriver);
                context.setVariable("driver", dummyDriver);
                context.setVariable("session", dummySession);
                context.setVariable("previous", prevDriver);
                
                expression.getValue(context);
            } catch (Exception ex) {
                Notification.show("Invalid SpEL expression: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                return;
            }

            ExtraPointRule rule = new ExtraPointRule();
            rule.setLeague(league);
            rule.setSessionType(selectedSessionType);
            rule.setRuleName(nameField.getValue());
            rule.setMetric(metricCombo.getValue());
            rule.setMetricExpression(metricExpressionField.getValue());
            rule.setRuleType(ruleTypeCombo.getValue());
            rule.setThresholdValue(thresholdField.getValue());
            rule.setPoints(pointsField.getValue() != null ? pointsField.getValue() : 0);
            rule.setMustFinish(mustFinishCb.getValue());
            rule.setOnlyForPointScorers(onlyForPointScorersCb.getValue());
            rule.setExcludeAi(excludeAiCb.getValue());

            extraPointRuleRepository.save(rule);
            
            List<Tier> tiers = tierRepository.findByLeague(league);
            for (Tier t : tiers) {
                telemetryProcessingService.recalculateStandings(t.getId());
            }

            updateData();
            dialog.close();
            Notification.show("Extra point rule added and standings recalculated", 3000, Notification.Position.TOP_CENTER);
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
        dialog.open();
    }

    private void configurePenaltyGrid(Grid<PenaltyStandingRow> grid) {
        grid.addComponentColumn(ps -> {
            HorizontalLayout nameLayout = new HorizontalLayout();
            nameLayout.setAlignItems(Alignment.CENTER);
            nameLayout.setSpacing(false);

            Span flagSpan = new Span(CountryProvider.getFlagByName(ps.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            nameLayout.add(flagSpan);

            if (ps.getRaceNumber() != null && ps.getRaceNumber() > 0) {
                Span raceNum = new Span("#" + ps.getRaceNumber());
                raceNum.getStyle().set("color", "var(--lumo-secondary-text-color)");
                raceNum.getStyle().set("font-size", "0.8em");
                raceNum.getStyle().set("margin-right", "var(--lumo-space-s)");
                nameLayout.add(raceNum);
            }

            Span name = new Span(ps.getDriverName());
            nameLayout.add(name);
            return nameLayout;
        }).setHeader("Driver").setSortable(true).setComparator(PenaltyStandingRow::getDriverName);

        grid.addColumn(PenaltyStandingRow::getTeamName).setHeader("Team");
        grid.addColumn(PenaltyStandingRow::getIncidentCount).setHeader("Incidents").setSortable(true);
        grid.addColumn(PenaltyStandingRow::getTotalGivenSeconds).setHeader("Total Seconds").setSortable(true);
        grid.addColumn(PenaltyStandingRow::getTotalPointsDeducted).setHeader("Points Deducted").setSortable(true);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
    }

    private void updatePenaltiesData() {
        if (selectedTier == null) return;
        List<ManualPenalty> penalties = manualPenaltyRepository.findBySessionResultTier(selectedTier);
        List<PenaltyStandingRow> rows = buildPenaltyStandingRows(penalties, selectedTier);
        penaltiesGrid.setItems(rows);
    }

    private void updateLeaguePenaltiesData() {
        if (league == null) return;
        List<ManualPenalty> penalties = manualPenaltyRepository.findBySessionResultTierLeague(league);
        List<PenaltyStandingRow> rows = buildPenaltyStandingRows(penalties, null);
        leaguePenaltiesGrid.setItems(rows);
    }

    private List<PenaltyStandingRow> buildPenaltyStandingRows(List<ManualPenalty> penalties, Tier tier) {
        Map<DriverMapping, List<ManualPenalty>> grouped = penalties.stream()
                .collect(Collectors.groupingBy(ManualPenalty::getDriverMapping));

        // Pre-fetch standings to avoid N+1 queries
        Map<String, String> driverToTeamMap = new HashMap<>();
        Map<String, List<DriverStanding>> groupedStandings = new HashMap<>();

        if (tier != null) {
            List<DriverStanding> standings = driverStandingRepository.findByTier(tier);
            for (DriverStanding ds : standings) {
                String key = ds.getDriverName() + "|" + ds.getRaceNumber() + "|" + ds.getCountry();
                driverToTeamMap.put(key, ds.getTeamName());
            }
        } else if (league != null) {
            List<DriverStanding> allStandings = new ArrayList<>();
            for (Tier t : leagueTiers) {
                allStandings.addAll(driverStandingRepository.findByTier(t));
            }
            groupedStandings = allStandings.stream()
                    .collect(Collectors.groupingBy(
                            ds -> ds.getDriverName() + "|" + ds.getRaceNumber() + "|" + ds.getCountry()
                    ));
        }

        List<PenaltyStandingRow> rows = new ArrayList<>();
        for (Map.Entry<DriverMapping, List<ManualPenalty>> entry : grouped.entrySet()) {
            DriverMapping mapping = entry.getKey();
            List<ManualPenalty> list = entry.getValue();

            String name = mapping.getOverriddenName() != null && !mapping.getOverriddenName().isEmpty() 
                    ? mapping.getOverriddenName() 
                    : mapping.getTelemetryName();

            long incidentCount = list.stream()
                    .filter(p -> (p.getSeconds() != null && p.getSeconds() > 0) || (p.getPointDeduction() != null && p.getPointDeduction() > 0))
                    .count();

            if (incidentCount == 0) {
                continue;
            }

            long totalPointsDeducted = list.stream()
                    .mapToLong(p -> p.getPointDeduction() != null ? p.getPointDeduction() : 0)
                    .sum();

            long totalGivenSeconds = list.stream()
                    .mapToLong(p -> p.getSeconds() != null && p.getSeconds() > 0 ? p.getSeconds() : 0)
                    .sum();

            String teamName = "Unknown";
            String lookupKey = name + "|" + mapping.getRaceNumber() + "|" + mapping.getCountry();

            if (tier != null) {
                teamName = driverToTeamMap.getOrDefault(lookupKey, "Unknown");
            } else {
                List<DriverStanding> standings = groupedStandings.getOrDefault(lookupKey, Collections.emptyList());
                teamName = standings.stream()
                        .map(DriverStanding::getTeamName)
                        .filter(t -> t != null && !t.isEmpty())
                        .distinct()
                        .collect(Collectors.joining(", "));
                if (teamName.isEmpty()) teamName = "Unknown";
            }

            PenaltyStandingRow row = new PenaltyStandingRow();
            row.setDriverName(name);
            row.setTeamName(teamName);
            row.setCountry(mapping.getCountry());
            row.setRaceNumber(mapping.getRaceNumber());
            row.setIncidentCount(incidentCount);
            row.setTotalPointsDeducted(totalPointsDeducted);
            row.setTotalGivenSeconds(totalGivenSeconds);
            rows.add(row);
        }

        rows.sort((r1, r2) -> {
            int comp = Long.compare(r2.getTotalGivenSeconds(), r1.getTotalGivenSeconds());
            if (comp != 0) return comp;
            comp = Long.compare(r2.getTotalPointsDeducted(), r1.getTotalPointsDeducted());
            if (comp != 0) return comp;
            comp = Long.compare(r2.getIncidentCount(), r1.getIncidentCount());
            if (comp != 0) return comp;
            return r1.getDriverName().compareToIgnoreCase(r2.getDriverName());
        });

        return rows;
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

    private void saveBackgroundColor(String color) {
        if (league != null && !isInitializing) {
            league.setLogoBackgroundColor(color);
            league = leagueRepository.save(league);
            updateLogo();
        }
    }

    private void saveAccentColor(String color) {
        if (league != null && !isInitializing) {
            league.setAccentColor(color);
            league = leagueRepository.save(league);
        }
    }

    private String getCarTypeForLeague() {
        return league != null && league.getCarType() != null ? league.getCarType() : "F1 25";
    }

    private List<TeamMapping> getTeamsForLeague() {
        String carType = getCarTypeForLeague();
        List<TeamMapping> teams = teamMappingRepository.findByCarType(carType);
        if ("F1 26".equals(carType)) {
            teams = teams.stream().filter(t -> t.getTeamId() != null && t.getTeamId() >= 400).toList();
        }
        return teams;
    }

    private List<DriverMapping> getTeamDriversInTier(Tier tier, Integer teamId, Long excludeDriverMappingId) {
        if (tier == null || teamId == null) {
            return java.util.Collections.emptyList();
        }
        return driverMappingRepository.findByTier(tier).stream()
                .filter(m -> !Objects.equals(m.getId(), excludeDriverMappingId))
                .filter(m -> !m.isReserve())
                .filter(m -> Objects.equals(m.getTeamId(), teamId))
                .toList();
    }

    private boolean validateChampionshipTeamConstraints(DriverMapping mapping) {
        if (league == null || mapping == null || mapping.getTier() == null) return true;
        
        boolean teamsEnabled = Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
            && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
            && league.getTeamBName() != null && !league.getTeamBName().isEmpty();
            
        if (!teamsEnabled) return true;
        
        if (mapping.isReserve()) {
            return true;
        }
        
        // Simulate mappings in the tier
        List<DriverMapping> simulated = new ArrayList<>();
        boolean found = false;
        for (DriverMapping m : driverMappingRepository.findByTier(mapping.getTier())) {
            if (mapping.getId() != null && Objects.equals(m.getId(), mapping.getId())) {
                simulated.add(mapping);
                found = true;
            } else {
                simulated.add(m);
            }
        }
        if (!found) {
            simulated.add(mapping);
        }
        
        // Enforce reserve cleanups on all mappings in simulation (just to be safe)
        for (DriverMapping m : simulated) {
            if (m.isReserve()) {
                m.setChampionshipTeam(null);
            }
        }
        
        // 1. Max 11 regular players per team
        long countA = simulated.stream()
            .filter(m -> !m.isReserve() && "A".equals(m.getChampionshipTeam()))
            .count();
            
        long countB = simulated.stream()
            .filter(m -> !m.isReserve() && "B".equals(m.getChampionshipTeam()))
            .count();
            
        String teamAName = league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
        String teamBName = league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
        
        if (countA > 11) {
            Notification notification = Notification.show(teamAName + " cannot have more than 11 regular players.", 5000, Notification.Position.TOP_CENTER);
            notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            return false;
        }
        if (countB > 11) {
            Notification notification = Notification.show(teamBName + " cannot have more than 11 regular players.", 5000, Notification.Position.TOP_CENTER);
            notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            return false;
        }
        
        // 2. Only 1 split constructor
        Map<Integer, Set<String>> constructorTeams = new HashMap<>();
        for (DriverMapping m : simulated) {
            if (!m.isReserve() && m.getTeamId() != null && m.getChampionshipTeam() != null) {
                constructorTeams.computeIfAbsent(m.getTeamId(), k -> new java.util.HashSet<>())
                                .add(m.getChampionshipTeam());
            }
        }
        
        long splitCount = constructorTeams.values().stream()
            .filter(teams -> teams.contains("A") && teams.contains("B"))
            .count();
            
        if (splitCount > 1) {
            Notification notification = Notification.show("Only 1 constructor can have drivers split between both teams.", 5000, Notification.Position.TOP_CENTER);
            notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            return false;
        }
        
        return true;
    }

    private void checkTeamCapacityAndSave(DriverMapping mapping, Runnable saveAction) {
        if (mapping.isReserve() || mapping.getTeamId() == null || mapping.getTier() == null) {
            saveAction.run();
            return;
        }

        List<DriverMapping> teamDrivers = getTeamDriversInTier(mapping.getTier(), mapping.getTeamId(), mapping.getId());
        if (teamDrivers.size() >= 2) {
            Dialog replacementDialog = new Dialog();
            replacementDialog.setHeaderTitle("Team is Full");
            
            VerticalLayout layout = new VerticalLayout();
            layout.add(new Span("The team already has 2 active drivers in this tier:"));
            for (DriverMapping td : teamDrivers) {
                String displayName = td.getOverriddenName() != null && !td.getOverriddenName().isEmpty()
                        ? td.getOverriddenName() : td.getTelemetryName();
                layout.add(new Span("- " + displayName));
            }
            layout.add(new Span("Choose which driver to replace, or Cancel:"));
            
            HorizontalLayout buttonsLayout = new HorizontalLayout();
            
            List<Button> actionButtons = new ArrayList<>();
            for (DriverMapping td : teamDrivers) {
                String displayName = td.getOverriddenName() != null && !td.getOverriddenName().isEmpty()
                        ? td.getOverriddenName() : td.getTelemetryName();
                Button replaceBtn = new Button("Replace " + displayName);
                replaceBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                replaceBtn.addClickListener(ev -> {
                    actionButtons.forEach(b -> b.setEnabled(false));
                    layout.add(new Span("Processing replacement..."));
                    
                    td.setTeamId(null);
                    td.setReserve(true);
                    td.setChampionshipTeam(null);
                    driverMappingRepository.save(td);
                    
                    saveAction.run();
                    replacementDialog.close();
                });
                actionButtons.add(replaceBtn);
                buttonsLayout.add(replaceBtn);
            }
            
            Button cancelBtn = new Button("Cancel", ev -> {
                replacementDialog.close();
                updateData();
            });
            actionButtons.add(cancelBtn);
            buttonsLayout.add(cancelBtn);
            
            layout.add(buttonsLayout);
            replacementDialog.add(layout);
            replacementDialog.open();
        } else {
            saveAction.run();
        }
    }

    private void showCopyDriverDialog(DriverMapping source) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Copy Driver to Another Tier");

        ComboBox<Tier> targetTierField = new ComboBox<>("Target Tier");
        targetTierField.setItems(tierRepository.findByLeagueOrderByNameAsc(league).stream()
                .filter(t -> !Objects.equals(t.getId(), source.getTier() != null ? source.getTier().getId() : null))
                .toList());
        targetTierField.setItemLabelGenerator(Tier::getName);
        targetTierField.setWidthFull();

        Checkbox reserveField = new Checkbox("Reserve");
        reserveField.setValue(source.isReserve());
        
        ComboBox<TeamMapping> teamCombo = new ComboBox<>("Team");
        teamCombo.setItems(getTeamsForLeague());
        teamCombo.setItemLabelGenerator(TeamMapping::getTeamName);
        teamCombo.setWidthFull();

        ComboBox<String> champTeamCombo = new ComboBox<>("Championship Team");
        champTeamCombo.setItems("A", "B", "None");
        champTeamCombo.setItemLabelGenerator(val -> {
            if ("A".equals(val)) {
                return league != null && league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
            } else if ("B".equals(val)) {
                return league != null && league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
            }
            return "None";
        });
        champTeamCombo.setValue(source.getChampionshipTeam() != null ? source.getChampionshipTeam() : "None");
        champTeamCombo.setWidthFull();
        boolean teamsEnabled = Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
            && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
            && league.getTeamBName() != null && !league.getTeamBName().isEmpty();
        champTeamCombo.setVisible(teamsEnabled);

        if (source.isReserve()) {
            teamCombo.setValue(null);
            teamCombo.setEnabled(false);
            champTeamCombo.setValue("None");
            champTeamCombo.setEnabled(false);
        }

        reserveField.addValueChangeListener(ev -> {
            if (ev.getValue()) {
                teamCombo.setValue(null);
                teamCombo.setEnabled(false);
                champTeamCombo.setValue("None");
                champTeamCombo.setEnabled(false);
            } else {
                teamCombo.setEnabled(true);
                champTeamCombo.setEnabled(true);
            }
        });

        VerticalLayout layout = new VerticalLayout(targetTierField, reserveField, teamCombo, champTeamCombo);
        dialog.add(layout);

        Button saveBtn = new Button("Copy", ev -> {
            if (targetTierField.getValue() == null) {
                Notification.show("Please select a target tier", 3000, Notification.Position.TOP_CENTER);
                return;
            }
            
            Optional<DriverMapping> existing = driverMappingRepository.findByTierAndTelemetryNameAndRaceNumberAndDriverIdAndCountry(
                targetTierField.getValue(),
                source.getTelemetryName(),
                source.getRaceNumber(),
                source.getDriverId(),
                source.getCountry()
            );
            if (existing.isPresent()) {
                Notification notification = Notification.show("Driver is already mapped in the target tier!", 3000, Notification.Position.TOP_CENTER);
                notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
                return;
            }
            
            DriverMapping copy = new DriverMapping();
            copy.setLeague(source.getLeague());
            copy.setTelemetryName(source.getTelemetryName());
            copy.setRaceNumber(source.getRaceNumber());
            copy.setDriverId(source.getDriverId());
            copy.setOverriddenName(source.getOverriddenName());
            copy.setCountry(source.getCountry());
            copy.setTier(targetTierField.getValue());
            copy.setReserve(reserveField.getValue());
            copy.setTeamId(teamCombo.getValue() != null ? teamCombo.getValue().getTeamId() : null);
            if (teamsEnabled && !"None".equals(champTeamCombo.getValue()) && !copy.isReserve()) {
                copy.setChampionshipTeam(champTeamCombo.getValue());
            } else {
                copy.setChampionshipTeam(null);
            }

            checkTeamCapacityAndSave(copy, () -> {
                if (!validateChampionshipTeamConstraints(copy)) {
                    updateData();
                    return;
                }
                driverMappingRepository.save(copy);
                telemetryProcessingService.refreshDriverMappings(league.getId());
                
                List<Tier> tiers = tierRepository.findByLeague(league);
                for (Tier t : tiers) {
                    telemetryProcessingService.recalculateStandings(t.getId());
                }
                
                updateData();
                dialog.close();
                Notification.show("Driver copied successfully!", 3000, Notification.Position.TOP_CENTER);
            });
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), saveBtn);
        dialog.open();
    }

    private void moveEventUp(Event event) {
        if (selectedTier == null) return;
        List<Event> events = eventRepository.findByTier(selectedTier);
        int index = events.indexOf(event);
        if (index > 0) {
            for (int i = 0; i < events.size(); i++) {
                events.get(i).setDisplayOrder(i);
            }
            events.get(index).setDisplayOrder(index - 1);
            events.get(index - 1).setDisplayOrder(index);
            eventRepository.saveAll(events);
            updateData();
            Notification.show("Event order updated", 2000, Notification.Position.TOP_CENTER);
        }
    }

    private void moveEventDown(Event event) {
        if (selectedTier == null) return;
        List<Event> events = eventRepository.findByTier(selectedTier);
        int index = events.indexOf(event);
        if (index >= 0 && index < events.size() - 1) {
            for (int i = 0; i < events.size(); i++) {
                events.get(i).setDisplayOrder(i);
            }
            events.get(index).setDisplayOrder(index + 1);
            events.get(index + 1).setDisplayOrder(index);
            eventRepository.saveAll(events);
            updateData();
            Notification.show("Event order updated", 2000, Notification.Position.TOP_CENTER);
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

    private void updateCalendarView() {
        calendarLayout.removeAll();
        calendarLayout.setPadding(true);
        calendarLayout.setSpacing(true);
        calendarLayout.setAlignItems(Alignment.STRETCH);

        if (selectedTier == null) return;

        if (this.currentEvents == null || this.currentEvents.isEmpty()) {
            com.vaadin.flow.component.html.H3 title = new com.vaadin.flow.component.html.H3("Season Schedule & Calendar - " + selectedTier.getName());
            calendarLayout.add(title);
            Div emptyMessage = new Div();
            emptyMessage.setText("No race weekends scheduled yet.");
            emptyMessage.getStyle().set("color", "var(--lumo-secondary-text-color)");
            emptyMessage.getStyle().set("font-style", "italic");
            calendarLayout.add(emptyMessage);
            return;
        }

        // Download button
        Button downloadCalendarBtn = new Button("Download Calendar Image", com.vaadin.flow.component.icon.VaadinIcon.DOWNLOAD.create());
        downloadCalendarBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        downloadCalendarBtn.getStyle().set("margin-bottom", "15px");
        downloadCalendarBtn.getStyle().set("align-self", "flex-start");
        
        String tierNameSafe = selectedTier.getName().toLowerCase().replace(" ", "_");
        downloadCalendarBtn.addClickListener(e -> {
            getElement().executeJs(
                "if (!window.html2canvas) {" +
                "  const script = document.createElement('script');" +
                "  script.src = 'https://unpkg.com/html2canvas@1.4.1/dist/html2canvas.min.js';" +
                "  script.onload = () => { downloadCalendarPoster(); };" +
                "  document.head.appendChild(script);" +
                "} else {" +
                "  downloadCalendarPoster();" +
                "}" +
                "function downloadCalendarPoster() {" +
                "  const el = document.querySelector('.calendar-poster');" +
                "  if (el) {" +
                "    html2canvas(el, { useCORS: true, backgroundColor: null }).then(canvas => {" +
                "      const link = document.createElement('a');" +
                "      link.download = 'calendar_' + $0 + '.png';" +
                "      link.href = canvas.toDataURL('image/png');" +
                "      link.click();" +
                "    });" +
                "  }" +
                "}",
                tierNameSafe
            );
        });

        // Body content of the poster
        Div body = new Div();
        body.addClassName("results-poster-body");
        body.getStyle().set("justify-content", "center");

        Div container = new Div();
        container.addClassName("calendar-container");
        container.getStyle().set("justify-content", "center");

        for (int i = 0; i < this.currentEvents.size(); i++) {
            Event event = this.currentEvents.get(i);
            boolean hasResults = event.getSessionResults() != null && !event.getSessionResults().isEmpty();

            Div card = new Div();
            card.addClassName("calendar-card");

            String statusText;
            String statusBadgeClass;
            if ("FINAL".equalsIgnoreCase(event.getStatus())) {
                statusText = "Final";
                statusBadgeClass = "badge-final";
                card.addClassName("finalized");
            } else if ("PROVISIONAL_WARNING".equalsIgnoreCase(event.getStatus())) {
                statusText = "Provisional (Warning)";
                statusBadgeClass = "badge-provisional-warning";
                card.addClassName("provisional-warning");
            } else if (hasResults) {
                statusText = "Provisional";
                statusBadgeClass = "badge-provisional";
                card.addClassName("provisional");
            } else {
                statusText = "Upcoming";
                statusBadgeClass = "badge-upcoming";
                card.addClassName("upcoming");
            }

            // Month / Day Badge
            Div dateBadge = new Div();
            dateBadge.addClassName("calendar-badge");

            Div monthDiv = new Div();
            monthDiv.addClassName("badge-month");

            Div dayDiv = new Div();
            dayDiv.addClassName("badge-day");

            if (event.getPlannedDate() != null) {
                LocalDate date = event.getPlannedDate();
                monthDiv.setText(date.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH).toUpperCase());
                dayDiv.setText(String.valueOf(date.getDayOfMonth()));
            } else {
                monthDiv.setText("TBD");
                dayDiv.setText("--");
            }

            if (league != null && league.getAccentColor() != null && !league.getAccentColor().isEmpty()) {
                monthDiv.getStyle().set("background-color", league.getAccentColor());
                String textCol = isDarkColor(league.getAccentColor()) ? "#ffffff" : "#000000";
                monthDiv.getStyle().set("color", textCol);
            }

            dateBadge.add(monthDiv, dayDiv);

            // Round & Flag
            Div roundInfo = new Div();
            roundInfo.addClassName("card-round");
            String flag = getTrackCountryFlag(event.getTrackId());
            roundInfo.setText("ROUND " + (i + 1) + " " + flag);

            // Right content (Title, Track, Status Badge)
            Div eventTitle = new Div();
            eventTitle.addClassName("card-title");
            if (hasResults) {
                RouterLink link = new RouterLink(event.getEventName(), EventResultsView.class, event.getId());
                link.getStyle().set("color", "inherit");
                link.getStyle().set("text-decoration", "none");
                eventTitle.add(link);
            } else {
                eventTitle.setText(event.getEventName());
            }

            Div trackName = new Div();
            trackName.addClassName("card-track");
            try {
                int trackIdInt = Integer.parseInt(event.getTrackId());
                trackName.setText(TelemetryProcessingService.TRACK_NAMES.getOrDefault(trackIdInt, "Track " + event.getTrackId()));
            } catch (Exception ex) {
                trackName.setText("Track " + event.getTrackId());
            }

            Span statusSpan = new Span(statusText);
            statusSpan.addClassName("status-pill");
            statusSpan.addClassName(statusBadgeClass);

            VerticalLayout rightContent = new VerticalLayout(eventTitle, trackName, statusSpan);
            rightContent.setSpacing(false);
            rightContent.setPadding(false);
            rightContent.getStyle().set("margin", "0");
            rightContent.setAlignItems(Alignment.START);

            HorizontalLayout contentRow = new HorizontalLayout(dateBadge, rightContent);
            contentRow.setAlignItems(Alignment.CENTER);
            contentRow.setWidthFull();
            contentRow.setSpacing(true);

            // Sessions Info
            Div sessionsDiv = new Div();
            sessionsDiv.addClassName("card-sessions");
            if (hasResults) {
                java.util.Set<Integer> types = event.getSessionResults().stream()
                        .map(SessionResult::getSessionType)
                        .collect(Collectors.toSet());
                Map<Integer, Integer> sortOrder = Map.ofEntries(
                        Map.entry(1, 1), Map.entry(2, 2), Map.entry(3, 3), Map.entry(4, 4),
                        Map.entry(5, 5), Map.entry(6, 6), Map.entry(7, 7), Map.entry(8, 8), Map.entry(9, 9),
                        Map.entry(10, 10), Map.entry(11, 11), Map.entry(12, 12), Map.entry(13, 13), Map.entry(14, 14),
                        Map.entry(15, 15), Map.entry(16, 16), Map.entry(17, 17),
                        Map.entry(18, 18), Map.entry(19, 19)
                );
                String sessions = event.getSessionResults().stream()
                        .map(SessionResult::getSessionType)
                        .distinct()
                        .sorted(Comparator.comparingInt(type -> sortOrder.getOrDefault(type, 99)))
                        .map(type -> EventResultsView.getDynamicSessionName(type, types))
                        .collect(Collectors.joining(", "));
                sessionsDiv.setText("Completed: " + sessions);
            } else {
                sessionsDiv.setText("Upcoming race weekend");
            }

            card.add(roundInfo, contentRow, sessionsDiv);
            container.add(card);
        }

        body.add(container);

        // Create the base poster
        Div calendarPoster = createBasePoster("Season Schedule", body, "calendar-poster");

        calendarLayout.add(downloadCalendarBtn, calendarPoster);
    }

    private Div createBasePoster(String titleText, Div bodyContent, String posterClass) {
        if (league == null) return new Div();

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
        H4 subtitle = new H4(selectedTier != null ? selectedTier.getName().toUpperCase() : "");
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

    private boolean isDarkColor(String hexColor) {
        if (hexColor == null || !hexColor.startsWith("#") || hexColor.length() < 7) {
            return true;
        }
        try {
            int r = Integer.parseInt(hexColor.substring(1, 3), 16);
            int g = Integer.parseInt(hexColor.substring(3, 5), 16);
            int b = Integer.parseInt(hexColor.substring(5, 7), 16);
            double yiq = ((r * 299) + (g * 587) + (b * 114)) / 1000.0;
            return yiq < 128;
        } catch (Exception e) {
            return true;
        }
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
