package be.jabapage.racingleague.f1telemetry.ui.event;

import be.jabapage.racingleague.f1telemetry.entity.*;
import be.jabapage.racingleague.f1telemetry.model.ConsistencyStats;
import be.jabapage.racingleague.f1telemetry.model.LongestStintStats;
import be.jabapage.racingleague.f1telemetry.model.RacePaceStats;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.ui.EventResultsView;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;

import java.util.*;
import java.util.stream.Collectors;

public class PosterRenderer {

    public static Div createResultsPoster(Event currentEvent, SessionResult session, List<DriverResult> driverResults, String carType) {
        boolean isQualifying = session.getSessionType() >= 5 && session.getSessionType() <= 14;

        Div body = new Div();
        body.addClassName("results-poster-body");

        // Podium container
        Div podiumContainer = new Div();
        podiumContainer.addClassName("results-podium-container");

        DriverResult first = driverResults.size() > 0 ? driverResults.get(0) : null;
        DriverResult second = driverResults.size() > 1 ? driverResults.get(1) : null;
        DriverResult third = driverResults.size() > 2 ? driverResults.get(2) : null;

        podiumContainer.add(createPodiumStep(currentEvent, second, 2, carType, isQualifying));
        podiumContainer.add(createPodiumStep(currentEvent, first, 1, carType, isQualifying));
        podiumContainer.add(createPodiumStep(currentEvent, third, 3, carType, isQualifying));
        body.add(podiumContainer);

        // List container
        Div listContainer = new Div();
        listContainer.addClassName("results-list-container");

        if (driverResults.size() > 3) {
            List<DriverResult> remaining = driverResults.subList(3, driverResults.size());
            int mid = (remaining.size() + 1) / 2;

            Div leftCol = new Div();
            leftCol.addClassName("results-list-column");
            for (int i = 0; i < mid; i++) {
                leftCol.add(createListRow(remaining.get(i), i + 4, carType));
            }

            Div rightCol = new Div();
            rightCol.addClassName("results-list-column");
            for (int i = mid; i < remaining.size(); i++) {
                rightCol.add(createListRow(remaining.get(i), i + 4, carType));
            }

            listContainer.add(leftCol, rightCol);
        }
        body.add(listContainer);

        List<SessionResult> sessions = getOrderedSessions(currentEvent);
        String sessionName = EventResultsView.getDynamicSessionName(session.getSessionType(), sessions.stream().map(SessionResult::getSessionType).toList());
        String title = currentEvent.getEventName() + " " + sessionName;

        return createBasePoster(currentEvent, title, body, "results-poster");
    }

