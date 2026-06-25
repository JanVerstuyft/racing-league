package be.jabapage.racingleague.f1telemetry.ui.season;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
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
    private final SecurityService securityService;
    private final Runnable onDataChanged;
    private final Runnable onTiersListRefreshed;

    private League league;

    private final Grid<Tier> tierGrid = new Grid<>(Tier.class, false);
    private final H3 tiersTitle = new H3("Manage Tiers");
    private final HorizontalLayout tiersToolbar = new HorizontalLayout();
    private final TextField addTierNameField = new TextField("Tier Name");
    private final Button addTierBtn = new Button("Add Tier");

    private Grid.Column<Tier> tokenColumn;
    private Grid.Column<Tier> tierActionsColumn;

    private boolean isOwner() {
        return league != null && securityService.getAuthenticatedUserEntity()
                .map(user -> league.getUser() != null && league.getUser().getId().equals(user.getId()))
                .orElse(false);
    }

    public TiersTab(TierRepository tierRepository,
                    SecurityService securityService,
                    Runnable onDataChanged,
                    Runnable onTiersListRefreshed) {
        this.tierRepository = tierRepository;
        this.securityService = securityService;
        this.onDataChanged = onDataChanged;
        this.onTiersListRefreshed = onTiersListRefreshed;

        setSizeFull();

        addTierBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addTierBtn.addClickListener(ev -> {
            if (!isOwner()) return;
            if (league == null || addTierNameField.getValue().isEmpty()) return;
            Tier t = new Tier();
            t.setName(addTierNameField.getValue());
            t.setToken(UUID.randomUUID().toString());
            t.setLeague(league);
            tierRepository.save(t);
            addTierNameField.clear();
            onTiersListRefreshed.run();
            onDataChanged.run();
            Notification.show("Tier added", 3000, Notification.Position.TOP_CENTER);
        });

        tiersToolbar.add(addTierNameField, addTierBtn);
        tiersToolbar.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

        add(tiersTitle, tiersToolbar, tierGrid);

        configureGrid();
    }

    private void configureGrid() {
        tierGrid.addColumn(Tier::getName).setHeader("Tier Name").setAutoWidth(true);
        tokenColumn = tierGrid.addComponentColumn(t -> {
            Span tokenSpan = new Span(t.getToken());
            tokenSpan.getStyle().set("font-family", "monospace");
            tokenSpan.getStyle().set("font-size", "0.8em");
            Button copyBtn = new Button("Copy", e -> {
                if (!isOwner()) return;
                getElement().executeJs("navigator.clipboard.writeText($0)", t.getToken());
                Notification.show("Token copied to clipboard", 3000, Notification.Position.TOP_CENTER);
            });
            copyBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            
            HorizontalLayout l = new HorizontalLayout(tokenSpan, copyBtn);
            l.setAlignItems(Alignment.CENTER);
            return l;
        }).setHeader("Telemetry Token").setAutoWidth(true);
        
        tierGrid.addComponentColumn(t -> new RouterLink("Live Dashboard", LeaderboardView.class, t.getId())).setHeader("Live");
        
        tierActionsColumn = tierGrid.addComponentColumn(t -> {
            HorizontalLayout actions = new HorizontalLayout();
            
            TextField renameField = new TextField();
            renameField.setValue(t.getName());
            renameField.setVisible(false);
            
            Button renameBtn = new Button("Rename");
            renameBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            
            Button saveBtn = new Button("Save");
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            saveBtn.setVisible(false);
            
            renameBtn.addClickListener(e -> {
                if (!isOwner()) return;
                renameField.setVisible(true);
                saveBtn.setVisible(true);
                renameBtn.setVisible(false);
            });
            
            saveBtn.addClickListener(e -> {
                if (!isOwner()) return;
                if (!renameField.getValue().isEmpty()) {
                    t.setName(renameField.getValue());
                    tierRepository.save(t);
                    onTiersListRefreshed.run();
                    onDataChanged.run();
                    renameField.setVisible(false);
                    saveBtn.setVisible(false);
                    renameBtn.setVisible(true);
                    Notification.show("Tier renamed!", 3000, Notification.Position.TOP_CENTER);
                }
            });
            
            Button deleteBtn = new Button("Delete", e -> {
                if (!isOwner()) return;
                ConfirmDialog cd = new ConfirmDialog();
                cd.setHeader("Delete Tier?");
                cd.setText("Are you sure you want to delete '" + t.getName() + "'? All race weekends and standings in this tier will be lost.");
                cd.setCancelable(true);
                cd.setConfirmText("Delete");
                cd.setConfirmButtonTheme("error primary");
                cd.addConfirmListener(event -> {
                    if (!isOwner()) return;
                    tierRepository.delete(t);
                    onTiersListRefreshed.run();
                    onDataChanged.run();
                    Notification.show("Tier deleted", 3000, Notification.Position.TOP_CENTER);
                });
                cd.open();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            
            actions.add(renameField, renameBtn, saveBtn, deleteBtn);
            actions.setAlignItems(Alignment.CENTER);
            actions.setVisible(isOwner());
            return actions;
        }).setHeader("Actions");
    }

    public void update(League league) {
        this.league = league;

        if (league == null) return;

        boolean isOwner = isOwner();
        tiersToolbar.setVisible(isOwner);
        if (tokenColumn != null) {
            tokenColumn.setVisible(isOwner);
        }
        if (tierActionsColumn != null) {
            tierActionsColumn.setVisible(isOwner);
        }
        tiersTitle.setText(isOwner ? "Manage Tiers" : "Tiers");

        tierGrid.setItems(tierRepository.findByLeagueOrderByNameAsc(league));
    }
}
