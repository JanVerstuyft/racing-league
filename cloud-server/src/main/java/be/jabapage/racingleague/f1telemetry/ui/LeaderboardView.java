package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.model.DriverBoardState;
import be.jabapage.racingleague.f1telemetry.service.Broadcaster;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

@PageTitle("Live Leaderboard | F1 Telemetry")
@Route(value = "leaderboard", layout = MainLayout.class)
public class LeaderboardView extends VerticalLayout {

    private final Broadcaster broadcaster;
    private final Grid<DriverBoardState> grid = new Grid<>(DriverBoardState.class, false);
    private final H2 title = new H2("LIVE LEADERBOARD");

    public LeaderboardView(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
        setSizeFull();

        configureGrid();
        add(title, grid);
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(DriverBoardState::getPosition).setHeader("Pos").setWidth("60px").setFlexGrow(0);
        grid.addColumn(DriverBoardState::getName).setHeader("Driver");
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
            // This is a workaround to hide/show columns dynamically based on the first item
            // Better approach is to rebuild columns, but for live updates this is smoother if we use CSS to hide
            return null;
        });

        // Store columns for easy toggling
        this.raceColumns = List.of(tyreCol, ageCol, pitsCol, gapLdrCol, intervalCol);
        this.qualiColumns = List.of(bestLapCol, gapBestCol, s1Col, s2Col, s3Col);
        
        grid.getStyle().set("font-family", "monospace");
    }

    private List<Grid.Column<DriverBoardState>> raceColumns;
    private List<Grid.Column<DriverBoardState>> qualiColumns;

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        broadcaster.registerLeaderboard(data -> ui.access(() -> updateLeaderboard(data)));
        broadcaster.registerSessionType(type -> ui.access(() -> updateTitle(type)));
    }

    private void updateLeaderboard(List<DriverBoardState> data) {
        if (!data.isEmpty()) {
            boolean isQuali = data.get(0).isQualifying();
            raceColumns.forEach(c -> c.setVisible(!isQuali));
            qualiColumns.forEach(c -> c.setVisible(isQuali));
        }
        grid.setItems(data);
    }

    private void updateTitle(String sessionType) {
        title.setText("LIVE LEADERBOARD - " + sessionType.toUpperCase());
    }
}
