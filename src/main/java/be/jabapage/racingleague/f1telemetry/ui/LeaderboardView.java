package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.model.DriverBoardState;
import be.jabapage.racingleague.f1telemetry.service.Broadcaster;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

@PageTitle("Live Leaderboard | F1 Telemetry")
@Route(value = "leaderboard", layout = MainLayout.class)
public class LeaderboardView extends VerticalLayout {

    private final Broadcaster broadcaster;
    private final Grid<DriverBoardState> grid = new Grid<>(DriverBoardState.class, false);

    public LeaderboardView(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
        setSizeFull();

        configureGrid();
        add(new H2("LIVE LEADERBOARD"), grid);
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(DriverBoardState::getPosition).setHeader("Pos").setWidth("60px").setFlexGrow(0);
        grid.addColumn(DriverBoardState::getName).setHeader("Driver");
        grid.addColumn(DriverBoardState::getTeam).setHeader("Team");
        grid.addColumn(DriverBoardState::getTyreCompound).setHeader("Tyre");
        grid.addColumn(DriverBoardState::getTyreAge).setHeader("Age");
        grid.addColumn(DriverBoardState::getPitStops).setHeader("Pits");
        grid.addColumn(DriverBoardState::getGapToLeader).setHeader("Gap Leader");
        grid.addColumn(DriverBoardState::getGapToFront).setHeader("Interval");
        
        grid.getStyle().set("font-family", "monospace");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        broadcaster.registerLeaderboard(data -> ui.access(() -> updateLeaderboard(data)));
    }

    private void updateLeaderboard(List<DriverBoardState> data) {
        grid.setItems(data);
    }
}
