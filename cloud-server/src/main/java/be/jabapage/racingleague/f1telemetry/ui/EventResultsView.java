package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.LeagueLogo;
import be.jabapage.racingleague.f1telemetry.repository.*;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryResultsService;
import be.jabapage.racingleague.f1telemetry.ui.event.InfographicsTab;
import be.jabapage.racingleague.f1telemetry.ui.event.LineupTab;
import be.jabapage.racingleague.f1telemetry.ui.event.ResultsTab;
import be.jabapage.racingleague.f1telemetry.ui.event.StatsTab;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Objects;

@AnonymousAllowed
@PageTitle("Event Results | F1 Telemetry")
@Route(value = "event")
public class EventResultsView extends VerticalLayout implements HasUrlParameter<Long> {

    private final EventRepository eventRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;
    private final LeagueLogoRepository leagueLogoRepository;

    private final HorizontalLayout logoContainer = new HorizontalLayout();
    private final H2 eventHeader = new H2();
    private final Span statusBadge = new Span();
    private final Button toggleFinalizedBtn = new Button();
    private final RouterLink backToSeason = new RouterLink("Back to Season", SeasonDetailsView.class, 0L);

    private final ResultsTab resultsTabContent;
    private final StatsTab statsTabContent;
    private final LineupTab lineupTabContent;
    private final InfographicsTab infographicsTabContent;

    private Long currentEventId;
    private Event currentEvent;

    public EventResultsView(EventRepository eventRepository,
                            SessionResultRepository sessionResultRepository,
                            DriverResultRepository driverResultRepository,
                            DriverMappingRepository driverMappingRepository,
                            TelemetryProcessingService telemetryProcessingService,
                            SecurityService securityService,
                            TeamMappingRepository teamMappingRepository,
                            LeagueLogoRepository leagueLogoRepository,
                            EventLineupEntryRepository eventLineupEntryRepository,
                            DriverStandingRepository driverStandingRepository,
                            TelemetryResultsService telemetryResultsService,
                            SessionPointConfigRepository sessionPointConfigRepository,
                            LapTelemetryRepository lapTelemetryRepository,
                            ManualPenaltyRepository manualPenaltyRepository) {
        this.eventRepository = eventRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;
        this.leagueLogoRepository = leagueLogoRepository;

        setSizeFull();

        // Instantiate Tabs
        resultsTabContent = new ResultsTab(
                sessionResultRepository,
                driverResultRepository,
                driverMappingRepository,
                teamMappingRepository,
                eventLineupEntryRepository,
                sessionPointConfigRepository,
                manualPenaltyRepository,
                lapTelemetryRepository,
                securityService,
                telemetryProcessingService,
                telemetryResultsService,
                this::refreshEvent
        );

        statsTabContent = new StatsTab(
                telemetryProcessingService,
                sessionResultRepository
        );

        lineupTabContent = new LineupTab(
                eventLineupEntryRepository,
                driverMappingRepository,
                teamMappingRepository,
                leagueLogoRepository,
                sessionResultRepository,
                securityService,
                telemetryProcessingService
        );

        infographicsTabContent = new InfographicsTab(
                telemetryProcessingService,
                sessionResultRepository
        );

        // Hide all initially except results
        statsTabContent.setVisible(false);
        lineupTabContent.setVisible(false);
        infographicsTabContent.setVisible(false);

        // Main Tabs navigation
        Tab resultsTab = new Tab("Results");
        Tab statsTab = new Tab("Stats");
        Tab lineupTab = new Tab("Lineup");
        Tab infographicsTab = new Tab("Infographics");
        Tabs mainTabs = new Tabs(resultsTab, statsTab, lineupTab, infographicsTab);

        mainTabs.addSelectedChangeListener(event -> {
            boolean isResults = event.getSelectedTab().equals(resultsTab);
            boolean isStats = event.getSelectedTab().equals(statsTab);
            boolean isLineup = event.getSelectedTab().equals(lineupTab);
            boolean isInfographics = event.getSelectedTab().equals(infographicsTab);

            resultsTabContent.setVisible(isResults);
            statsTabContent.setVisible(isStats);
            lineupTabContent.setVisible(isLineup);
            infographicsTabContent.setVisible(isInfographics);

            if (isStats) {
                statsTabContent.update(currentEvent);
            } else if (isLineup) {
                lineupTabContent.update(currentEvent);
            } else if (isInfographics) {
                infographicsTabContent.update(currentEvent);
            }
        });

        // Top Navigation & Header
        HorizontalLayout nav = new HorizontalLayout(backToSeason);
        if (!securityService.getAuthenticatedUser().isPresent()) {
            nav.add(new RouterLink("Login", LoginView.class));
        }
        nav.add(new RouterLink("Documentation", DocumentationView.class));
        nav.setSpacing(true);

        logoContainer.setAlignItems(Alignment.CENTER);
        statusBadge.getStyle().set("margin-left", "var(--lumo-space-m)");
        toggleFinalizedBtn.getStyle().set("margin-left", "var(--lumo-space-m)");
        HorizontalLayout titleLayout = new HorizontalLayout(logoContainer, eventHeader, statusBadge, toggleFinalizedBtn);
        titleLayout.setAlignItems(Alignment.CENTER);
        titleLayout.setSpacing(true);

        toggleFinalizedBtn.addClickListener(ev -> {
            if (currentEvent == null) return;
            boolean newStatus = !Boolean.TRUE.equals(currentEvent.getFinalized());
            String statusWord = newStatus ? "final" : "provisional";
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Mark Event as " + statusWord.substring(0, 1).toUpperCase() + statusWord.substring(1) + "?");
            dialog.setText("Are you sure you want to mark this event as " + statusWord + "? Standings will be recalculated.");
            dialog.setCancelable(true);
            dialog.setConfirmText("Yes");
            dialog.addConfirmListener(confirmEv -> {
                currentEvent.setFinalized(newStatus);
                eventRepository.save(currentEvent);
                telemetryProcessingService.recalculateStandings(currentEvent.getTier().getId());
                refreshEvent();
                Notification.show("Event marked as " + statusWord + " and standings recalculated", 3000, Notification.Position.TOP_CENTER);
            });
            dialog.open();
        });

        add(nav, titleLayout, mainTabs, resultsTabContent, statsTabContent, lineupTabContent, infographicsTabContent);
    }

