package be.jabapage.racingleague.f1telemetry.ui.tabs;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.ui.LeaderboardView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.RouterLink;

import java.util.UUID;

public class TiersTab extends VerticalLayout {

    private final TierRepository tierRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;
    private final Runnable onTiersChanged;

    private League league;
    private final Grid<Tier> grid = new Grid<>(Tier.class, false);
    private final TextField nameField = new TextField("Tier Name");
    private final Button addBtn = new Button("Add Tier");
    private final HorizontalLayout addLayout = new HorizontalLayout(nameField, addBtn);

    public TiersTab(TierRepository tierRepository, TelemetryProcessingService telemetryProcessingService, SecurityService securityService, Runnable onTiersChanged) {
        this.tierRepository = tierRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;
        this.onTiersChanged = onTiersChanged;

        setSizeFull();
        configureGrid();

        addBtn.addClickListener(e -> {
            if (league != null && !nameField.getValue().isEmpty()) {
                Tier tier = new Tier();
                tier.setLeague(league);
                tier.setName(nameField.getValue());
                tier.setToken(UUID.randomUUID().toString());
                tierRepository.save(tier);
                nameField.clear();
                onTiersChanged.run();
                Notification.show("Tier added", 3000, Notification.Position.TOP_CENTER);
            }
        });
        addLayout.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

        add(new H3("Tiers"), addLayout, grid);
    }

    private void configureGrid() {
        grid.addColumn(Tier::getName).setHeader("Tier Name");

        grid.addComponentColumn(tier -> {
            Span tokenSpan = new Span(tier.getToken());
            tokenSpan.getStyle().set("font-family", "monospace");
            tokenSpan.getStyle().set("font-size", "0.8em");

            Button copyBtn = new Button("Copy", e -> {
                getElement().executeJs("navigator.clipboard.writeText($0)", tier.getToken());
                Notification.show("Token copied to clipboard", 3000, Notification.Position.TOP_CENTER);
            });
            copyBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            HorizontalLayout layout = new HorizontalLayout(tokenSpan, copyBtn);
            layout.setAlignItems(Alignment.CENTER);
            return layout;
        }).setHeader("Telemetry Token").setAutoWidth(true);

        grid.addComponentColumn(tier -> {
            RouterLink liveLink = new RouterLink("Live Dashboard", LeaderboardView.class, tier.getId());
            return liveLink;
        }).setHeader("Live");

        grid.addComponentColumn(tier -> {
            Button deleteBtn = new Button("Delete", e -> {
                ConfirmDialog dialog = new ConfirmDialog();
                dialog.setHeader("Delete Tier?");
                dialog.setText("Are you sure you want to delete '" + tier.getName() + "'? All results and standings for this tier will be lost.");
                dialog.setCancelable(true);
                dialog.setConfirmText("Delete");
                dialog.setConfirmButtonTheme("error primary");
                dialog.addConfirmListener(event -> {
                    tierRepository.delete(tier);
                    onTiersChanged.run();
                    Notification.show("Tier deleted", 3000, Notification.Position.TOP_CENTER);
                });
                dialog.open();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            return deleteBtn;
        }).setHeader("Actions").setKey("Actions");
    }

    public void setLeague(League league) {
        this.league = league;
        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        addLayout.setVisible(loggedIn);
        grid.getColumnByKey("Actions").setVisible(loggedIn);
        updateData();
    }

    public void updateData() {
        if (league != null) {
            grid.setItems(tierRepository.findByLeagueId(league.getId()));
        }
    }
}
