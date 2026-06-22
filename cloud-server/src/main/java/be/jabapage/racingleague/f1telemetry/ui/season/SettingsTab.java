package be.jabapage.racingleague.f1telemetry.ui.season;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.LeagueLogo;
import be.jabapage.racingleague.f1telemetry.repository.LeagueLogoRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.util.ImageColorExtractor;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;

import java.io.IOException;

public class SettingsTab extends VerticalLayout {

    private final LeagueRepository leagueRepository;
    private final LeagueLogoRepository leagueLogoRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;
    private final Runnable onDataChanged;
    private final Runnable onLogoChanged;

    private League league;
    private boolean isInitializing = false;

    private final TextField seasonNameField = new TextField("Season Name");
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

    private final HorizontalLayout logoUploadLayout = new HorizontalLayout();

    public SettingsTab(LeagueRepository leagueRepository,
                       LeagueLogoRepository leagueLogoRepository,
                       TelemetryProcessingService telemetryProcessingService,
                       SecurityService securityService,
                       Runnable onDataChanged,
                       Runnable onLogoChanged) {
        this.leagueRepository = leagueRepository;
        this.leagueLogoRepository = leagueLogoRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;
        this.onDataChanged = onDataChanged;
        this.onLogoChanged = onLogoChanged;

        setSizeFull();
        setPadding(false);

        useChampionshipTeamsCheckbox.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setUseChampionshipTeams(e.getValue());
                league = leagueRepository.save(league);
                teamANameField.setEnabled(e.getValue());
                teamBNameField.setEnabled(e.getValue());
                onDataChanged.run();
                Notification.show("Championship Teams toggle updated", 3000, Notification.Position.TOP_CENTER);
            }
        });

        teamANameField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setTeamAName(e.getValue());
                league = leagueRepository.save(league);
                onDataChanged.run();
            }
        });

        teamBNameField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setTeamBName(e.getValue());
                league = leagueRepository.save(league);
                onDataChanged.run();
            }
        });

        hideAiCheckbox.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setHideAi(e.getValue());
                league = leagueRepository.save(league);
                telemetryProcessingService.refreshHideAiSetting(league.getId());
                onDataChanged.run();
                Notification.show("AI visibility updated", 3000, Notification.Position.TOP_CENTER);
            }
        });

        showTyreWearCheckbox.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setShowTyreWear(e.getValue());
                league = leagueRepository.save(league);
                Notification.show("Tyre wear visibility updated", 3000, Notification.Position.TOP_CENTER);
            }
        });

        showErsCheckbox.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setShowErs(e.getValue());
                league = leagueRepository.save(league);
                Notification.show("ERS visibility updated", 3000, Notification.Position.TOP_CENTER);
            }
        });
        
        seasonNameField.setWidth("300px");
        seasonNameField.addValueChangeListener(e -> {
            if (league != null && !isInitializing && e.getValue() != null && !e.getValue().isEmpty()) {
                league.setName(e.getValue());
                league = leagueRepository.save(league);
                onDataChanged.run();
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
                league = leagueRepository.save(league);
                Notification.show("Minimum laps percentage updated", 3000, Notification.Position.TOP_CENTER);
            }
        });

        carTypeCombo.setItems(java.util.List.of("F1 25", "F1 26"));
        carTypeCombo.setWidth("300px");
        carTypeCombo.addValueChangeListener(e -> {
            if (league != null && !isInitializing && e.getValue() != null) {
                league.setCarType(e.getValue());
                league = leagueRepository.save(league);
                onDataChanged.run();
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
                
                isInitializing = true;
                if (bgColor != null) {
                    hexColorField.setValue(bgColor);
                    colorPicker.setValue(bgColor);
                } else {
                    hexColorField.setValue("");
                    colorPicker.setValue("#ffffff");
                }
                isInitializing = false;
                
                onLogoChanged.run();
                Notification.show("Logo uploaded successfully!", 3000, Notification.Position.TOP_CENTER);
            } catch (IOException ex) {
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
                
                isInitializing = true;
                hexColorField.setValue("");
                colorPicker.setValue("#ffffff");
                isInitializing = false;
                
                onLogoChanged.run();
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
                league = leagueRepository.save(league);
            }
        });
        tiktokField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setTiktokHandle(e.getValue());
                league = leagueRepository.save(league);
            }
        });
        xField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setXHandle(e.getValue());
                league = leagueRepository.save(league);
            }
        });
        instagramField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setInstagramHandle(e.getValue());
                league = leagueRepository.save(league);
            }
        });
        twitchField.addValueChangeListener(e -> {
            if (league != null && !isInitializing) {
                league.setTwitchHandle(e.getValue());
                league = leagueRepository.save(league);
            }
        });

        Tab generalSettingsTab = new Tab("Season Settings");
        Tab uiTweaksTab = new Tab("UI Tweaks");
        Tabs settingsTabs = new Tabs(generalSettingsTab, uiTweaksTab);
        
        VerticalLayout generalSettingsContent = new VerticalLayout();
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
        
        VerticalLayout uiTweaksSettingsContent = new VerticalLayout();
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
            if (event.getSelectedTab() == null) return;
            boolean isGeneral = event.getSelectedTab().equals(generalSettingsTab);
            generalSettingsContent.setVisible(isGeneral);
            uiTweaksSettingsContent.setVisible(!isGeneral);
        });

        add(new H3("Settings"), settingsTabs, generalSettingsContent, uiTweaksSettingsContent);
    }

    private void saveBackgroundColor(String color) {
        if (league != null && !isInitializing) {
            league.setLogoBackgroundColor(color);
            league = leagueRepository.save(league);
            onLogoChanged.run();
        }
    }

    private void saveAccentColor(String color) {
        if (league != null && !isInitializing) {
            league.setAccentColor(color);
            league = leagueRepository.save(league);
        }
    }

    public void update(League league, boolean isOwner) {
        isInitializing = true;
        try {
            this.league = league;
            if (league == null) return;

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
            
            logoUploadLayout.setVisible(isOwner);
        } finally {
            isInitializing = false;
        }
    }
}