    private void refreshEvent() {
        if (currentEventId == null) return;
        telemetryProcessingService.getEventWithAllResults(currentEventId).ifPresent(e -> {
            this.currentEvent = e;
            resultsTabContent.update(currentEvent);
            if (statsTabContent.isVisible()) {
                statsTabContent.update(currentEvent);
            }
            if (lineupTabContent.isVisible()) {
                lineupTabContent.update(currentEvent);
            }
            if (infographicsTabContent.isVisible()) {
                infographicsTabContent.update(currentEvent);
            }
            updateStatusUI();
        });
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        this.currentEventId = parameter;
        telemetryProcessingService.getEventWithAllResults(parameter).ifPresentOrElse(e -> {
            this.currentEvent = e;
            eventHeader.setText("Event: " + currentEvent.getEventName());
            backToSeason.setRoute(SeasonDetailsView.class, currentEvent.getTier().getLeague().getId());
            updateLogo();
            
            resultsTabContent.update(currentEvent);
            if (statsTabContent.isVisible()) {
                statsTabContent.update(currentEvent);
            }
            if (lineupTabContent.isVisible()) {
                lineupTabContent.update(currentEvent);
            }
            if (infographicsTabContent.isVisible()) {
                infographicsTabContent.update(currentEvent);
            }
            updateStatusUI();
        }, () -> {
            event.forwardTo(SeasonListView.class);
        });
    }