    public static Div createPitStopsPoster(Event currentEvent, SessionResult session, List<DriverResult> driverResults, String carType) {
        Div body = new Div();
        body.addClassName("results-poster-body");

        Div rowsContainer = new Div();
        rowsContainer.addClassName("pitstops-rows-container");

        // Calculate max laps in the session
        int maxLaps = driverResults.stream()
                .mapToInt(dr -> dr.getNumLaps() != null ? dr.getNumLaps() : 0)
                .max().orElse(50);
        if (maxLaps <= 0) maxLaps = 50;

        for (int i = 0; i < driverResults.size(); i++) {
            DriverResult dr = driverResults.get(i);
            Div row = new Div();
            row.addClassName("pitstops-row");

            Span posSpan = new Span(String.valueOf(i + 1));
            posSpan.addClassName("pitstops-row-pos");

            Span flagSpan = new Span(CountryProvider.getFlagByName(dr.getCountry()));
            flagSpan.addClassName("pitstops-row-flag");

            Span nameSpan = new Span(dr.getDriverName());
            nameSpan.addClassName("pitstops-row-name");

            Div timelineWrapper = new Div();
            timelineWrapper.addClassName("pitstops-timeline-wrapper");

            Div timeline = new Div();
            timeline.addClassName("pitstops-timeline");

            // Extract stints
            List<TyreStint> stints = dr.getTyreStints().stream()
                    .sorted(java.util.Comparator.comparingInt(TyreStint::getStintOrder))
                    .toList();

            int drLaps = dr.getNumLaps() != null ? dr.getNumLaps() : 0;
            List<StintSegment> segments = new ArrayList<>();
            if (stints.isEmpty() && drLaps > 0) {
                segments.add(new StintSegment(drLaps, drLaps, "Dry"));
            } else {
                int currentEndLap = 0;
                for (TyreStint stint : stints) {
                    int laps = stint.getLaps();
                    currentEndLap = stint.getEndLap() != null ? stint.getEndLap() : (currentEndLap + laps);
                    String compound = TelemetryProcessingService.TYRE_COMPOUNDS.getOrDefault(stint.getTyreCompound(), "Dry");
                    segments.add(new StintSegment(laps, currentEndLap, compound));
                }
            }

            int activeLapsSum = 0;
            for (StintSegment seg : segments) {
                Div segmentDiv = new Div();
                segmentDiv.addClassName("pitstops-segment");
                segmentDiv.getStyle().set("flex", String.valueOf(seg.laps));

                // Tyre compound style
                switch (seg.compound) {
                    case "Soft" -> segmentDiv.addClassName("tyre-soft");
                    case "Medium" -> segmentDiv.addClassName("tyre-medium");
                    case "Hard" -> segmentDiv.addClassName("tyre-hard");
                    case "Inter" -> segmentDiv.addClassName("tyre-inter");
                    case "Wet" -> segmentDiv.addClassName("tyre-wet");
                    default -> segmentDiv.addClassName("tyre-unknown");
                }

                Span label = new Span(String.format("%02d", seg.endLap));
                label.addClassName("pitstops-lap-label");
                segmentDiv.add(label);

                timeline.add(segmentDiv);
                activeLapsSum += seg.laps;
            }

            // If retired/incomplete, add a spacer
            if (activeLapsSum < maxLaps) {
                Div spacerDiv = new Div();
                spacerDiv.addClassName("pitstops-segment-spacer");
                spacerDiv.getStyle().set("flex", String.valueOf(maxLaps - activeLapsSum));
                timeline.add(spacerDiv);
            }

            timelineWrapper.add(timeline);
            row.add(posSpan, flagSpan, nameSpan, timelineWrapper);
            rowsContainer.add(row);
        }

        body.add(rowsContainer);

        String trackName = getTrackNameForEvent(currentEvent);
        String title = trackName + " Pit Stops";

        return createBasePoster(currentEvent, title, body, "pitstops-poster");
    }

    public static Div createPacePoster(Event currentEvent, SessionResult session, List<RacePaceStats> stats, String carType) {
        Div body = new Div();
        body.addClassName("results-poster-body");

        Div podiumContainer = new Div();
        podiumContainer.addClassName("results-podium-container");

        RacePaceStats first = stats.size() > 0 ? stats.get(0) : null;
        RacePaceStats second = stats.size() > 1 ? stats.get(1) : null;
        RacePaceStats third = stats.size() > 2 ? stats.get(2) : null;
        double bestPace = first != null ? first.getPureRacePace() : 0.0;

        podiumContainer.add(createPacePodiumStep(currentEvent, second, 2, carType, bestPace));
        podiumContainer.add(createPacePodiumStep(currentEvent, first, 1, carType, bestPace));
        podiumContainer.add(createPacePodiumStep(currentEvent, third, 3, carType, bestPace));
        body.add(podiumContainer);

        Div listContainer = new Div();
        listContainer.addClassName("results-list-container");

        if (stats.size() > 3) {
            List<RacePaceStats> remaining = stats.subList(3, stats.size());
            int mid = (remaining.size() + 1) / 2;

            Div leftCol = new Div();
            leftCol.addClassName("results-list-column");
            for (int i = 0; i < mid; i++) {
                leftCol.add(createPaceListRow(currentEvent, remaining.get(i), i + 4, carType, bestPace));
            }

            Div rightCol = new Div();
            rightCol.addClassName("results-list-column");
            for (int i = mid; i < remaining.size(); i++) {
                rightCol.add(createPaceListRow(currentEvent, remaining.get(i), i + 4, carType, bestPace));
            }

            listContainer.add(leftCol, rightCol);
        }
        body.add(listContainer);

        List<SessionResult> sessions = getOrderedSessions(currentEvent);
        String sessionName = EventResultsView.getDynamicSessionName(session.getSessionType(), sessions.stream().map(SessionResult::getSessionType).toList());
        String title = currentEvent.getEventName() + " " + sessionName + " - Pure Pace";

        return createBasePoster(currentEvent, title, body, "pace-poster");
    }

