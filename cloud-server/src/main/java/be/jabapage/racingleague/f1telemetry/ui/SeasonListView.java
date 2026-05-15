package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
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
import jakarta.annotation.security.PermitAll;

@PermitAll
@PageTitle("Seasons | F1 Telemetry")
@Route(value = "", layout = MainLayout.class)
public class SeasonListView extends VerticalLayout {

    private final LeagueRepository leagueRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;
    private final Grid<League> grid = new Grid<>(League.class, false);
    private final TextField nameField = new TextField("Season Name");

    public SeasonListView(LeagueRepository leagueRepository, 
                          TelemetryProcessingService telemetryProcessingService,
                          SecurityService securityService) {
        this.leagueRepository = leagueRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;
        
        setSizeFull();
        configureGrid();

        HorizontalLayout toolbar = createToolbar();

        add(toolbar, grid);
        updateList();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(League::getName).setHeader("Season Name").setAutoWidth(true);
        
        grid.addComponentColumn(league -> {
            // we might want to track this per user session.
            // For now, let's just keep the Details link.
            RouterLink detailsLink = new RouterLink("Details", SeasonDetailsView.class, league.getId());
            return detailsLink;
        }).setHeader("Details");

        grid.addComponentColumn(league -> {
            Button deleteBtn = new Button("Delete", e -> {
                ConfirmDialog dialog = new ConfirmDialog();
                dialog.setHeader("Delete Season?");
                dialog.setText("Are you sure you want to delete '" + league.getName() + "'? All results and standings will be lost.");
                dialog.setCancelable(true);
                dialog.setConfirmText("Delete");
                dialog.setConfirmButtonTheme("error primary");
                dialog.addConfirmListener(event -> {
                    Notification deletingNote = new Notification("Deleting season...");
                    deletingNote.setPosition(Notification.Position.TOP_CENTER);
                    deletingNote.setDuration(0);
                    deletingNote.open();
                    try {
                        leagueRepository.delete(league);
                        updateList();
                        deletingNote.close();
                        Notification.show("Season deleted", 3000, Notification.Position.TOP_CENTER);
                    } catch (Exception ex) {
                        deletingNote.close();
                        Notification.show("Error deleting season: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                    }
                });
                dialog.open();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            return deleteBtn;
        }).setHeader("Actions");
    }

    private HorizontalLayout createToolbar() {
        Button addBtn = new Button("Add Season", e -> {
            if (!nameField.getValue().isEmpty()) {
                securityService.getAuthenticatedUserEntity().ifPresent(user -> {
                    League league = new League();
                    league.setName(nameField.getValue());
                    league.setUser(user);
                    league.setHideAi(true);
                    leagueRepository.save(league);
                    nameField.clear();
                    updateList();
                });
            }
        });

        HorizontalLayout toolbar = new HorizontalLayout(nameField, addBtn);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.BASELINE);
        return toolbar;
    }

    private void updateList() {
        securityService.getAuthenticatedUserEntity().ifPresent(user -> {
            grid.setItems(leagueRepository.findByUser(user));
        });
    }
}
