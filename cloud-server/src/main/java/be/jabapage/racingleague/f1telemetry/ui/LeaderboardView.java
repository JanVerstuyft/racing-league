package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.model.DriverBoardState;
import be.jabapage.racingleague.f1telemetry.model.SessionInfo;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.Broadcaster;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.shared.Registration;

import java.util.List;

@PageTitle("Live Leaderboard | F1 Telemetry")
@Route(value = "leaderboard")
@AnonymousAllowed
public class LeaderboardView extends VerticalLayout implements HasUrlParameter<Long> {

    private final Broadcaster broadcaster;
    private final SecurityService securityService;
    private final TierRepository tierRepository;
    private final be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService telemetryProcessingService;
    private final Grid<DriverBoardState> grid = new Grid<>(DriverBoardState.class, false);
    private final H2 title = new H2("LIVE LEADERBOARD");
    private final Span scStatus = new Span();
    private final Span drsStatus = new Span();
    private final Icon weatherIcon = new Icon(VaadinIcon.SUN_O);
    private final Span weatherTemp = new Span();
    private final Checkbox keepScreenOn = new Checkbox("Keep Screen On");
    private final RouterLink backLink = new RouterLink("← Back to Season", SeasonDetailsView.class, 0L);
    private Registration leaderboardRegistration;
    private Registration sessionInfoRegistration;
    private java.util.Timer heartbeatTimer;
    private Long tierId;
    private SessionInfo currentSessionInfo;