    public static Div createConsistencyPoster(Event currentEvent, SessionResult session, List<ConsistencyStats> stats, String carType) {
        Div body = new Div();
        body.addClassName("results-poster-body");

        Div podiumContainer = new Div();
        podiumContainer.addClassName("results-podium-container");

        ConsistencyStats first = stats.size() > 0 ? stats.get(0) : null;
        ConsistencyStats second = stats.size() > 1 ? stats.get(1) : null;
        ConsistencyStats third = stats.size() > 2 ? stats.get(2) : null;

        podiumContainer.add(createConsistencyPodiumStep(currentEvent, second, 2, carType));
        podiumContainer.add(createConsistencyPodiumStep(currentEvent, first, 1, carType));
        podiumContainer.add(createConsistencyPodiumStep(currentEvent, third, 3, carType));
        body.add(podiumContainer);

        Div listContainer = new Div();
        listContainer.addClassName("results-list-container");

        if (stats.size() > 3) {
            List<ConsistencyStats> remaining = stats.subList(3, stats.size());
            int mid = (remaining.size() + 1) / 2;

            Div leftCol = new Div();
            leftCol.addClassName("results-list-column");
            for (int i = 0; i < mid; i++) {
                leftCol.add(createConsistencyListRow(currentEvent, remaining.get(i), i + 4, carType));
            }

            Div rightCol = new Div();
            rightCol.addClassName("results-list-column");
            for (int i = mid; i < remaining.size(); i++) {
                rightCol.add(createConsistencyListRow(currentEvent, remaining.get(i), i + 4, carType));
            }

            listContainer.add(leftCol, rightCol);
        }
        body.add(listContainer);

        List<SessionResult> sessions = getOrderedSessions(currentEvent);
        String sessionName = EventResultsView.getDynamicSessionName(session.getSessionType(), sessions.stream().map(SessionResult::getSessionType).toList());
        String title = currentEvent.getEventName() + " " + sessionName + " - Consistency";

        return createBasePoster(currentEvent, title, body, "consistency-poster");
    }

    public static Div createStintsPoster(Event currentEvent, SessionResult session, List<LongestStintStats> stats, String carType) {
        Div body = new Div();
        body.addClassName("results-poster-body");

        Div podiumContainer = new Div();
        podiumContainer.addClassName("results-podium-container");

        LongestStintStats first = stats.size() > 0 ? stats.get(0) : null;
        LongestStintStats second = stats.size() > 1 ? stats.get(1) : null;
        LongestStintStats third = stats.size() > 2 ? stats.get(2) : null;

        podiumContainer.add(createStintsPodiumStep(currentEvent, second, 2, carType));
        podiumContainer.add(createStintsPodiumStep(currentEvent, first, 1, carType));
        podiumContainer.add(createStintsPodiumStep(currentEvent, third, 3, carType));
        body.add(podiumContainer);

        Div listContainer = new Div();
        listContainer.addClassName("results-list-container");

        if (stats.size() > 3) {
            List<LongestStintStats> remaining = stats.subList(3, stats.size());
            int mid = (remaining.size() + 1) / 2;

            Div leftCol = new Div();
            leftCol.addClassName("results-list-column");
            for (int i = 0; i < mid; i++) {
                leftCol.add(createStintsListRow(currentEvent, remaining.get(i), i + 4, carType));
            }

            Div rightCol = new Div();
            rightCol.addClassName("results-list-column");
            for (int i = mid; i < remaining.size(); i++) {
                rightCol.add(createStintsListRow(currentEvent, remaining.get(i), i + 4, carType));
            }

            listContainer.add(leftCol, rightCol);
        }
        body.add(listContainer);

        List<SessionResult> sessions = getOrderedSessions(currentEvent);
        String sessionName = EventResultsView.getDynamicSessionName(session.getSessionType(), sessions.stream().map(SessionResult::getSessionType).toList());
        String title = currentEvent.getEventName() + " " + sessionName + " - Tyre Stints";

        return createBasePoster(currentEvent, title, body, "stints-poster");
    }

