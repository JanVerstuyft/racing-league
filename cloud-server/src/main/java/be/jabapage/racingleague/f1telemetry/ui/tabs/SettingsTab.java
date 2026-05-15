package be.jabapage.racingleague.f1telemetry.ui.tabs;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class SettingsTab extends VerticalLayout {

    private final LeagueRepository leagueRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;

    private League league;
    private final Checkbox hideAiCheckbox = new Checkbox("Hide AI Drivers");
    private final Checkbox showTyreWearCheckbox = new Checkbox("Show Tyre Wear on Live Leaderboard");
    private final Checkbox showErsCheckbox = new Checkbox("Show ERS on Live Leaderboard");
    private boolean isUpdating = false;
    private final Runnable onSettingsChanged;

    public SettingsTab(LeagueRepository leagueRepository,
                       TelemetryProcessingService telemetryProcessingService,
                       SecurityService securityService,
                       Runnable onSettingsChanged) {
        this.leagueRepository = leagueRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;
        this.onSettingsChanged = onSettingsChanged;

        setSizeFull();

        add(new H3("Season Settings"));
        add(hideAiCheckbox, showTyreWearCheckbox, showErsCheckbox);

        hideAiCheckbox.addValueChangeListener(e -> {
            if (league != null && !isUpdating) {
                league.setHideAi(e.getValue());
                leagueRepository.save(league);
                telemetryProcessingService.refreshHideAiSetting(league.getId());
                onSettingsChanged.run();
                Notification.show("AI visibility updated", 3000, Notification.Position.TOP_CENTER);
            }
        });

        showTyreWearCheckbox.addValueChangeListener(e -> {
            if (league != null && !isUpdating) {
                league.setShowTyreWear(e.getValue());
                leagueRepository.save(league);
                Notification.show("Tyre wear visibility updated", 3000, Notification.Position.TOP_CENTER);
            }
        });

        showErsCheckbox.addValueChangeListener(e -> {
            if (league != null && !isUpdating) {
                league.setShowErs(e.getValue());
                leagueRepository.save(league);
                Notification.show("ERS visibility updated", 3000, Notification.Position.TOP_CENTER);
            }
        });
    }

    public void setLeague(League league) {
        this.league = league;
        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        hideAiCheckbox.setVisible(loggedIn);
        showTyreWearCheckbox.setVisible(loggedIn);
        showErsCheckbox.setVisible(loggedIn);
        updateData();
    }

    public void updateData() {
        if (league == null) return;
        isUpdating = true;
        hideAiCheckbox.setValue(league.isHideAi());
        showTyreWearCheckbox.setValue(league.isShowTyreWear());
        showErsCheckbox.setValue(league.isShowErs());
        isUpdating = false;
    }
}