    public LeaderboardView(Broadcaster broadcaster, SecurityService securityService, TierRepository tierRepository, be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService telemetryProcessingService) {
        this.broadcaster = broadcaster;
        this.securityService = securityService;
        this.tierRepository = tierRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        setSizeFull();

        configureGrid();

        drsStatus.getStyle().set("margin-left", "var(--lumo-space-m)");
        drsStatus.getStyle().set("font-weight", "bold");

        HorizontalLayout weatherLayout = new HorizontalLayout(weatherIcon, weatherTemp);
        weatherLayout.setSpacing(true);
        weatherLayout.setAlignItems(Alignment.CENTER);
        weatherLayout.getStyle().set("cursor", "pointer");
        weatherLayout.getStyle().set("margin-left", "var(--lumo-space-m)");
        weatherLayout.addClickListener(e -> showWeatherForecast());

        HorizontalLayout header = new HorizontalLayout(title, scStatus, drsStatus, weatherLayout, keepScreenOn);
        header.setAlignItems(Alignment.BASELINE);
        header.setSpacing(true);
        header.expand(title);

        HorizontalLayout nav = new HorizontalLayout(backLink);
        if (!securityService.getAuthenticatedUser().isPresent()) {
            nav.add(new RouterLink("Login", LoginView.class));
        }
        nav.add(new RouterLink("Documentation", DocumentationView.class));
        nav.setSpacing(true);

        add(nav, header, grid);
        
        setupWakeLockLogic();
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        this.tierId = parameter;
        tierRepository.findById(tierId).ifPresent(tier -> {
            backLink.setRoute(SeasonDetailsView.class, tier.getLeague().getId());
        });
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(state -> {
            int status = state.getResultStatus();
            if (status == 4) return "DNF";
            if (status == 5) return "DSQ";
            if (status == 6) return "NC";
            if (status == 7) return "RET";
            return state.getPosition();
        }).setHeader("Pos").setWidth("70px").setFlexGrow(0);
        
        grid.addComponentColumn(state -> {
            Span raceNum = new Span("#" + state.getRaceNumber());
            raceNum.getStyle().set("color", "var(--lumo-secondary-text-color)");
            raceNum.getStyle().set("font-size", "0.8em");
            raceNum.getStyle().set("margin-right", "var(--lumo-space-s)");

            Span name = new Span(state.getName());
            HorizontalLayout nameLayout = new HorizontalLayout(raceNum, name);
            nameLayout.setAlignItems(Alignment.CENTER);
            nameLayout.setSpacing(false);

            if (state.isAi()) {
                Span badge = new Span("AI");
                badge.getElement().getThemeList().add("badge contrast small");
                badge.getStyle().set("margin-left", "var(--lumo-space-s)");
                nameLayout.add(badge);
            }
            return nameLayout;
        }).setHeader("Driver");
        
        grid.addColumn(DriverBoardState::getTeam).setHeader("Team");
        
        // Race columns
        Grid.Column<DriverBoardState> tyreCol = grid.addComponentColumn(state -> {
            Span badge = new Span();
            badge.addClassName("tyre-badge");
            String compound = state.getTyreCompound();
            if (compound == null) compound = "Unknown";
            badge.setText(compound.substring(0, 1));
            
            switch (compound) {
                case "Soft" -> badge.addClassName("tyre-soft");
                case "Medium" -> badge.addClassName("tyre-medium");
                case "Hard" -> badge.addClassName("tyre-hard");
                case "Inter" -> badge.addClassName("tyre-inter");
                case "Wet" -> badge.addClassName("tyre-wet");
                default -> badge.addClassName("tyre-unknown");
            }
            return badge;
        }).setHeader("Tyre");
        
        Grid.Column<DriverBoardState> ageCol = grid.addColumn(DriverBoardState::getTyreAge).setHeader("Age");
        Grid.Column<DriverBoardState> pitsCol = grid.addColumn(DriverBoardState::getPitStops).setHeader("Pits");
        Grid.Column<DriverBoardState> penCol = grid.addColumn(state -> state.getPenalties() > 0 ? state.getPenalties() + "s" : "-").setHeader("Pen");
        Grid.Column<DriverBoardState> warnCol = grid.addColumn(DriverBoardState::getWarnings).setHeader("Warn").setWidth("70px").setFlexGrow(0);
        
        Grid.Column<DriverBoardState> wearCol = grid.addColumn(state -> state.getTyreWear() + "%").setHeader("Wear").setWidth("80px").setFlexGrow(0);
        
        Grid.Column<DriverBoardState> ersCol = grid.addComponentColumn(state -> {
            Span ers = new Span(state.getErsPercentage() + "%");
            if (state.isErsActive()) {
                ers.getStyle().set("color", "#ffff00"); // Yellow for active
                ers.getStyle().set("font-weight", "bold");
            } else {
                ers.getStyle().set("color", "#00ff00"); // Green for normal
            }
            return ers;
        }).setHeader("ERS").setWidth("80px").setFlexGrow(0);

        Grid.Column<DriverBoardState> gapLdrCol = grid.addColumn(DriverBoardState::getGapToLeader).setHeader("Gap Leader");
        Grid.Column<DriverBoardState> intervalCol = grid.addColumn(DriverBoardState::getGapToFront).setHeader("Interval");

        // Quali columns
        Grid.Column<DriverBoardState> bestLapCol = grid.addColumn(DriverBoardState::getBestLapTime).setHeader("Best Lap");
        Grid.Column<DriverBoardState> gapBestCol = grid.addColumn(DriverBoardState::getGapToLeaderBest).setHeader("Gap");
        Grid.Column<DriverBoardState> s1Col = grid.addColumn(DriverBoardState::getS1Time).setHeader("S1");
        Grid.Column<DriverBoardState> s2Col = grid.addColumn(DriverBoardState::getS2Time).setHeader("S2");
        Grid.Column<DriverBoardState> s3Col = grid.addColumn(DriverBoardState::getS3Time).setHeader("S3");

        warnCol.setPartNameGenerator(state -> state.getWarnings() == 2 ? "warning-danger" : null);
        s1Col.setPartNameGenerator(state -> state.isBestS1() ? "best-sector" : null);
        s2Col.setPartNameGenerator(state -> state.isBestS2() ? "best-sector" : null);
        s3Col.setPartNameGenerator(state -> state.isBestS3() ? "best-sector" : null);

        grid.setPartNameGenerator(state -> {
            int status = state.getResultStatus();
            if (status >= 4) return "status-retired";
            return null;
        });

        // Store columns for easy toggling
        this.raceColumns = List.of(tyreCol, ageCol, pitsCol, penCol, warnCol, gapLdrCol, intervalCol);
        this.qualiColumns = List.of(bestLapCol, gapBestCol, s1Col, s2Col, s3Col);
        
        this.wearCol = wearCol;
        this.ersCol = ersCol;
        
        grid.getStyle().set("font-family", "monospace");
    }

