package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.model.DriverBoardState;
import be.jabapage.racingleague.f1telemetry.model.SessionInfo;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.LeagueLogo;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.server.StreamResource;
import java.io.ByteArrayInputStream;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.Broadcaster;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.data.renderer.LitRenderer;

import java.util.List;

@PageTitle("Live Leaderboard | F1 Telemetry")
@Route(value = "leaderboard")
@AnonymousAllowed
public class LeaderboardView extends VerticalLayout implements HasUrlParameter<Long> {

    private final Broadcaster broadcaster;
    private final SecurityService securityService;
    private final be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService telemetryProcessingService;
    private final TierRepository tierRepository;
    private final be.jabapage.racingleague.f1telemetry.repository.LeagueLogoRepository leagueLogoRepository;
    private final Grid<DriverBoardState> grid = new Grid<>(DriverBoardState.class, false);
    private final HorizontalLayout logoContainer = new HorizontalLayout();
    private League league;
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
    private final java.util.Set<String> highlightedDrivers = new java.util.HashSet<>();
    private List<DriverBoardState> pendingLeaderboardData;
    private long lastLeaderboardUpdateTime = 0;
    private boolean leaderboardUpdateScheduled = false;

    public LeaderboardView(Broadcaster broadcaster, SecurityService securityService, be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService telemetryProcessingService, TierRepository tierRepository, be.jabapage.racingleague.f1telemetry.repository.LeagueLogoRepository leagueLogoRepository) {
        this.broadcaster = broadcaster;
        this.securityService = securityService;
        this.telemetryProcessingService = telemetryProcessingService;
        this.tierRepository = tierRepository;
        this.leagueLogoRepository = leagueLogoRepository;
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

        logoContainer.setAlignItems(Alignment.CENTER);
        HorizontalLayout header = new HorizontalLayout(logoContainer, title, scStatus, drsStatus, weatherLayout, keepScreenOn);
        header.setAlignItems(Alignment.CENTER);
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
            this.league = tier.getLeague();
            backLink.setRoute(SeasonDetailsView.class, league.getId());
            updateLogo();
        });
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(LitRenderer.<DriverBoardState>of(
            "<vaadin-button theme=\"tertiary\" style=\"cursor: pointer; padding: 0; margin: 0; min-width: 0; line-height: 1;\" @click=\"${handleStarClick}\">" +
            "  <vaadin-icon icon=\"${item.isHighlighted ? 'vaadin:star' : 'vaadin:star-o'}\" style=\"color: ${item.isHighlighted ? '#ffcc00' : '#888888'}\"></vaadin-icon>" +
            "</vaadin-button>"
        )
        .withProperty("isHighlighted", state -> highlightedDrivers.contains(state.getName()))
        .withFunction("handleStarClick", state -> {
            if (highlightedDrivers.contains(state.getName())) {
                highlightedDrivers.remove(state.getName());
            } else {
                highlightedDrivers.add(state.getName());
            }
            grid.getDataProvider().refreshAll();
        }))
        .setWidth("50px").setFlexGrow(0).setHeader("");

        grid.addColumn(state -> {
            int status = state.getResultStatus();
            if (status == 4) return "DNF";
            if (status == 5) return "DSQ";
            if (status == 6) return "NC";
            if (status == 7) return "RET";
            return state.getPosition();
        }).setHeader("Pos").setWidth("70px").setFlexGrow(0);
        
        grid.addComponentColumn(state -> {
            Span flagSpan = new Span(CountryProvider.getFlagByName(state.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");

            Span raceNum = new Span("#" + state.getRaceNumber());
            raceNum.getStyle().set("color", "var(--lumo-secondary-text-color)");
            raceNum.getStyle().set("font-size", "0.8em");
            raceNum.getStyle().set("margin-right", "var(--lumo-space-s)");

            Span name = new Span(state.getName());
            HorizontalLayout nameLayout = new HorizontalLayout(flagSpan, raceNum, name);
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
        
        grid.addColumn(LitRenderer.<DriverBoardState>of(
            "<div style=\"display: inline-flex; align-items: center; gap: 8px; height: 100%;\" title=\"${item.teamName}\">" +
            "  <div style=\"width: 4px; height: 18px; border-radius: 2px; background-color: ${item.teamColor}; flex-shrink: 0;\"></div>" +
            "  <span style=\"display: inline-flex; align-items: center; justify-content: center; color: ${item.teamColor}; width: 20px; height: 20px;\" .innerHTML=\"${item.teamLogo}\"></span>" +
            "</div>"
        )
        .withProperty("teamName", DriverBoardState::getTeam)
        .withProperty("teamColor", state -> getTeamColor(state.getTeamId()))
        .withProperty("teamLogo", state -> getTeamLogoSvg(state.getTeamId()))
        ).setHeader("Team").setWidth("75px").setFlexGrow(0);
        
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
        Grid.Column<DriverBoardState> warnCol = grid.addComponentColumn(state -> {
            int warnings = state.getWarnings();
            Span span = new Span(String.valueOf(warnings));
            if (warnings > 0 && warnings % 3 == 2) {
                span.getElement().setAttribute("title", "One warning away from a penalty");
            }
            return span;
        }).setHeader("Warn").setWidth("70px").setFlexGrow(0);
        
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

        warnCol.setPartNameGenerator(state -> state.getWarnings() % 3 == 2 ? "warning-danger" : null);
        bestLapCol.setPartNameGenerator(state -> state.isBestLap() ? "fastest-lap" : null);
        s1Col.setPartNameGenerator(state -> state.isBestS1() ? "best-sector" : null);
        s2Col.setPartNameGenerator(state -> state.isBestS2() ? "best-sector" : null);
        s3Col.setPartNameGenerator(state -> state.isBestS3() ? "best-sector" : null);

        grid.setPartNameGenerator(state -> {
            int status = state.getResultStatus();
            StringBuilder parts = new StringBuilder();
            if (status >= 4) {
                parts.append("status-retired ");
            }
            if (highlightedDrivers.contains(state.getName())) {
                parts.append("highlighted-driver ");
            }
            return parts.length() > 0 ? parts.toString().trim() : null;
        });

        // Store columns for easy toggling
        this.raceColumns = List.of(tyreCol, ageCol, pitsCol, penCol, warnCol, gapLdrCol, intervalCol);
        this.qualiColumns = List.of(gapBestCol, s1Col, s2Col, s3Col);
        
        this.wearCol = wearCol;
        this.ersCol = ersCol;
        
        grid.getStyle().set("font-family", "monospace");
    }

    private void setupWakeLockLogic() {
        keepScreenOn.addValueChangeListener(event -> {
            if (event.getValue()) {
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
                    "  alert('Wake Lock API not supported on this browser.');" +
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
        super.onAttach(attachEvent);
        if (tierId == null) {
            title.setText("NO TIER SELECTED");
            return;
        }
        updateLogo();
        UI ui = attachEvent.getUI();
        leaderboardRegistration = broadcaster.registerLeaderboard(tierId, data -> {
            if (attachEvent.getUI().isAttached()) {
                attachEvent.getUI().access(() -> {
                    if (isAttached()) {
                        throttleLeaderboardUpdate(data);
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
        detachEvent.getUI().getPage().executeJs(
            "document.documentElement.style.removeProperty('--lumo-base-color'); document.body.style.backgroundColor = '';"
        );
    }

    private void throttleLeaderboardUpdate(List<DriverBoardState> data) {
        long now = System.currentTimeMillis();
        pendingLeaderboardData = data;
        
        if (now - lastLeaderboardUpdateTime >= 500) {
            updateLeaderboard(pendingLeaderboardData);
            lastLeaderboardUpdateTime = now;
        } else if (!leaderboardUpdateScheduled) {
            leaderboardUpdateScheduled = true;
            long delay = 500 - (now - lastLeaderboardUpdateTime);
            if (heartbeatTimer != null) {
                try {
                    heartbeatTimer.schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            getUI().ifPresent(ui -> ui.access(() -> {
                                if (isAttached() && pendingLeaderboardData != null) {
                                    updateLeaderboard(pendingLeaderboardData);
                                    lastLeaderboardUpdateTime = System.currentTimeMillis();
                                }
                                leaderboardUpdateScheduled = false;
                            }));
                        }
                    }, delay);
                } catch (IllegalStateException e) {
                    leaderboardUpdateScheduled = false;
                }
            } else {
                leaderboardUpdateScheduled = false;
            }
        }
    }

    private void updateLeaderboard(List<DriverBoardState> data) {
        if (data == null) {
            grid.setItems(List.of());
            return;
        }
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
        if (info == null) return;
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
            logoImg.setHeight("40px");
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



    private static String getTeamColor(int teamId) {
        return switch (teamId) {
            case 0, 220, 476 -> "#27F4D2"; // Mercedes
            case 1, 221, 477 -> "#E80020"; // Ferrari
            case 2, 222, 478 -> "#3671C6"; // Red Bull
            case 3, 223, 479 -> "#37BEDD"; // Williams
            case 4, 224, 480 -> "#229971"; // Aston Martin
            case 5, 225, 481 -> "#0093CC"; // Alpine
            case 6, 226, 482 -> "#6692FF"; // RB
            case 7, 227, 483 -> "#B6BABD"; // Haas
            case 8, 228, 484 -> "#FF8000"; // McLaren
            case 9 -> "#52E252";          // Sauber (F1 25)
            case 229, 485 -> "#FF007F";    // Audi (F1 26)
            case 230, 486 -> "#D1B86E";    // Cadillac (F1 26)
            default -> "#888888";
        };
    }

    private static String getTeamLogoSvg(int teamId) {
        return switch (teamId) {
            case 0, 220, 476 -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" style=\"width: 20px; height: 20px;\">" +
                "  <circle cx=\"12\" cy=\"12\" r=\"10\"/>" +
                "  <path d=\"M12 2v10M12 12l-8.66 5M12 12l8.66 5\"/>" +
                "</svg>";
            case 1, 221, 477 -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"width: 20px; height: 20px;\">" +
                "  <path d=\"M12 2 L19 5v8c0 5-7 9-7 9s-7-4-7-9V5l7-3z\"/>" +
                "  <text x=\"12\" y=\"14\" font-size=\"8\" font-weight=\"bold\" fill=\"#FFFF00\" text-anchor=\"middle\">SF</text>" +
                "</svg>";
            case 2, 222, 478 -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"width: 20px; height: 20px;\">" +
                "  <circle cx=\"12\" cy=\"12\" r=\"9\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"/>" +
                "  <path d=\"M12 4c-4 0-7 3-7 7 0 2 1 4 3 5-1-1-2-3-2-5 0-3 3-5 6-5s6 2 6 5c0 2-1 4-2 5 2-1 3-3 3-5 0-4-3-7-7-7z M8 13s2-2 4-2 4 2 4 2c0 0-2 1-4 1s-4-1-4-1z\"/>" +
                "</svg>";
            case 3, 223, 479 -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\" style=\"width: 20px; height: 20px;\">" +
                "  <path d=\"M4 6l4 12 4-8 4 8 4-12\"/>" +
                "</svg>";
            case 4, 224, 480 -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" style=\"width: 20px; height: 20px;\">" +
                "  <path d=\"M2 10c4-2 8-2 10 2 2-4 6-4 10-2M4 12h16M6 14h12\"/>" +
                "</svg>";
            case 5, 225, 481 -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\" style=\"width: 20px; height: 20px;\">" +
                "  <path d=\"M12 3 L4 21h16L12 3z M6 17h12 M8 13h5\"/>" +
                "</svg>";
            case 6, 226, 482 -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" style=\"width: 20px; height: 20px;\">" +
                "  <circle cx=\"12\" cy=\"12\" r=\"10\"/>" +
                "  <path d=\"M9 7h4a2 2 0 0 1 0 4H9v-4zm0 4h4.5a2 2 0 0 1 0 4H9v-4zm-2 8h10\"/>" +
                "</svg>";
            case 7, 227, 483 -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" style=\"width: 20px; height: 20px;\">" +
                "  <circle cx=\"12\" cy=\"12\" r=\"10\"/>" +
                "  <path d=\"M8 8v8M16 8v8M8 12h8\"/>" +
                "</svg>";
            case 8, 228, 484 -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"width: 20px; height: 20px;\">" +
                "  <path d=\"M4 18c8-1 14-6 16-12-2 4-6 8-12 10-2 .5-3 1-4 2z\"/>" +
                "</svg>";
            case 9 -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" style=\"width: 20px; height: 20px;\">" +
                "  <circle cx=\"12\" cy=\"12\" r=\"10\"/>" +
                "  <path d=\"M16 9a3 3 0 0 0-6 0v6a3 3 0 0 0 6 0\"/>" +
                "</svg>";
            case 229, 485 -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" style=\"width: 20px; height: 20px;\">" +
                "  <circle cx=\"6\" cy=\"12\" r=\"3\"/>" +
                "  <circle cx=\"10\" cy=\"12\" r=\"3\"/>" +
                "  <circle cx=\"14\" cy=\"12\" r=\"3\"/>" +
                "  <circle cx=\"18\" cy=\"12\" r=\"3\"/>" +
                "</svg>";
            case 230, 486 -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" style=\"width: 20px; height: 20px;\">" +
                "  <path d=\"M12 2 L20 5v8c0 4-4 8-8 9-4-1-8-5-8-9V5l8-3z M8 8h8 M8 12h8 M8 16h8\"/>" +
                "</svg>";
            default -> 
                "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" style=\"width: 20px; height: 20px;\">" +
                "  <path d=\"M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1zM4 22v-7\"/>" +
                "</svg>";
        };
    }
}
