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

import java.util.UUID;

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
            Span tokenSpan = new Span(league.getToken());
            tokenSpan.getStyle().set("font-family", "monospace");
            tokenSpan.getStyle().set("font-size", "0.8em");
            
            Button copyBtn = new Button("Copy", e -> {
                getElement().executeJs("navigator.clipboard.writeText($0)", league.getToken());
                Notification.show("Token copied to clipboard");
            });
            copyBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            
            HorizontalLayout layout = new HorizontalLayout(tokenSpan, copyBtn);
            layout.setAlignItems(Alignment.CENTER);
            return layout;
        }).setHeader("Telemetry Token").setAutoWidth(true);
        
        grid.addComponentColumn(league -> {
            // Since we don't have a single active league anymore for the whole server,
            // we might want to track this per user session.
            // For now, let's just keep the Details link.
            RouterLink detailsLink = new RouterLink("Details", SeasonDetailsView.class, league.getId());
            return detailsLink;
        }).setHeader("Details");

        grid.addComponentColumn(league -> {
            RouterLink liveLink = new RouterLink("Live Dashboard", LeaderboardView.class, league.getId());
            return liveLink;
        }).setHeader("Live");

        grid.addComponentColumn(league -> {
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
                    league.setToken(UUID.randomUUID().toString());
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