    private void setupWakeLockLogic() {
        keepScreenOn.addValueChangeListener(event -> {
            boolean checked = event.getValue();
            getElement().executeJs("localStorage.setItem('keepScreenOn', $0)", checked);
            if (checked) {
                getElement().executeJs(
                    "if ('wakeLock' in navigator) {" +
                    "  const requestWakeLock = async () => {" +
                    "    try {" +
                    "      window.wakeLock = await navigator.wakeLock.request('screen');" +
                    "      console.log('Wake Lock is active');" +
                    "    } catch (err) {" +
                    "      console.error(`${err.name}, ${err.message}`);" +
                    "    }" +
                    "  };" +
                    "  requestWakeLock();" +
                    "  window.reacquireWakeLock = () => {" +
                    "    if (document.visibilityState === 'visible' && window.wakeLock !== null) {" +
                    "      requestWakeLock();" +
                    "    }" +
                    "  };" +
                    "  document.addEventListener('visibilitychange', window.reacquireWakeLock);" +
                    "} else {" +
                    "  console.log('Wake Lock API not supported on this browser.');" +
                    "}"
                );
            } else {
                getElement().executeJs(
                    "if (window.wakeLock) {" +
                    "  window.wakeLock.release();" +
                    "  window.wakeLock = null;" +
                    "}" +
                    "if (window.reacquireWakeLock) {" +
                    "  document.removeEventListener('visibilitychange', window.reacquireWakeLock);" +
                    "}"
                );
            }
        });
    }

    private Grid.Column<DriverBoardState> wearCol;
    private Grid.Column<DriverBoardState> ersCol;
    private List<Grid.Column<DriverBoardState>> raceColumns;
    private List<Grid.Column<DriverBoardState>> qualiColumns;

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        if (tierId == null) {
            title.setText("NO TIER SELECTED");
            return;
        }

        // Read preference from browser local storage
        getElement().executeJs("return localStorage.getItem('keepScreenOn')").then(String.class, val -> {
            if ("false".equals(val)) {
                keepScreenOn.setValue(false);
            } else {
                // Default to true if not set or explicitly true
                keepScreenOn.setValue(true);
            }
        });

        UI ui = attachEvent.getUI();
        leaderboardRegistration = broadcaster.registerLeaderboard(tierId, data -> {
            if (attachEvent.getUI().isAttached()) {
                attachEvent.getUI().access(() -> {
                    if (isAttached()) {
                        updateLeaderboard(data);
                    }
                });
            }
        });
        sessionInfoRegistration = broadcaster.registerSessionInfo(tierId, info -> {
            if (attachEvent.getUI().isAttached()) {
                attachEvent.getUI().access(() -> {
                    if (isAttached()) {
                        updateSessionInfo(info);
                    }
                });
            }
        });

