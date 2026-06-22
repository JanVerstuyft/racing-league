package be.jabapage.racingleague.f1telemetry.ui.event;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.EventLineupEntry;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.LeagueLogo;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.entity.TeamMapping;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.EventLineupEntryRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueLogoRepository;
import be.jabapage.racingleague.f1telemetry.repository.SessionResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamMappingRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class LineupTab extends VerticalLayout {

    private final EventLineupEntryRepository eventLineupEntryRepository;
    private final DriverMappingRepository driverMappingRepository;
    private final TeamMappingRepository teamMappingRepository;
    private final LeagueLogoRepository leagueLogoRepository;
    private final SessionResultRepository sessionResultRepository;
    private final SecurityService securityService;
    private final TelemetryProcessingService telemetryProcessingService;

    private Event currentEvent;

    private final Div lineupContainer = new Div();
    private final Grid<EventLineupEntry> lineupGrid = new Grid<>(EventLineupEntry.class, false);
    private Grid.Column<EventLineupEntry> champTeamColumn;
    private final Button addLineupBtn = new Button("Add Driver to Lineup");
    private final Button clearLineupBtn = new Button("Clear Lineup");
    private final Button updateRealLineupBtn = new Button("Update with Real Lineup");

    public LineupTab(EventLineupEntryRepository eventLineupEntryRepository,
                     DriverMappingRepository driverMappingRepository,
                     TeamMappingRepository teamMappingRepository,
                     LeagueLogoRepository leagueLogoRepository,
                     SessionResultRepository sessionResultRepository,
                     SecurityService securityService,
                     TelemetryProcessingService telemetryProcessingService) {
        this.eventLineupEntryRepository = eventLineupEntryRepository;
        this.driverMappingRepository = driverMappingRepository;
        this.teamMappingRepository = teamMappingRepository;
        this.leagueLogoRepository = leagueLogoRepository;
        this.sessionResultRepository = sessionResultRepository;
        this.securityService = securityService;
        this.telemetryProcessingService = telemetryProcessingService;

        setSizeFull();
        setPadding(false);

        lineupContainer.setSizeFull();
        add(lineupContainer);

        configureLineupGrid();
    }

    public void update(Event event) {
        this.currentEvent = event;
        if (event == null) return;

        initLineupIfEmpty();
        updateLineupContent();
    }

    private void initLineupIfEmpty() {
        if (currentEvent == null) return;
        List<EventLineupEntry> existing = eventLineupEntryRepository.findByEvent(currentEvent);
        if (!existing.isEmpty()) return;

        List<DriverMapping> leagueDrivers = driverMappingRepository.findByLeague(currentEvent.getTier().getLeague());
        List<DriverMapping> regularDrivers = leagueDrivers.stream()
                .filter(d -> !d.isReserve())
                .filter(d -> Objects.equals(d.getTier(), currentEvent.getTier()))
                .toList();

        if (regularDrivers.isEmpty()) {
            regularDrivers = leagueDrivers.stream()
                .filter(d -> !d.isReserve())
                .toList();
        }

        Map<Integer, List<DriverMapping>> teamDrivers = new HashMap<>();
        String carType = getCarTypeForEvent();

        for (DriverMapping driver : regularDrivers) {
            Integer teamId = driver.getTeamId();
            if (teamId != null && teamId != -1) {
                teamDrivers.computeIfAbsent(teamId, k -> new ArrayList<>()).add(driver);
            }
        }

        List<String> warnedTeams = new ArrayList<>();
        for (Map.Entry<Integer, List<DriverMapping>> entry : teamDrivers.entrySet()) {
            Integer teamId = entry.getKey();
            List<DriverMapping> drivers = entry.getValue();
            String teamName = TelemetryProcessingService.getTeamNameStatic(teamId, carType);

            int limit = Math.min(drivers.size(), 2);
            for (int i = 0; i < limit; i++) {
                EventLineupEntry ele = new EventLineupEntry();
                ele.setEvent(currentEvent);
                ele.setDriver(drivers.get(i));
                ele.setTeamId(teamId);
                ele.setCarType(carType);
                eventLineupEntryRepository.save(ele);
            }

            if (drivers.size() > 2) {
                warnedTeams.add(teamName);
            }
        }

        if (!warnedTeams.isEmpty()) {
            String warningMsg = "Lineup pre-populated. Warning: The following teams have more than 2 regular drivers: "
                    + String.join(", ", warnedTeams) + ". Only the first 2 drivers were automatically assigned.";
            Notification notification = Notification.show(warningMsg, 8000, Notification.Position.MIDDLE);
            notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_WARNING);
        }
    }

    private void updateLineupContent() {
        lineupContainer.removeAll();
        if (currentEvent == null) return;

        List<EventLineupEntry> lineup = eventLineupEntryRepository.findByEvent(currentEvent);
        lineupGrid.setItems(lineup);

        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        addLineupBtn.setVisible(loggedIn);
        clearLineupBtn.setVisible(loggedIn);
        updateRealLineupBtn.setVisible(loggedIn);

        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(true);

        if (loggedIn) {
            HorizontalLayout toolbar = new HorizontalLayout(addLineupBtn, updateRealLineupBtn, clearLineupBtn);
            toolbar.setSpacing(true);
            layout.add(new H2("Lineup Manager"), toolbar, lineupGrid);
        }

        String carType = getCarTypeForEvent();
        Map<Integer, List<EventLineupEntry>> teamAssignments = lineup.stream()
                .collect(Collectors.groupingBy(EventLineupEntry::getTeamId));

        League league = currentEvent.getTier().getLeague();
        boolean teamsEnabled = league != null && Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
            && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
            && league.getTeamBName() != null && !league.getTeamBName().isEmpty();

        if (champTeamColumn != null) {
            champTeamColumn.setVisible(teamsEnabled);
        }

        List<Integer> leftTeamIds = new ArrayList<>();
        List<Integer> rightTeamIds = new ArrayList<>();
        List<Integer> splitTeamIds = new ArrayList<>();

        List<TeamMapping> allTeamsForCarType = getTeamsForCarType(carType);
        List<Integer> allConstructorIds = allTeamsForCarType.stream().map(TeamMapping::getTeamId).toList();

        if (teamsEnabled) {
            for (Integer teamId : allConstructorIds) {
                List<EventLineupEntry> teamEntries = lineup.stream().filter(e -> Objects.equals(e.getTeamId(), teamId)).toList();
                List<String> teams = new ArrayList<>();
                if (!teamEntries.isEmpty()) {
                    for (EventLineupEntry entry : teamEntries) {
                        String ct = entry.getChampionshipTeam() != null ? entry.getChampionshipTeam() : entry.getDriver().getChampionshipTeam();
                        if (ct != null) {
                            teams.add(ct);
                        }
                    }
                } else {
                    List<DriverMapping> regularDrivers = driverMappingRepository.findByTier(currentEvent.getTier()).stream()
                        .filter(m -> !m.isReserve() && Objects.equals(m.getTeamId(), teamId))
                        .toList();
                    for (DriverMapping dm : regularDrivers) {
                        if (dm.getChampionshipTeam() != null) {
                            teams.add(dm.getChampionshipTeam());
                        }
                    }
                }

                boolean hasA = teams.contains("A");
                boolean hasB = teams.contains("B");

                if (hasA && hasB) {
                    splitTeamIds.add(teamId);
                } else if (hasA) {
                    leftTeamIds.add(teamId);
                } else if (hasB) {
                    rightTeamIds.add(teamId);
                } else {
                    if ("F1 26".equals(carType)) {
                        if (List.of(484, 478, 480, 483, 479).contains(teamId)) {
                            leftTeamIds.add(teamId);
                        } else if (List.of(477, 476, 481, 482, 485).contains(teamId)) {
                            rightTeamIds.add(teamId);
                        } else {
                            splitTeamIds.add(teamId);
                        }
                    } else {
                        if (List.of(8, 2, 4, 7, 3).contains(teamId)) {
                            leftTeamIds.add(teamId);
                        } else {
                            rightTeamIds.add(teamId);
                        }
                    }
                }
            }
        } else {
            if ("F1 26".equals(carType)) {
                leftTeamIds = List.of(484, 478, 480, 483, 479);
                rightTeamIds = List.of(477, 476, 481, 482, 485);
                splitTeamIds = List.of(486);
            } else {
                leftTeamIds = List.of(8, 2, 4, 7, 3);
                rightTeamIds = List.of(1, 0, 5, 6, 9);
            }
        }

        Div posterWrapper = new Div();
        posterWrapper.addClassName("lineup-poster-wrapper");

        Div poster = new Div();
        poster.addClassName("lineup-poster");

        if (league.getLogoBackgroundColor() != null && !league.getLogoBackgroundColor().isEmpty()) {
            poster.getStyle().set("background", "linear-gradient(135deg, " + league.getLogoBackgroundColor() + " 0%, #090a0f 100%)");
        }

        String accentColor = league.getAccentColor() != null && !league.getAccentColor().isEmpty()
                ? league.getAccentColor()
                : "#eef30d";
        poster.getStyle().set("--lineup-accent-color", accentColor);

        Div topLeftRibbon = new Div(new Span(league.getName()));
        topLeftRibbon.addClassName("lineup-ribbon");
        topLeftRibbon.addClassName("lineup-ribbon-top-left");

        Div bottomRightRibbon = new Div(new Span(league.getName()));
        bottomRightRibbon.addClassName("lineup-ribbon");
        bottomRightRibbon.addClassName("lineup-ribbon-bottom-right");

        poster.add(topLeftRibbon, bottomRightRibbon);

        Div header = new Div();
        header.addClassName("lineup-poster-header");
        H4 subtitle = new H4(currentEvent.getTier().getName().toUpperCase());
        subtitle.addClassName("lineup-poster-title-mini");
        H1 title = new H1(currentEvent.getEventName().toUpperCase() + " DRIVER LINE-UP");
        title.addClassName("lineup-poster-title-main");
        header.add(subtitle, title);
        poster.add(header);

        Div posterGrid = new Div();
        posterGrid.addClassName("lineup-poster-grid");

        Div leftCol = new Div();
        leftCol.addClassName("lineup-column");
        if (teamsEnabled) {
            Div leftHeader = new Div(new Span(league.getTeamAName().toUpperCase()));
            leftHeader.addClassName("lineup-column-header");
            leftCol.add(leftHeader);
        }
        for (Integer teamId : leftTeamIds) {
            leftCol.add(createTeamCard(teamId, carType, teamAssignments, teamsEnabled, splitTeamIds));
        }
        posterGrid.add(leftCol);

        Div centerCol = new Div();
        centerCol.addClassName("lineup-center-column");

        Div logoContainer = new Div();
        logoContainer.addClassName("lineup-trophy-container");
        
        byte[] logoBytes = leagueLogoRepository.findById(league.getId())
                .map(LeagueLogo::getLogo)
                .orElse(null);
                
        if (logoBytes != null && logoBytes.length > 0) {
            String base64Logo = java.util.Base64.getEncoder().encodeToString(logoBytes);
            String dataUrl = "data:image/png;base64," + base64Logo;
            Image leagueLogoImg = new Image(dataUrl, "League Logo");
            leagueLogoImg.getStyle().set("max-height", "110px");
            leagueLogoImg.getStyle().set("max-width", "180px");
            leagueLogoImg.getStyle().set("object-fit", "contain");
            logoContainer.add(leagueLogoImg);
        } else {
            Span trophyIcon = new Span("🏆");
            trophyIcon.addClassName("lineup-trophy-icon");
            logoContainer.add(trophyIcon);
        }
        centerCol.add(logoContainer);

        for (Integer teamId : splitTeamIds) {
            centerCol.add(createTeamCard(teamId, carType, teamAssignments, teamsEnabled, splitTeamIds));
        }

        posterGrid.add(centerCol);

        Div rightCol = new Div();
        rightCol.addClassName("lineup-column");
        if (teamsEnabled) {
            Div rightHeader = new Div(new Span(league.getTeamBName().toUpperCase()));
            rightHeader.addClassName("lineup-column-header");
            rightCol.add(rightHeader);
        }
        for (Integer teamId : rightTeamIds) {
            rightCol.add(createTeamCard(teamId, carType, teamAssignments, teamsEnabled, splitTeamIds));
        }
        posterGrid.add(rightCol);

        poster.add(posterGrid);

        Div footer = new Div();
        footer.addClassName("lineup-poster-footer");
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

        Span watermark = new Span("made by https://racingleague.jabapage.be");
        watermark.addClassName("poster-watermark");
        poster.add(watermark);

        posterWrapper.add(poster);

        Button downloadBtn = new Button("Download Lineup Image");
        downloadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        downloadBtn.getStyle().set("margin-bottom", "15px");
        downloadBtn.addClickListener(e -> {
            getElement().executeJs(
                "window.downloadInfographic('.lineup-poster', 'lineup_' + $0 + '.png', $1)",
                currentEvent.getEventName().toLowerCase().replace(" ", "_"),
                e.getSource().getElement()
            );
        });

        layout.add(new H2("Lineup Poster"), downloadBtn, posterWrapper);
        lineupContainer.add(layout);
    }

    private Div createTeamCard(Integer teamId, String carType, Map<Integer, List<EventLineupEntry>> teamAssignments, boolean teamsEnabled, List<Integer> splitTeamIds) {
        League league = (currentEvent != null && currentEvent.getTier() != null) ? currentEvent.getTier().getLeague() : null;
        Div card = new Div();
        card.addClassName("lineup-team-card");
        card.getStyle().set("--team-color", PosterRenderer.getTeamColor(teamId, carType));

        Div header = new Div();
        header.addClassName("lineup-team-header");
        Span name = new Span(TelemetryProcessingService.getTeamNameStatic(teamId, carType));
        name.addClassName("lineup-team-name");
        Span symbol = new Span(PosterRenderer.getTeamSymbol(teamId, carType));
        symbol.addClassName("lineup-team-symbol");
        header.add(name, symbol);
        card.add(header);

        Div row = new Div();
        row.addClassName("lineup-drivers-row");

        List<EventLineupEntry> entries = teamAssignments.getOrDefault(teamId, Collections.emptyList());
        boolean isSplit = teamsEnabled && splitTeamIds != null && splitTeamIds.contains(teamId);

        for (int i = 0; i < 2; i++) {
            Div slot = new Div();
            slot.addClassName("lineup-driver-slot");
            
            Div slotContent = new Div();
            slotContent.addClassName("lineup-driver-slot-content");
            
            Span driverName = new Span();
            driverName.addClassName("lineup-driver-name");

            EventLineupEntry matchedEntry = null;
            if (isSplit) {
                String targetTeam = (i == 0) ? "A" : "B";
                matchedEntry = entries.stream()
                        .filter(e -> {
                            String ct = e.getChampionshipTeam() != null ? e.getChampionshipTeam() : e.getDriver().getChampionshipTeam();
                            return targetTeam.equals(ct);
                        })
                        .findFirst()
                        .orElse(null);
            } else {
                if (i < entries.size()) {
                    matchedEntry = entries.get(i);
                }
            }

            if (matchedEntry != null) {
                DriverMapping dm = matchedEntry.getDriver();
                String dispName = dm.getOverriddenName() != null && !dm.getOverriddenName().isEmpty() 
                    ? dm.getOverriddenName() 
                    : dm.getTelemetryName();
                driverName.setText(dispName);
                slot.addClassName("assigned");
                
                slotContent.add(driverName);

                if (teamsEnabled && league != null) {
                    String ct = matchedEntry.getChampionshipTeam() != null ? matchedEntry.getChampionshipTeam() : dm.getChampionshipTeam();
                    if ("A".equals(ct)) {
                        Span badge = new Span(league.getTeamAName().toUpperCase());
                        badge.addClassName("lineup-driver-team-badge");
                        badge.addClassName("team-a");
                        slotContent.add(badge);
                    } else if ("B".equals(ct)) {
                        Span badge = new Span(league.getTeamBName().toUpperCase());
                        badge.addClassName("lineup-driver-team-badge");
                        badge.addClassName("team-b");
                        slotContent.add(badge);
                    }
                }
            } else {
                driverName.setText("VACANT");
                slot.addClassName("vacant");
                slotContent.add(driverName);
            }
            slot.add(slotContent);
            row.add(slot);
        }
        card.add(row);
        return card;
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

    private void showAddLineupDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Driver to Lineup");

        ComboBox<DriverMapping> driverCombo = new ComboBox<>("Driver");
        driverCombo.setItems(driverMappingRepository.findByTier(currentEvent.getTier()).stream()
                .sorted(Comparator.comparing(m -> m.getOverriddenName() != null ? m.getOverriddenName() : m.getTelemetryName()))
                .collect(Collectors.toList()));
        driverCombo.setItemLabelGenerator(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty() 
            ? m.getOverriddenName() 
            : m.getTelemetryName());
        driverCombo.setWidthFull();

        Button createNewDriverBtn = new Button("New Driver");
        createNewDriverBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        createNewDriverBtn.addClickListener(e -> showCreateManualDriverDialog(driverCombo));

        HorizontalLayout driverLayout = new HorizontalLayout(driverCombo, createNewDriverBtn);
        driverLayout.setAlignItems(Alignment.END);
        driverLayout.setWidthFull();

        ComboBox<TeamMapping> teamCombo = new ComboBox<>("Team");
        teamCombo.setWidthFull();

        String carType = getCarTypeForEvent();
        List<TeamMapping> allTeams = getTeamsForCarType(carType);

        League league = currentEvent.getTier().getLeague();
        boolean teamsEnabled = league != null && Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
            && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
            && league.getTeamBName() != null && !league.getTeamBName().isEmpty();

        ComboBox<String> champTeamCombo = new ComboBox<>("Championship Team");
        champTeamCombo.setItems("A", "B", "None");
        champTeamCombo.setItemLabelGenerator(val -> {
            if ("A".equals(val)) {
                return league.getTeamAName() != null ? league.getTeamAName() : "Team A";
            } else if ("B".equals(val)) {
                return league.getTeamBName() != null ? league.getTeamBName() : "Team B";
            }
            return "None";
        });
        champTeamCombo.setValue("None");
        champTeamCombo.setWidthFull();
        champTeamCombo.setVisible(teamsEnabled);

        Span note = new Span();
        note.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        driverCombo.addValueChangeListener(e -> {
            DriverMapping dm = e.getValue();
            if (dm == null) {
                teamCombo.clear();
                teamCombo.setReadOnly(false);
                champTeamCombo.setValue("None");
                champTeamCombo.setReadOnly(false);
                note.setText("");
                return;
            }

            if (!dm.isReserve()) {
                Integer teamId = dm.getTeamId();
                if (teamId != null) {
                    Optional<TeamMapping> tmOpt = teamMappingRepository.findByTeamIdAndCarType(teamId, carType);
                    if (tmOpt.isPresent()) {
                        String teamName = tmOpt.get().getTeamName();
                        TeamMapping uiTeam = allTeams.stream()
                                .filter(t -> Objects.equals(t.getTeamName(), teamName))
                                .findFirst()
                                .orElse(null);
                        teamCombo.setItems(allTeams);
                        teamCombo.setValue(uiTeam);
                        teamCombo.setReadOnly(true);
                        
                        if (dm.getChampionshipTeam() != null) {
                            champTeamCombo.setValue(dm.getChampionshipTeam());
                            champTeamCombo.setReadOnly(true);
                        } else {
                            champTeamCombo.setValue("None");
                            champTeamCombo.setReadOnly(false);
                        }
                        
                        note.setText("Regular driver auto-assigned to their primary team.");
                        return;
                    }
                }
                teamCombo.setReadOnly(false);
                teamCombo.setItems(allTeams);
                if (dm.getChampionshipTeam() != null) {
                    champTeamCombo.setValue(dm.getChampionshipTeam());
                    champTeamCombo.setReadOnly(true);
                } else {
                    champTeamCombo.setValue("None");
                    champTeamCombo.setReadOnly(false);
                }
                note.setText("Regular driver (no primary team assigned) - please assign a team.");
            } else {
                teamCombo.setReadOnly(false);
                List<EventLineupEntry> currentLineup = eventLineupEntryRepository.findByEvent(currentEvent);
                Map<Integer, Long> teamCounts = currentLineup.stream()
                        .collect(Collectors.groupingBy(EventLineupEntry::getTeamId, Collectors.counting()));
                List<TeamMapping> remainingTeams = allTeams.stream()
                        .filter(tm -> teamCounts.getOrDefault(tm.getTeamId(), 0L) < 2)
                        .toList();
                
                teamCombo.setItems(remainingTeams);
                champTeamCombo.setReadOnly(false);
                champTeamCombo.setValue("None");
                note.setText("Reserve driver - select from remaining teams with open seats.");
            }
        });

        teamCombo.setItemLabelGenerator(TeamMapping::getTeamName);

        VerticalLayout dialogLayout = new VerticalLayout(driverLayout, teamCombo, champTeamCombo, note);
        dialog.add(dialogLayout);

        Button saveBtn = new Button("Save", ev -> {
            if (driverCombo.getValue() == null || teamCombo.getValue() == null) {
                Notification.show("Please select both Driver and Team", 3000, Notification.Position.TOP_CENTER);
                return;
            }

            DriverMapping driver = driverCombo.getValue();
            TeamMapping team = teamCombo.getValue();

            EventLineupEntry ele = new EventLineupEntry();
            ele.setEvent(currentEvent);
            ele.setDriver(driver);
            ele.setTeamId(team.getTeamId());
            ele.setCarType(carType);
            if (teamsEnabled && !"None".equals(champTeamCombo.getValue())) {
                ele.setChampionshipTeam(champTeamCombo.getValue());
            } else {
                ele.setChampionshipTeam(null);
            }

            try {
                eventLineupEntryRepository.save(ele);
                updateLineupContent();
                dialog.close();
                Notification.show("Driver added to lineup", 3000, Notification.Position.TOP_CENTER);
            } catch (Exception ex) {
                Notification.show("Error: Driver might already be assigned.", 5000, Notification.Position.TOP_CENTER);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
        dialog.open();
    }

    private void showCreateManualDriverDialog(ComboBox<DriverMapping> parentCombo) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create New Driver");

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

        VerticalLayout dialogLayout = new VerticalLayout(nameField, telemetryNameField, raceNumField, countryCombo);
        dialog.add(dialogLayout);

        Button saveBtn = new Button("Create", ev -> {
            if (nameField.getValue().isEmpty()) {
                Notification.show("Please enter a display name", 3000, Notification.Position.TOP_CENTER);
                return;
            }

            DriverMapping dm = new DriverMapping();
            dm.setLeague(currentEvent.getTier().getLeague());
            dm.setOverriddenName(nameField.getValue());
            dm.setTelemetryName(telemetryNameField.getValue().isEmpty() ? nameField.getValue() : telemetryNameField.getValue());
            dm.setRaceNumber(raceNumField.getValue() != null ? raceNumField.getValue() : 0);
            dm.setCountry(countryCombo.getValue() != null ? countryCombo.getValue() : "Unknown");
            dm.setDriverId(255);
            dm.setTier(currentEvent.getTier());

            driverMappingRepository.save(dm);
            
            List<DriverMapping> tierDrivers = driverMappingRepository.findByTier(currentEvent.getTier());
            List<DriverMapping> lineupDrivers = eventLineupEntryRepository.findByEvent(currentEvent).stream()
                    .map(EventLineupEntry::getDriver)
                    .toList();
            java.util.Set<DriverMapping> allDrivers = new java.util.LinkedHashSet<>(tierDrivers);
            allDrivers.addAll(lineupDrivers);

            parentCombo.setItems(allDrivers.stream()
                    .sorted(Comparator.comparing(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty() ? m.getOverriddenName() : m.getTelemetryName()))
                    .collect(Collectors.toList()));
            parentCombo.setValue(dm);

            dialog.close();
            Notification.show("Driver created successfully", 3000, Notification.Position.TOP_CENTER);
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
        dialog.open();
    }

    private void configureLineupGrid() {
        addLineupBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addLineupBtn.addClickListener(e -> showAddLineupDialog());

        clearLineupBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        clearLineupBtn.addClickListener(e -> {
            if (currentEvent == null) return;
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Clear Lineup?");
            dialog.setText("Are you sure you want to clear the entire lineup for this event?");
            dialog.setCancelable(true);
            dialog.setConfirmText("Clear");
            dialog.setConfirmButtonTheme("error primary");
            dialog.addConfirmListener(ev -> {
                eventLineupEntryRepository.deleteAll(eventLineupEntryRepository.findByEvent(currentEvent));
                updateLineupContent();
                Notification.show("Lineup cleared", 3000, Notification.Position.TOP_CENTER);
            });
            dialog.open();
        });

        updateRealLineupBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        updateRealLineupBtn.addClickListener(e -> {
            if (currentEvent == null) return;
            showUpdateRealLineupConfirmDialog();
        });

        lineupGrid.addComponentColumn(entry -> {
            DriverMapping dm = entry.getDriver();
            String nameText = dm.getOverriddenName() != null && !dm.getOverriddenName().isEmpty() 
                ? dm.getOverriddenName() 
                : dm.getTelemetryName();
            Span flagSpan = new Span(CountryProvider.getFlagByName(dm.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            Span name = new Span(nameText);
            HorizontalLayout row = new HorizontalLayout(flagSpan, name);
            row.setAlignItems(Alignment.CENTER);
            row.setSpacing(false);
            return row;
        }).setHeader("Driver").setSortable(true);

        lineupGrid.addColumn(entry -> {
            return TelemetryProcessingService.getTeamNameStatic(entry.getTeamId(), entry.getCarType());
        }).setHeader("Team").setSortable(true);

        champTeamColumn = lineupGrid.addColumn(entry -> {
            League lg = (currentEvent != null && currentEvent.getTier() != null) ? currentEvent.getTier().getLeague() : null;
            String ct = entry.getChampionshipTeam() != null ? entry.getChampionshipTeam() : (entry.getDriver() != null ? entry.getDriver().getChampionshipTeam() : null);
            if ("A".equals(ct)) {
                return lg != null && lg.getTeamAName() != null ? lg.getTeamAName() : "Team A";
            } else if ("B".equals(ct)) {
                return lg != null && lg.getTeamBName() != null ? lg.getTeamBName() : "Team B";
            }
            return "None";
        }).setHeader("Championship Team");

        lineupGrid.addColumn(entry -> {
            return entry.getDriver().isReserve() ? "Reserve Driver" : "Regular Driver";
        }).setHeader("Status");

        lineupGrid.addComponentColumn(entry -> {
            Button deleteBtn = new Button("Remove", e -> {
                ConfirmDialog dialog = new ConfirmDialog();
                dialog.setHeader("Remove from Lineup?");
                dialog.setText("Are you sure you want to remove '" 
                    + entry.getDriver().getOverriddenName() + "' from this weekend's lineup?");
                dialog.setCancelable(true);
                dialog.setConfirmText("Remove");
                dialog.setConfirmButtonTheme("error primary");
                dialog.addConfirmListener(ev -> {
                    eventLineupEntryRepository.delete(entry);
                    updateLineupContent();
                    Notification.show("Driver removed from lineup", 3000, Notification.Position.TOP_CENTER);
                });
                dialog.open();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            deleteBtn.setVisible(securityService.getAuthenticatedUser().isPresent());
            return deleteBtn;
        }).setHeader("Actions");

        lineupGrid.setAllRowsVisible(true);
    }

    private void showUpdateRealLineupConfirmDialog() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Update with Real Lineup?");
        dialog.setText("Are you sure you want to clear the current lineup and update it with the actual drivers who participated in this weekend's session results?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Update");
        dialog.setConfirmButtonTheme("primary");
        dialog.addConfirmListener(ev -> {
            List<SessionResult> sessions = sessionResultRepository.findByTier(currentEvent.getTier()).stream()
                    .filter(sr -> sr.getEvent() != null && Objects.equals(sr.getEvent().getId(), currentEvent.getId()))
                    .toList();
            if (sessions.isEmpty()) {
                Notification.show("No telemetry session results found for this weekend to update from.", 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_WARNING);
                return;
            }
            List<DriverMapping> mappings = driverMappingRepository.findByLeague(currentEvent.getTier().getLeague());
            Map<DriverMapping, Integer> driverToTeamMap = new java.util.LinkedHashMap<>();
            for (SessionResult sr : sessions) {
                for (DriverResult dr : sr.getDriverResults()) {
                    if (dr.isAi()) {
                        continue;
                    }
                    DriverMapping mapping = findMappingForDriverResult(dr, mappings, currentEvent.getTier());
                    if (mapping != null && dr.getTeamId() != null && dr.getTeamId() != -1) {
                        driverToTeamMap.put(mapping, dr.getTeamId());
                    }
                }
            }
            if (driverToTeamMap.isEmpty()) {
                Notification.show("No matching driver mappings found in telemetry session results.", 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_WARNING);
                return;
            }
            eventLineupEntryRepository.deleteAll(eventLineupEntryRepository.findByEvent(currentEvent));
            
            String carType = getCarTypeForEvent();
            for (Map.Entry<DriverMapping, Integer> entry : driverToTeamMap.entrySet()) {
                EventLineupEntry ele = new EventLineupEntry();
                ele.setEvent(currentEvent);
                ele.setDriver(entry.getKey());
                ele.setTeamId(entry.getValue());
                ele.setCarType(carType);
                if (entry.getKey().getChampionshipTeam() != null) {
                    ele.setChampionshipTeam(entry.getKey().getChampionshipTeam());
                }
                eventLineupEntryRepository.save(ele);
            }
            
            updateLineupContent();
            Notification.show("Lineup updated with real participants", 3000, Notification.Position.TOP_CENTER);
        });
        dialog.open();
    }

    private DriverMapping findMappingForDriverResult(DriverResult result, List<DriverMapping> mappings, Tier tier) {
        if (result.getTelemetryName() == null || result.getRaceNumber() == null || result.getDriverId() == null) {
            return null;
        }
        DriverMapping bestMatch = null;
        for (DriverMapping m : mappings) {
            if (Objects.equals(m.getTelemetryName(), result.getTelemetryName())
                    && Objects.equals(m.getRaceNumber(), result.getRaceNumber())
                    && Objects.equals(m.getDriverId(), result.getDriverId())
                    && Objects.equals(m.getCountry(), result.getCountry())) {
                if (tier != null && m.getTier() != null && Objects.equals(m.getTier().getId(), tier.getId())) {
                    return m;
                }
                if (bestMatch == null) {
                    bestMatch = m;
                } else if (bestMatch.getTier() != null && m.getTier() == null) {
                    bestMatch = m;
                }
            }
        }
        return bestMatch;
    }

    private List<TeamMapping> getTeamsForCarType(String carType) {
        List<TeamMapping> teams = teamMappingRepository.findByCarType(carType);
        if ("F1 26".equals(carType)) {
            teams = teams.stream().filter(t -> t.getTeamId() != null && t.getTeamId() >= 400).toList();
        }
        return teams;
    }

    private String getCarTypeForEvent() {
        if (currentEvent == null) return "F1 25";
        if (currentEvent.getTier() != null && currentEvent.getTier().getLeague() != null && currentEvent.getTier().getLeague().getCarType() != null) {
            return currentEvent.getTier().getLeague().getCarType();
        }
        String currentEventCarType = currentEvent.getSessionResults().stream()
                .map(SessionResult::getCarType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (currentEventCarType != null) {
            return currentEventCarType;
        }
        return sessionResultRepository.findByTier(currentEvent.getTier()).stream()
                .map(SessionResult::getCarType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("F1 25");
    }
}