    private static Div createBasePoster(Event event, String titleText, Div bodyContent, String posterClass) {
        League league = event.getTier().getLeague();

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
        H4 subtitle = new H4(event.getTier().getName().toUpperCase());
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

    private static Div createSocialItem(String platform, String handle) {
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

    private static String getTrackNameForEvent(Event event) {
        if (event == null || event.getTrackId() == null) return "Unknown Track";
        try {
            int trackIdInt = Integer.parseInt(event.getTrackId());
            return TelemetryProcessingService.TRACK_NAMES.getOrDefault(trackIdInt, "Unknown Track");
        } catch (NumberFormatException e) {
            return event.getTrackId();
        }
    }

    private static Div createPodiumStep(Event event, DriverResult dr, int place, String carType, boolean isQualifying) {
        Div stepContainer = new Div();
        stepContainer.addClassName("podium-step-container");
        stepContainer.addClassName("place-" + place);

        if (dr != null) {
            stepContainer.getStyle().set("--team-color", getTeamColor(dr.getTeamId(), carType));

            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");

            Span name = new Span(dr.getDriverName());
            name.addClassName("podium-driver-name");

            Span team = new Span(dr.getTeamName() != null ? dr.getTeamName() : "");
            team.addClassName("podium-team-name");

            String timeText = "";
            if (place == 1) {
                if (isQualifying) {
                    timeText = EventResultsView.formatLapTime(dr.getBestLapTime() != null ? dr.getBestLapTime() : 0.0f);
                } else {
                    float totalTime = dr.getTotalTime() != null ? dr.getTotalTime().floatValue() : 0.0f;
                    if (totalTime > 0) {
                        timeText = EventResultsView.formatLapTime(totalTime);
                    } else {
                        timeText = EventResultsView.formatLapTime(dr.getBestLapTime() != null ? dr.getBestLapTime() : 0.0f);
                    }
                }
            } else {
                timeText = dr.getGapToLeader() != null && !dr.getGapToLeader().isEmpty() ? dr.getGapToLeader() : "";
                if (timeText.isEmpty() && dr.getBestLapTime() != null && dr.getBestLapTime() > 0) {
                    timeText = EventResultsView.formatLapTime(dr.getBestLapTime());
                }
            }

            Integer status = dr.getResultStatus();
            if (status != null) {
                if (status == 4) name.setText(name.getText() + " (DNF)");
                else if (status == 5) name.setText(name.getText() + " (DSQ)");
                else if (status == 6) name.setText(name.getText() + " (NC)");
                else if (status == 7) name.setText(name.getText() + " (RET)");
            }

            Span time = new Span(timeText);
            time.addClassName("podium-time");

            driverInfo.add(name, team, time);
            stepContainer.add(driverInfo);
        } else {
            stepContainer.getStyle().set("--team-color", "#3e404b");
            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");
            Span name = new Span("VACANT");
            name.addClassName("podium-driver-name");
            name.getStyle().set("color", "#3e404b");
            name.getStyle().set("font-style", "italic");
            driverInfo.add(name);
            stepContainer.add(driverInfo);
        }

        Div step = new Div();
        step.addClassName("podium-step");
        step.addClassName("step-" + place);

        Span number = new Span(String.valueOf(place));
        number.addClassName("podium-number");
        step.add(number);

        stepContainer.add(step);
        return stepContainer;
    }

    private static Div createListRow(DriverResult dr, int pos, String carType) {
        Div row = new Div();
        row.addClassName("results-list-row");
        row.getStyle().set("--team-color", getTeamColor(dr.getTeamId(), carType));

        Span posSpan = new Span(String.valueOf(pos));
        posSpan.addClassName("results-list-pos");

        Div colorBar = new Div();
        colorBar.addClassName("results-list-color-bar");

        String nameText = dr.getDriverName();
        Integer status = dr.getResultStatus();
        if (status != null) {
            if (status == 4) nameText += " (DNF)";
            else if (status == 5) nameText += " (DSQ)";
            else if (status == 6) nameText += " (NC)";
            else if (status == 7) nameText += " (RET)";
        }
        Span nameSpan = new Span(nameText);
        nameSpan.addClassName("results-list-name");

        Span teamSpan = new Span(dr.getTeamName() != null ? dr.getTeamName() : "");
        teamSpan.addClassName("results-list-team");

        String timeText = dr.getGapToLeader() != null && !dr.getGapToLeader().isEmpty() ? dr.getGapToLeader() : "";
        if (timeText.isEmpty() && dr.getBestLapTime() != null && dr.getBestLapTime() > 0) {
            timeText = EventResultsView.formatLapTime(dr.getBestLapTime());
        }
        Span timeSpan = new Span(timeText);
        timeSpan.addClassName("results-list-time");

        row.add(posSpan, colorBar, nameSpan, teamSpan, timeSpan);
        return row;
    }

    private static Div createPacePodiumStep(Event event, RacePaceStats stat, int place, String carType, double bestPace) {
        Div stepContainer = new Div();
        stepContainer.addClassName("podium-step-container");
        stepContainer.addClassName("place-" + place);

        if (stat != null) {
            DriverResult dr = findDriverResultByName(event, stat.getDriverName());
            Integer teamId = dr != null ? dr.getTeamId() : null;
            stepContainer.getStyle().set("--team-color", getTeamColor(teamId, carType));

            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");

            Span name = new Span(stat.getDriverName());
            name.addClassName("podium-driver-name");

            Span team = new Span(stat.getTeamName() != null ? stat.getTeamName() : "");
            team.addClassName("podium-team-name");

            String timeText = "";
            if (place == 1) {
                timeText = EventResultsView.formatLapTime((float) stat.getPureRacePace());
            } else {
                timeText = String.format("+%.3fs", stat.getPureRacePace() - bestPace);
            }
            Span time = new Span(timeText);
            time.addClassName("podium-time");

            driverInfo.add(name, team, time);
            stepContainer.add(driverInfo);
        } else {
            stepContainer.getStyle().set("--team-color", "#3e404b");
            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");
            Span name = new Span("VACANT");
            name.addClassName("podium-driver-name");
            name.getStyle().set("color", "#3e404b").set("font-style", "italic");
            driverInfo.add(name);
            stepContainer.add(driverInfo);
        }

        Div step = new Div();
        step.addClassName("podium-step");
        step.addClassName("step-" + place);
        Span number = new Span(String.valueOf(place));
        number.addClassName("podium-number");
        step.add(number);
        stepContainer.add(step);

        return stepContainer;
    }

    private static Div createPaceListRow(Event event, RacePaceStats stat, int pos, String carType, double bestPace) {
        Div row = new Div();
        row.addClassName("results-list-row");
        DriverResult dr = findDriverResultByName(event, stat.getDriverName());
        Integer teamId = dr != null ? dr.getTeamId() : null;
        row.getStyle().set("--team-color", getTeamColor(teamId, carType));

        Span posSpan = new Span(String.valueOf(pos));
        posSpan.addClassName("results-list-pos");

        Div colorBar = new Div();
        colorBar.addClassName("results-list-color-bar");

        Span nameSpan = new Span(stat.getDriverName());
        nameSpan.addClassName("results-list-name");

        Span teamSpan = new Span(stat.getTeamName() != null ? stat.getTeamName() : "");
        teamSpan.addClassName("results-list-team");

        // Performance badges (S1, S2, S3)
        Div perfCols = new Div();
        perfCols.addClassName("results-list-perf-cols");
        perfCols.add(createPosterPerformanceBadge(stat.getS1Performance()));
        perfCols.add(createPosterPerformanceBadge(stat.getS2Performance()));
        perfCols.add(createPosterPerformanceBadge(stat.getS3Performance()));

        String timeText = stat.getPureRacePace() == bestPace
                ? EventResultsView.formatLapTime((float) stat.getPureRacePace())
                : String.format("+%.3fs", stat.getPureRacePace() - bestPace);
        Span timeSpan = new Span(timeText);
        timeSpan.addClassName("results-list-time");

        row.add(posSpan, colorBar, nameSpan, teamSpan, perfCols, timeSpan);
        return row;
    }

    private static Span createPosterPerformanceBadge(double perf) {
        Span span = new Span(String.format("%.1f", perf));
        span.addClassName("poster-perf-badge");
        if (perf >= 9.0) {
            span.addClassName("poster-perf-purple");
        } else if (perf >= 7.0) {
            span.addClassName("poster-perf-green");
        } else if (perf >= 4.0) {
            span.addClassName("poster-perf-yellow");
        } else {
            span.addClassName("poster-perf-red");
        }
        return span;
    }

    private static Div createConsistencyPodiumStep(Event event, ConsistencyStats stat, int place, String carType) {
        Div stepContainer = new Div();
        stepContainer.addClassName("podium-step-container");
        stepContainer.addClassName("place-" + place);

        if (stat != null) {
            DriverResult dr = findDriverResultByName(event, stat.getDriverName());
            Integer teamId = dr != null ? dr.getTeamId() : null;
            stepContainer.getStyle().set("--team-color", getTeamColor(teamId, carType));

            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");

            Span name = new Span(stat.getDriverName());
            name.addClassName("podium-driver-name");

            Span team = new Span(stat.getTeamName() != null ? stat.getTeamName() : "");
            team.addClassName("podium-team-name");

            Span time = new Span(String.format("Rating: %.1f", stat.getRating()));
            time.addClassName("podium-time");

            driverInfo.add(name, team, time);
            stepContainer.add(driverInfo);
        } else {
            stepContainer.getStyle().set("--team-color", "#3e404b");
            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");
            Span name = new Span("VACANT");
            name.addClassName("podium-driver-name");
            name.getStyle().set("color", "#3e404b").set("font-style", "italic");
            driverInfo.add(name);
            stepContainer.add(driverInfo);
        }

        Div step = new Div();
        step.addClassName("podium-step");
        step.addClassName("step-" + place);
        Span number = new Span(String.valueOf(place));
        number.addClassName("podium-number");
        step.add(number);
        stepContainer.add(step);

        return stepContainer;
    }

    private static Div createConsistencyListRow(Event event, ConsistencyStats stat, int pos, String carType) {
        Div row = new Div();
        row.addClassName("results-list-row");
        DriverResult dr = findDriverResultByName(event, stat.getDriverName());
        Integer teamId = dr != null ? dr.getTeamId() : null;
        row.getStyle().set("--team-color", getTeamColor(teamId, carType));

        Span posSpan = new Span(String.valueOf(pos));
        posSpan.addClassName("results-list-pos");

        Div colorBar = new Div();
        colorBar.addClassName("results-list-color-bar");

        Span nameSpan = new Span(stat.getDriverName());
        nameSpan.addClassName("results-list-name");

        Span teamSpan = new Span(stat.getTeamName() != null ? stat.getTeamName() : "");
        teamSpan.addClassName("results-list-team");

        Span ratingSpan = new Span(String.format("%.1f", stat.getRating()));
        ratingSpan.addClassName("results-list-rating");

        Span diffSpan = new Span(String.format("%.3fs", stat.getAvgDiff()));
        diffSpan.addClassName("results-list-diff");

        row.add(posSpan, colorBar, nameSpan, teamSpan, ratingSpan, diffSpan);
        return row;
    }

    private static Div createStintsPodiumStep(Event event, LongestStintStats stat, int place, String carType) {
        Div stepContainer = new Div();
        stepContainer.addClassName("podium-step-container");
        stepContainer.addClassName("place-" + place);

        if (stat != null) {
            DriverResult dr = findDriverResultByName(event, stat.getDriverName());
            Integer teamId = dr != null ? dr.getTeamId() : null;
            stepContainer.getStyle().set("--team-color", getTeamColor(teamId, carType));

            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");

            Span name = new Span(stat.getDriverName());
            name.addClassName("podium-driver-name");

            Span team = new Span(stat.getTeamName() != null ? stat.getTeamName() : "");
            team.addClassName("podium-team-name");

            Span time = new Span(stat.getLaps() + " Laps (" + stat.getTyreCompound() + ")");
            time.addClassName("podium-time");

            driverInfo.add(name, team, time);
            stepContainer.add(driverInfo);
        } else {
            stepContainer.getStyle().set("--team-color", "#3e404b");
            Div driverInfo = new Div();
            driverInfo.addClassName("podium-driver-info");
            Span name = new Span("VACANT");
            name.addClassName("podium-driver-name");
            name.getStyle().set("color", "#3e404b");
            name.getStyle().set("font-style", "italic");
            driverInfo.add(name);
            stepContainer.add(driverInfo);
        }

        Div step = new Div();
        step.addClassName("podium-step");
        step.addClassName("step-" + place);
        Span number = new Span(String.valueOf(place));
        number.addClassName("podium-number");
        step.add(number);
        stepContainer.add(step);

        return stepContainer;
    }

    private static Div createStintsListRow(Event event, LongestStintStats stat, int pos, String carType) {
        Div row = new Div();
        row.addClassName("results-list-row");
        DriverResult dr = findDriverResultByName(event, stat.getDriverName());
        Integer teamId = dr != null ? dr.getTeamId() : null;
        row.getStyle().set("--team-color", getTeamColor(teamId, carType));

        Span posSpan = new Span(String.valueOf(pos));
        posSpan.addClassName("results-list-pos");

        Div colorBar = new Div();
        colorBar.addClassName("results-list-color-bar");

        Span nameSpan = new Span(stat.getDriverName());
        nameSpan.addClassName("results-list-name");

        Span teamSpan = new Span(stat.getTeamName() != null ? stat.getTeamName() : "");
        teamSpan.addClassName("results-list-team");

        Div tyreStintContainer = new Div();
        tyreStintContainer.addClassName("results-list-tyre-stint");

        Span tyreBadge = new Span();
        tyreBadge.addClassName("tyre-badge");
        tyreBadge.setText(stat.getTyreCompound().substring(0, 1));
        switch (stat.getTyreCompound()) {
            case "Soft" -> tyreBadge.addClassName("tyre-soft");
            case "Medium" -> tyreBadge.addClassName("tyre-medium");
            case "Hard" -> tyreBadge.addClassName("tyre-hard");
            case "Inter" -> tyreBadge.addClassName("tyre-inter");
            case "Wet" -> tyreBadge.addClassName("tyre-wet");
            default -> tyreBadge.addClassName("tyre-unknown");
        }
        tyreBadge.getStyle().set("width", "16px").set("height", "16px").set("font-size", "9px").set("line-height", "16px").set("margin-right", "8px");

        Span lapsSpan = new Span(stat.getLaps() + " laps");
        lapsSpan.getStyle().set("font-size", "12px").set("font-weight", "800");

        tyreStintContainer.add(tyreBadge, lapsSpan);

        Span timeSpan = new Span(EventResultsView.formatLapTime((float) stat.getAvgLapTime()));
        timeSpan.addClassName("results-list-time");

        row.add(posSpan, colorBar, nameSpan, teamSpan, tyreStintContainer, timeSpan);
        return row;
    }

    public static String getTeamColor(Integer teamId, String carType) {
        if (teamId == null) return "#888888";
        if ("F1 26".equals(carType)) {
            return switch (teamId) {
                case 220, 476 -> "#00a19b";
                case 221, 477 -> "#ef1a2d";
                case 222, 478 -> "#0600ef";
                case 223, 479 -> "#005aff";
                case 224, 480 -> "#00594f";
                case 225, 481 -> "#0090ff";
                case 226, 482 -> "#1e41ff";
                case 227, 483 -> "#e60000";
                case 228, 484 -> "#ff8700";
                case 229, 485 -> "#c4002d";
                case 230, 486 -> "#fdb913";
                default -> "#888888";
            };
        } else {
            return switch (teamId) {
                case 0 -> "#00a19b";
                case 1 -> "#ef1a2d";
                case 2 -> "#0600ef";
                case 3 -> "#005aff";
                case 4 -> "#00594f";
                case 5 -> "#0090ff";
                case 6 -> "#1e41ff";
                case 7 -> "#e60000";
                case 8 -> "#ff8700";
                case 9 -> "#52e252";
                default -> "#888888";
            };
        }
    }

    public static String getTeamSymbol(Integer teamId, String carType) {
        if (teamId == null) return "T";
        if ("F1 26".equals(carType)) {
            return switch (teamId) {
                case 220, 476 -> "✦";
                case 221, 477 -> "🐎";
                case 222, 478 -> "🐂";
                case 223, 479 -> "W";
                case 224, 480 -> "▲";
                case 225, 481 -> "A";
                case 226, 482 -> "RB";
                case 227, 483 -> "H";
                case 228, 484 -> "M";
                case 229, 485 -> "四";
                case 230, 486 -> "★";
                default -> "T";
            };
        } else {
            return switch (teamId) {
                case 0 -> "✦";
                case 1 -> "🐎";
                case 2 -> "🐂";
                case 3 -> "W";
                case 4 -> "▲";
                case 5 -> "A";
                case 6 -> "RB";
                case 7 -> "H";
                case 8 -> "M";
                case 9 -> "K";
                default -> "T";
            };
        }
    }

    private static DriverResult findDriverResultByName(Event event, String driverName) {
        if (event == null || driverName == null) return null;
        for (SessionResult sr : event.getSessionResults()) {
            for (DriverResult dr : sr.getDriverResults()) {
                if (driverName.equals(dr.getDriverName())) {
                    return dr;
                }
            }
        }
        return null;
    }

    private static List<SessionResult> getOrderedSessions(Event event) {
        if (event == null) return Collections.emptyList();
        List<SessionResult> sessions = new ArrayList<>(event.getSessionResults());
        Map<Integer, Integer> sortOrder = Map.ofEntries(
                Map.entry(1, 1), Map.entry(2, 2), Map.entry(3, 3), Map.entry(4, 4),
                Map.entry(5, 5), Map.entry(6, 6), Map.entry(7, 7), Map.entry(8, 8), Map.entry(9, 9),
                Map.entry(10, 10), Map.entry(11, 11), Map.entry(12, 12), Map.entry(13, 13), Map.entry(14, 14),
                Map.entry(15, 15), Map.entry(16, 16), Map.entry(17, 17),
                Map.entry(18, 18), Map.entry(19, 19)
        );
        sessions.sort(Comparator.comparingInt(s -> sortOrder.getOrDefault(s.getSessionType(), 99)));
        return sessions;
    }

    private static class StintSegment {
        final int laps;
        final int endLap;
        final String compound;

        StintSegment(int laps, int endLap, String compound) {
            this.laps = laps;
            this.endLap = endLap;
            this.compound = compound;
        }
    }
}