        // Periodic full refresh (heartbeat) every 10 seconds to recover from missed push events
        heartbeatTimer = new java.util.Timer();
        heartbeatTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (attachEvent.getUI().isAttached()) {
                    attachEvent.getUI().access(() -> {
                        if (isAttached()) {
                            updateLeaderboard(telemetryProcessingService.getLeaderboard(tierId));
                            be.jabapage.racingleague.f1telemetry.model.SessionInfo info = telemetryProcessingService.getSessionInfo(tierId);
                            if (info != null) {
                                updateSessionInfo(info);
                            }
                        }
                    });
                }
            }
        }, 10000, 10000);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (leaderboardRegistration != null) {
            leaderboardRegistration.remove();
            leaderboardRegistration = null;
        }
        if (sessionInfoRegistration != null) {
            sessionInfoRegistration.remove();
            sessionInfoRegistration = null;
        }
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
    }

    private void updateLeaderboard(List<DriverBoardState> data) {
        if (!data.isEmpty()) {
            DriverBoardState first = data.get(0);
            boolean isQuali = first.isQualifying();
            raceColumns.forEach(c -> c.setVisible(!isQuali));
            qualiColumns.forEach(c -> c.setVisible(isQuali));
            
            // Also respect league settings (independent of session type)
            wearCol.setVisible(first.isShowTyreWear());
            ersCol.setVisible(first.isShowErs());
        }
        grid.setItems(data);
    }

    private void updateSessionInfo(SessionInfo info) {
        this.currentSessionInfo = info;
        String titleText = "LIVE LEADERBOARD - " + info.getSessionType().toUpperCase();
        if (info.isRace()) {
            titleText += " | LAP " + info.getCurrentLap() + " / " + info.getTotalLaps();
        } else if (info.getTimeLeftSeconds() > 0) {
            titleText += " | TIME REMAINING: " + formatTime(info.getTimeLeftSeconds());
        }
        title.setText(titleText);

        // Update SC status
        scStatus.setText("");
        scStatus.removeClassName("sc-active");
        scStatus.removeClassName("vsc-active");

        if (info.getSafetyCarStatus() == 1) {
            scStatus.setText(" | SAFETY CAR");
            scStatus.addClassName("sc-active");
        } else if (info.getSafetyCarStatus() == 2) {
            scStatus.setText(" | VIRTUAL SAFETY CAR");
            scStatus.addClassName("vsc-active");
        }

        // Update DRS status
        drsStatus.setText(info.isDrsEnabled() ? " | DRS ENABLED" : " | DRS DISABLED");
        drsStatus.getStyle().set("color", info.isDrsEnabled() ? "var(--lumo-success-text-color)" : "var(--lumo-error-text-color)");

        // Update Weather
        updateWeather(info.getWeather(), info.getAirTemperature(), info.getTrackTemperature());
    }

    private void updateWeather(int weather, int airTemp, int trackTemp) {
        weatherIcon.setIcon(getWeatherIcon(weather));
        weatherTemp.setText(airTemp + "°C (Track: " + trackTemp + "°C)");
    }

    private VaadinIcon getWeatherIcon(int weather) {
        return switch (weather) {
            case 0 -> VaadinIcon.SUN_O;
            case 1 -> VaadinIcon.CLOUD;
            case 2 -> VaadinIcon.CLOUD;
            case 3 -> VaadinIcon.UMBRELLA;
            case 4 -> VaadinIcon.UMBRELLA;
            case 5 -> VaadinIcon.FLASH;
            default -> VaadinIcon.QUESTION;
        };
    }

    private void showWeatherForecast() {
        if (currentSessionInfo == null || currentSessionInfo.getWeatherForecast() == null || currentSessionInfo.getWeatherForecast().isEmpty()) {
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Weather Forecast");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Filter and show only upcoming forecasts (timeOffset > 0)
        currentSessionInfo.getWeatherForecast().stream()
                .filter(s -> s.getTimeOffset() > 0)
                .limit(10)
                .forEach(sample -> {
                    HorizontalLayout row = new HorizontalLayout();
                    row.setAlignItems(Alignment.CENTER);
                    row.setSpacing(true);

                    Span time = new Span("+" + sample.getTimeOffset() + " min");
                    time.setWidth("70px");
                    
                    Icon icon = new Icon(getWeatherIcon(sample.getWeather()));
                    Span rain = new Span(sample.getRainPercentage() + "% rain");
                    rain.setWidth("80px");
                    
                    Span temp = new Span(sample.getAirTemperature() + "°C");
                    
                    row.add(time, icon, rain, temp);
                    layout.add(row);
                });

        dialog.add(layout);
        dialog.open();
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