    private void updateStatusUI() {
        if (currentEvent == null) return;

        statusBadge.getElement().getThemeList().clear();
        statusBadge.getElement().removeAttribute("title");
        if ("FINAL".equalsIgnoreCase(currentEvent.getStatus())) {
            statusBadge.setText("Final");
            statusBadge.getElement().getThemeList().add("badge success");
        } else if ("PROVISIONAL_WARNING".equalsIgnoreCase(currentEvent.getStatus())) {
            statusBadge.setText("Provisional (Warning)");
            statusBadge.getElement().getThemeList().add("badge warning");
            statusBadge.getElement().setAttribute("title", "Saved via fallback: didn't receive final classification packages.");
        } else {
            statusBadge.setText("Provisional");
            statusBadge.getElement().getThemeList().add("badge error");
        }

        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        toggleFinalizedBtn.setVisible(loggedIn);
        if (loggedIn) {
            toggleFinalizedBtn.getElement().getThemeList().clear();
            toggleFinalizedBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
            if (Boolean.TRUE.equals(currentEvent.getFinalized())) {
                toggleFinalizedBtn.setText("Reopen");
                toggleFinalizedBtn.setIcon(com.vaadin.flow.component.icon.VaadinIcon.UNLOCK.create());
                toggleFinalizedBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            } else {
                toggleFinalizedBtn.setText("Mark Final");
                toggleFinalizedBtn.setIcon(com.vaadin.flow.component.icon.VaadinIcon.LOCK.create());
                toggleFinalizedBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            }
        }
    }

