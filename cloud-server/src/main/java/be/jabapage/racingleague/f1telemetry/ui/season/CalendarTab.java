package be.jabapage.racingleague.f1telemetry.ui.season;

import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.LeagueLogo;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.repository.LeagueLogoRepository;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.ui.EventResultsView;
import be.jabapage.racingleague.f1telemetry.ui.SeasonDetailsView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.StreamResource;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CalendarTab extends VerticalLayout {

    private final LeagueLogoRepository leagueLogoRepository;

    private League league;
    private Tier selectedTier;
    private List<Event> currentEvents = Collections.emptyList();

    public CalendarTab(LeagueLogoRepository leagueLogoRepository) {
        this.leagueLogoRepository = leagueLogoRepository;
        setSizeFull();
        setPadding(false);
    }

    public void update(League league, Tier selectedTier, List<Event> currentEvents) {
        this.league = league;
        this.selectedTier = selectedTier;
        this.currentEvents = currentEvents;

        removeAll();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.STRETCH);

        if (selectedTier == null) return;

        if (this.currentEvents == null || this.currentEvents.isEmpty()) {
            com.vaadin.flow.component.html.H3 title = new com.vaadin.flow.component.html.H3("Season Schedule & Calendar - " + selectedTier.getName());
            add(title);
            Div emptyMessage = new Div();
            emptyMessage.setText("No race weekends scheduled yet.");
            emptyMessage.getStyle().set("color", "var(--lumo-secondary-text-color)");
            emptyMessage.getStyle().set("font-style", "italic");
            add(emptyMessage);
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
                "window.downloadInfographic('.calendar-poster', 'calendar_' + $0 + '.png', $1)",
                tierNameSafe,
                e.getSource().getElement()
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
            String flag = SeasonDetailsView.getTrackCountryFlag(event.getTrackId());
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

        add(downloadCalendarBtn, calendarPoster);
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
}
