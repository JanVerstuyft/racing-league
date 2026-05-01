package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.model.DriverBoardState;
import be.jabapage.racingleague.f1telemetry.model.SessionInfo;
import be.jabapage.racingleague.f1telemetry.service.Broadcaster;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
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
    private final Grid<DriverBoardState> grid = new Grid<>(DriverBoardState.class, false);
    private final H2 title = new H2("LIVE LEADERBOARD");
    private final Span scStatus = new Span();
    private final RouterLink backLink = new RouterLink("← Back to Season", SeasonDetailsView.class, 0L);
    private Registration leaderboardRegistration;
    private Registration sessionInfoRegistration;
    private Long leagueId;

    public LeaderboardView(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
        setSizeFull();

        configureGrid();

        HorizontalLayout header = new HorizontalLayout(title, scStatus);
        header.setAlignItems(Alignment.BASELINE);
        header.setSpacing(true);

        add(new HorizontalLayout(
                backLink,
                new RouterLink("Documentation", DocumentationView.class)
        ));
        add(header, grid);
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        this.leagueId = parameter;
        backLink.setRoute(SeasonDetailsView.class, leagueId);
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
        Grid.Column<DriverBoardState> gapLdrCol = grid.addColumn(DriverBoardState::getGapToLeader).setHeader("Gap Leader");
        Grid.Column<DriverBoardState> intervalCol = grid.addColumn(DriverBoardState::getGapToFront).setHeader("Interval");

        // Quali columns
        Grid.Column<DriverBoardState> bestLapCol = grid.addColumn(DriverBoardState::getBestLapTime).setHeader("Best Lap");
        Grid.Column<DriverBoardState> gapBestCol = grid.addColumn(DriverBoardState::getGapToLeaderBest).setHeader("Gap");
        Grid.Column<DriverBoardState> s1Col = grid.addColumn(DriverBoardState::getS1Time).setHeader("S1");
        Grid.Column<DriverBoardState> s2Col = grid.addColumn(DriverBoardState::getS2Time).setHeader("S2");
        Grid.Column<DriverBoardState> s3Col = grid.addColumn(DriverBoardState::getS3Time).setHeader("S3");

        s1Col.setPartNameGenerator(state -> state.isBestS1() ? "best-sector" : null);
        s2Col.setPartNameGenerator(state -> state.isBestS2() ? "best-sector" : null);
        s3Col.setPartNameGenerator(state -> state.isBestS3() ? "best-sector" : null);

        grid.setPartNameGenerator(state -> {
            int status = state.getResultStatus();
            if (status >= 4) return "status-retired";
            return null;
        });

        // Store columns for easy toggling
        this.raceColumns = List.of(tyreCol, ageCol, pitsCol, penCol, gapLdrCol, intervalCol);
        this.qualiColumns = List.of(bestLapCol, gapBestCol, s1Col, s2Col, s3Col);
        
        grid.getStyle().set("font-family", "monospace");
    }

    private List<Grid.Column<DriverBoardState>> raceColumns;
    private List<Grid.Column<DriverBoardState>> qualiColumns;

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        if (leagueId == null) {
            title.setText("NO LEAGUE SELECTED");
            return;
        }
        UI ui = attachEvent.getUI();
        leaderboardRegistration = broadcaster.registerLeaderboard(leagueId, data -> {
            if (ui.isAttached()) {
                ui.access(() -> updateLeaderboard(data));
            }
        });
        sessionInfoRegistration = broadcaster.registerSessionInfo(leagueId, info -> {
            if (ui.isAttached()) {
                ui.access(() -> updateSessionInfo(info));
            }
        });
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
    }

    private void updateLeaderboard(List<DriverBoardState> data) {
        if (!data.isEmpty()) {
            boolean isQuali = data.get(0).isQualifying();
            raceColumns.forEach(c -> c.setVisible(!isQuali));
            qualiColumns.forEach(c -> c.setVisible(isQuali));
        }
        grid.setItems(data);
    }

    private void updateSessionInfo(SessionInfo info) {
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
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
