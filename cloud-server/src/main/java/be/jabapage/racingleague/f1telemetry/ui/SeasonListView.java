package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
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
        
        grid.addComponentColumn(league -> {
            boolean isActive = league.getId().equals(telemetryProcessingService.getActiveLeagueId());
            if (isActive) {
                Span activeBadge = new Span("ACTIVE");
                activeBadge.getElement().getThemeList().add("badge success");
                return activeBadge;
            } else {
                Button activateBtn = new Button("Activate", e -> {
                    telemetryProcessingService.setActiveLeague(league.getId());
                    grid.getDataProvider().refreshAll();
                    Notification.show("Season " + league.getName() + " activated for telemetry!");
                });
                activateBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
                return activateBtn;
            }
        }).setHeader("Status");

        grid.addComponentColumn(league -> {
            HorizontalLayout actions = new HorizontalLayout();
            
            RouterLink detailsLink = new RouterLink("Details", SeasonDetailsView.class, league.getId());
            
            Button deleteBtn = new Button("Delete", e -> {
                ConfirmDialog dialog = new ConfirmDialog();
                dialog.setHeader("Delete Season?");
                dialog.setText("Are you sure you want to delete '" + league.getName() + "'? All results and standings will be lost.");
                dialog.setCancelable(true);
                dialog.setConfirmText("Delete");
                dialog.setConfirmButtonTheme("error primary");
                dialog.addConfirmListener(event -> {
                    leagueRepository.delete(league);
                    updateList();
                    Notification.show("Season deleted");
                });
                dialog.open();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            
            actions.add(detailsLink, deleteBtn);
            actions.setAlignItems(Alignment.CENTER);
            return actions;
        }).setHeader("Actions");
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