    private void updateLogo() {
        logoContainer.removeAll();
        if (currentEvent != null && currentEvent.getTier() != null && currentEvent.getTier().getLeague() != null) {
            League league = currentEvent.getTier().getLeague();
            if (league.getHasLogo()) {
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
            if (league.getLogoBackgroundColor() != null) {
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
    }

    @Override
    protected void onAttach(com.vaadin.flow.component.AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        updateLogo();
        attachEvent.getUI().getPage().executeJs(getDownloadInfographicJs());
    }

    @Override
    protected void onDetach(com.vaadin.flow.component.DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        detachEvent.getUI().getPage().executeJs(
            "document.documentElement.style.removeProperty('--lumo-base-color'); document.body.style.backgroundColor = '';"
        );
    }

    public static String getDynamicSessionName(int sessionType, java.util.Collection<Integer> sessionTypesInEvent) {
        if (sessionType == 15) {
            if (sessionTypesInEvent.contains(16)) {
                return "Sprint Race";
            }
            return "Race";
        }
        if (sessionType == 16) {
            if (sessionTypesInEvent.contains(15)) {
                return "Race";
            }
            return "Race 2";
        }
        return TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(sessionType, "Session " + sessionType);
    }

    public static String formatLapTime(float seconds) {
        if (seconds <= 0) return "-";
        int minutes = (int) (seconds / 60);
        float remainingSeconds = seconds % 60;
        return String.format("%d:%06.3f", minutes, remainingSeconds);
    }

    public static float parseLapTime(String text) {
        if (text == null || text.isEmpty()) return 0;
        if (text.contains(":")) {
            String[] parts = text.split(":");
            if (parts.length == 3) { // HH:mm:ss.SSS
                int hours = Integer.parseInt(parts[0]);
                int mins = Integer.parseInt(parts[1]);
                float secs = Float.parseFloat(parts[2]);
                return hours * 3600 + mins * 60 + secs;
            } else if (parts.length == 2) { // mm:ss.SSS
                int mins = Integer.parseInt(parts[0]);
                float secs = Float.parseFloat(parts[1]);
                return mins * 60 + secs;
            }
        }
        return Float.parseFloat(text);
    }

    public static String getDownloadInfographicJs() {
        return "window.downloadInfographic = function(selector, filename, buttonEl) {\n" +
                "    if (!selector || !filename) return;\n" +
                "    const originalText = buttonEl ? buttonEl.innerHTML : '';\n" +
                "    if (buttonEl) {\n" +
                "        buttonEl.style.pointerEvents = 'none';\n" +
                "        buttonEl.style.opacity = '0.6';\n" +
                "        buttonEl.classList.add('infographic-loading-btn');\n" +
                "        const spinnerSvg = `<svg class=\"btn-spinner\" viewBox=\"0 0 50 50\" style=\"width: 16px; height: 16px; margin-right: 8px; animation: spin 1s linear infinite; display: inline-block; vertical-align: middle;\"><circle cx=\"25\" cy=\"25\" r=\"20\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"5\" stroke-dasharray=\"80, 200\" stroke-linecap=\"round\"></circle></svg>`;\n" +
                "        buttonEl.innerHTML = spinnerSvg + ' Generating...';\n" +
                "    }\n" +
                "    let container = document.getElementById('infographic-toast-container');\n" +
                "    if (!container) {\n" +
                "        container = document.createElement('div');\n" +
                "        container.id = 'infographic-toast-container';\n" +
                "        Object.assign(container.style, {\n" +
                "            position: 'fixed',\n" +
                "            top: '20px',\n" +
                "            right: '20px',\n" +
                "            zIndex: '99999',\n" +
                "            display: 'flex',\n" +
                "            flexDirection: 'column',\n" +
                "            gap: '10px',\n" +
                "            pointerEvents: 'none'\n" +
                "        });\n" +
                "        document.body.appendChild(container);\n" +
                "    }\n" +
                "    const toastId = 'toast-' + Math.random().toString(36).substr(2, 9);\n" +
                "    const toast = document.createElement('div');\n" +
                "    toast.id = toastId;\n" +
                "    toast.className = 'infographic-toast infographic-toast-loading';\n" +
                "    Object.assign(toast.style, {\n" +
                "        background: 'rgba(18, 19, 24, 0.85)',\n" +
                "        backdropFilter: 'blur(12px)',\n" +
                "        borderLeft: '4px solid #ffd700',\n" +
                "        borderTop: '1px solid rgba(255,255,255,0.08)',\n" +
                "        borderRight: '1px solid rgba(255,255,255,0.04)',\n" +
                "        borderBottom: '1px solid rgba(255,255,255,0.04)',\n" +
                "        padding: '16px 20px',\n" +
                "        borderRadius: '8px',\n" +
                "        boxShadow: '0 10px 30px rgba(0,0,0,0.5)',\n" +
                "        color: 'white',\n" +
                "        fontFamily: 'system-ui, -apple-system, sans-serif',\n" +
                "        minWidth: '280px',\n" +
                "        display: 'flex',\n" +
                "        alignItems: 'center',\n" +
                "        gap: '15px',\n" +
                "        opacity: '0',\n" +
                "        transform: 'translateX(50px)',\n" +
                "        transition: 'all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275)',\n" +
                "        pointerEvents: 'auto'\n" +
                "    });\n" +
                "    toast.innerHTML = `\n" +
                "        <div class=\"toast-spinner-wrapper\" style=\"position: relative; width: 28px; height: 28px; flex-shrink: 0;\">\n" +
                "            <svg viewBox=\"0 0 50 50\" style=\"width: 100%; height: 100%; animation: spin 1s linear infinite;\">\n" +
                "                <circle cx=\"25\" cy=\"25\" r=\"20\" fill=\"none\" stroke=\"#ffd700\" stroke-width=\"4\" stroke-dasharray=\"80, 200\" stroke-linecap=\"round\"></circle>\n" +
                "            </svg>\n" +
                "        </div>\n" +
                "        <div class=\"toast-text-wrapper\" style=\"flex-grow: 1;\">\n" +
                "            <div style=\"font-weight: 800; font-size: 14px; text-transform: uppercase; letter-spacing: 0.5px;\">Generating Image</div>\n" +
                "            <div style=\"font-size: 12px; color: #a0a5b5; margin-top: 2px;\">Converting telemetry info...</div>\n" +
                "        </div>\n" +
                "    `;\n" +
                "    container.appendChild(toast);\n" +
                "    toast.offsetHeight;\n" +
                "    toast.style.opacity = '1';\n" +
                "    toast.style.transform = 'translateX(0)';\n" +
                "    const generate = () => {\n" +
                "        const el = document.querySelector(selector);\n" +
                "        if (!el) {\n" +
                "            updateToast(toast, 'Error', 'Infographic element not found.', '#ff3333');\n" +
                "            restoreButton();\n" +
                "            return;\n" +
                "        }\n" +
                "        const scale = (selector.includes('consistency') || selector.includes('stints') || selector.includes('pace')) ? 2 : 1.5;\n" +
                "        setTimeout(() => {\n" +
                "            html2canvas(el, { useCORS: true, backgroundColor: null, scale: scale }).then(canvas => {\n" +
                "                try {\n" +
                "                    const link = document.createElement('a');\n" +
                "                    link.download = filename;\n" +
                "                    link.href = canvas.toDataURL('image/png');\n" +
                "                    link.click();\n" +
                "                    updateToast(toast, 'Success', 'Infographic downloaded!', '#33ff33');\n" +
                "                    setTimeout(() => { dismissToast(toast); }, 2500);\n" +
                "                } catch (err) {\n" +
                "                    updateToast(toast, 'Error', 'Failed to save image.', '#ff3333');\n" +
                "                    setTimeout(() => { dismissToast(toast); }, 4000);\n" +
                "                }\n" +
                "                restoreButton();\n" +
                "            }).catch(err => {\n" +
                "                updateToast(toast, 'Error', 'Render failed: ' + err.message, '#ff3333');\n" +
                "                setTimeout(() => { dismissToast(toast); }, 4000);\n" +
                "                restoreButton();\n" +
                "            });\n" +
                "        }, 100);\n" +
                "    };\n" +
                "    const restoreButton = () => {\n" +
                "        if (buttonEl) {\n" +
                "            buttonEl.style.pointerEvents = '';\n" +
                "            buttonEl.style.opacity = '';\n" +
                "            buttonEl.innerHTML = originalText;\n" +
                "            buttonEl.classList.remove('infographic-loading-btn');\n" +
                "        }\n" +
                "    };\n" +
                "    const updateToast = (toastElement, title, message, color) => {\n" +
                "        toastElement.style.borderLeftColor = color;\n" +
                "        const iconSvg = title === 'Success'\n" +
                "            ? `<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"${color}\" stroke-width=\"3\" stroke-linecap=\"round\" stroke-linejoin=\"round\" style=\"width: 28px; height: 28px;\"><polyline points=\"20 6 9 17 4 12\"></polyline></svg>`\n" +
                "            : `<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"${color}\" stroke-width=\"3\" stroke-linecap=\"round\" stroke-linejoin=\"round\" style=\"width: 28px; height: 28px;\"><circle cx=\"12\" cy=\"12\" r=\"10\"></circle><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"></line><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"></line></svg>`;\n" +
                "        toastElement.innerHTML = `\n" +
                "            <div style=\"flex-shrink: 0; display: flex; align-items: center; justify-content: center;\">\n" +
                "                ${iconSvg}\n" +
                "            </div>\n" +
                "            <div class=\"toast-text-wrapper\" style=\"flex-grow: 1;\">\n" +
                "                <div style=\"font-weight: 800; font-size: 14px; text-transform: uppercase; letter-spacing: 0.5px; color: ${color};\">${title}</div>\n" +
                "                <div style=\"font-size: 12px; color: #a0a5b5; margin-top: 2px;\">${message}</div>\n" +
                "            </div>\n" +
                "        `;\n" +
                "    };\n" +
                "    const dismissToast = (toastElement) => {\n" +
                "        toastElement.style.opacity = '0';\n" +
                "        toastElement.style.transform = 'translateY(-20px) scale(0.9)';\n" +
                "        setTimeout(() => {\n" +
                "            if (toastElement.parentNode) {\n" +
                "                toastElement.parentNode.removeChild(toastElement);\n" +
                "            }\n" +
                "        }, 300);\n" +
                "    };\n" +
                "    if (!window.html2canvas) {\n" +
                "        const script = document.createElement('script');\n" +
                "        script.src = 'https://unpkg.com/html2canvas@1.4.1/dist/html2canvas.min.js';\n" +
                "        script.onload = generate;\n" +
                "        script.onerror = () => {\n" +
                "            updateToast(toast, 'Error', 'Failed to load rendering engine.', '#ff3333');\n" +
                "            restoreButton();\n" +
                "            setTimeout(() => { dismissToast(toast); }, 4000);\n" +
                "        };\n" +
                "        document.head.appendChild(script);\n" +
                "    } else {\n" +
                "        generate();\n" +
                "    }\n" +
                "};\n" +
                "if (!document.getElementById('infographic-toast-animation')) {\n" +
                "    const style = document.createElement('style');\n" +
                "    style.id = 'infographic-toast-animation';\n" +
                "    style.innerHTML = `@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`;\n" +
                "    document.head.appendChild(style);\n" +
                "}";
    }
}
