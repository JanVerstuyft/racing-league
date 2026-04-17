package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

@PageTitle("Seasons | F1 Telemetry")
@Route(value = "", layout = MainLayout.class)
public class SeasonListView extends VerticalLayout {

    private final LeagueRepository leagueRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final Grid<League> grid = new Grid<>(League.class, false);
    private final TextField nameField = new TextField("Season Name");

    public SeasonListView(LeagueRepository leagueRepository, TelemetryProcessingService telemetryProcessingService) {
        this.leagueRepository = leagueRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        
        setSizeFull();
        configureGrid();

        HorizontalLayout toolbar = createToolbar();

        add(toolbar, grid);
        updateList();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(League::getName).setHeader("Season Name");
        grid.addComponentColumn(league -> new RouterLink("Details", SeasonDetailsView.class, league.getId())).setHeader("Actions");
        grid.addComponentColumn(league -> {
            Button activateBtn = new Button("Activate", e -> {
                telemetryProcessingService.setActiveLeague(league.getId());
                Notification.show("Season " + league.getName() + " activated for telemetry!");
            });
            return activateBtn;
        });
    }

    private HorizontalLayout createToolbar() {
        Button addBtn = new Button("Add Season", e -> {
            if (!nameField.getValue().isEmpty()) {
                League league = new League();
                league.setName(nameField.getValue());
                leagueRepository.save(league);
                nameField.clear();
                updateList();
            }
        });

        HorizontalLayout toolbar = new HorizontalLayout(nameField, addBtn);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.BASELINE);
        return toolbar;
    }

    private void updateList() {
        grid.setItems(leagueRepository.findAll());
    }
}
