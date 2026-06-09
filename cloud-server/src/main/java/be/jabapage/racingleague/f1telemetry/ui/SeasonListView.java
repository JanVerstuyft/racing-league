package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.entity.LeagueLogo;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueLogoRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;

import java.util.UUID;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.StreamResource;
import java.io.ByteArrayInputStream;

@PermitAll
@PageTitle("Seasons | F1 Telemetry")
@Route(value = "", layout = MainLayout.class)
public class SeasonListView extends VerticalLayout {

    private final LeagueRepository leagueRepository;
    private final TierRepository tierRepository;
    private final LeagueLogoRepository leagueLogoRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;
    private final Grid<League> grid = new Grid<>(League.class, false);
    private final TextField nameField = new TextField("Season Name");

    public SeasonListView(LeagueRepository leagueRepository, 
                          TierRepository tierRepository,
                          LeagueLogoRepository leagueLogoRepository,
                          TelemetryProcessingService telemetryProcessingService,
                          SecurityService securityService) {
        this.leagueRepository = leagueRepository;
        this.tierRepository = tierRepository;
        this.leagueLogoRepository = leagueLogoRepository;
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
        
        grid.addComponentColumn(league -> {
            if (league.getHasLogo()) {
                StreamResource resource = new StreamResource("logo-" + league.getId() + "-" + System.currentTimeMillis() + ".png",
                        () -> {
                            byte[] logoBytes = leagueLogoRepository.findById(league.getId())
                                    .map(LeagueLogo::getLogo)
                                    .orElse(new byte[0]);
                            return new ByteArrayInputStream(logoBytes);
                        });
                Image img = new Image(resource, "logo");
                img.setHeight("40px");
                return img;
            }
            return new Span("-");
        }).setHeader("Logo").setWidth("80px").setFlexGrow(0);

        grid.addColumn(League::getName).setHeader("Season Name").setAutoWidth(true);
        
        grid.addComponentColumn(league -> {
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
                    League savedLeague = leagueRepository.save(league);

                    Tier tier = new Tier();
                    tier.setName("Tier 1");
                    tier.setToken(UUID.randomUUID().toString());
                    tier.setLeague(savedLeague);
                    tierRepository.save(tier);

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

    @Override
    protected void onAttach(com.vaadin.flow.component.AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        attachEvent.getUI().getPage().executeJs(
            "document.documentElement.style.removeProperty('--lumo-base-color'); document.body.style.backgroundColor = '';"
        );
    }
}
