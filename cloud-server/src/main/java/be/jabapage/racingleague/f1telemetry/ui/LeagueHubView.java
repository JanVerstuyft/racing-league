package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.LeagueLogo;
import be.jabapage.racingleague.f1telemetry.repository.LeagueLogoRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.io.ByteArrayInputStream;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@AnonymousAllowed
@PageTitle("Racing League Hub | F1 Telemetry")
@Route(value = "")
public class LeagueHubView extends VerticalLayout {

    private final LeagueRepository leagueRepository;
    private final LeagueLogoRepository leagueLogoRepository;
    private final SecurityService securityService;

    private final Grid<League> grid = new Grid<>(League.class, false);
    private final TextField searchField = new TextField();
    private List<League> allLeagues;

    public LeagueHubView(LeagueRepository leagueRepository,
                         LeagueLogoRepository leagueLogoRepository,
                         SecurityService securityService) {
        this.leagueRepository = leagueRepository;
        this.leagueLogoRepository = leagueLogoRepository;
        this.securityService = securityService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setMaxWidth("1200px");
        getElement().getStyle().set("margin", "0 auto");

        createHeader();
        createHero();
        createSearchAndGrid();

        refreshLeagues();
    }

    private void createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
        header.getStyle().set("padding-bottom", "var(--lumo-space-m)");

        H1 title = new H1("F1 Telemetry Leagues");
        title.getStyle().set("font-size", "var(--lumo-font-size-xl)");
        title.getStyle().set("margin", "0");
        header.add(title);
        header.expand(title);

        HorizontalLayout nav = new HorizontalLayout();
        nav.setSpacing(true);
        nav.setAlignItems(Alignment.CENTER);

        nav.add(new RouterLink("Documentation", DocumentationView.class));
        nav.add(new RouterLink("Privacy Policy", PrivacyView.class));

        if (securityService.getAuthenticatedUser().isPresent()) {
            nav.add(new RouterLink("My Seasons", SeasonListView.class));
            Button logoutBtn = new Button("Log out", e -> securityService.logout());
            logoutBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            nav.add(logoutBtn);
        } else {
            nav.add(new RouterLink("Login", LoginView.class));
            nav.add(new RouterLink("Register", RegistrationView.class));
        }

        header.add(nav);
        add(header);
    }

    private void createHero() {
        VerticalLayout hero = new VerticalLayout();
        hero.setWidthFull();
        hero.setAlignItems(Alignment.CENTER);
        hero.getStyle().set("background", "linear-gradient(135deg, var(--lumo-primary-color-10pct) 0%, var(--lumo-primary-color-5pct) 100%)");
        hero.getStyle().set("padding", "var(--lumo-space-xl) var(--lumo-space-l)");
        hero.getStyle().set("border-radius", "var(--lumo-border-radius-l)");
        hero.getStyle().set("margin-top", "var(--lumo-space-m)");
        hero.getStyle().set("margin-bottom", "var(--lumo-space-m)");

        H2 subtitle = new H2("Welcome to the F1 Telemetry League Hub");
        subtitle.getStyle().set("margin", "0");
        subtitle.getStyle().set("text-align", "center");
        subtitle.getStyle().set("font-size", "var(--lumo-font-size-xxl)");

        Paragraph desc = new Paragraph("Browse active racing leagues, view championship standings, and analyze detailed telemetry statistics.");
        desc.getStyle().set("color", "var(--lumo-secondary-text-color)");
        desc.getStyle().set("text-align", "center");
        desc.getStyle().set("max-width", "600px");

        hero.add(subtitle, desc);
        add(hero);
    }

    private void createSearchAndGrid() {
        searchField.setPlaceholder("Search leagues by name...");
        searchField.setWidth("300px");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> filterLeagues());
        
        HorizontalLayout searchLayout = new HorizontalLayout(searchField);
        searchLayout.setWidthFull();
        searchLayout.setJustifyContentMode(JustifyContentMode.START);
        add(searchLayout);

        grid.setSizeFull();
        grid.setMinHeight("400px");

        // Column for Logo
        grid.addComponentColumn(league -> {
            if (Boolean.TRUE.equals(league.getHasLogo())) {
                StreamResource resource = new StreamResource("logo-" + league.getId() + "-" + System.currentTimeMillis() + ".png",
                        () -> {
                            byte[] logoBytes = leagueLogoRepository.findById(league.getId())
                                     .map(LeagueLogo::getLogo)
                                     .orElse(new byte[0]);
                             return new ByteArrayInputStream(logoBytes);
                        });
                Image img = new Image(resource, "Logo");
                img.setHeight("40px");
                return img;
            }
            return new Span("-");
        }).setHeader("Logo").setWidth("90px").setFlexGrow(0);

        // Column for League/Season Name
        grid.addComponentColumn(league -> {
            RouterLink link = new RouterLink(league.getName(), SeasonDetailsView.class, league.getId());
            link.getStyle().set("font-weight", "600");
            return link;
        }).setHeader("League Name").setAutoWidth(true).setSortable(true).setComparator(Comparator.comparing(League::getName));

        // Column for Tiers count
        grid.addColumn(league -> league.getTiers() != null ? league.getTiers().size() : 0)
                .setHeader("Tiers").setAutoWidth(true);

        // Column for Host
        grid.addColumn(league -> league.getUser() != null ? league.getUser().getUsername() : "Unknown")
                .setHeader("Host").setAutoWidth(true);

        // Column for Action
        grid.addComponentColumn(league -> {
            RouterLink viewLink = new RouterLink("View Standings", SeasonDetailsView.class, league.getId());
            viewLink.getElement().getThemeList().add("badge success primary");
            viewLink.getStyle().set("text-decoration", "none");
            viewLink.getStyle().set("cursor", "pointer");
            return viewLink;
        }).setHeader("Action").setAutoWidth(true).setFlexGrow(0);

        add(grid);
        setFlexGrow(1, grid);
    }

    private void refreshLeagues() {
        allLeagues = leagueRepository.findAllWithTiersAndUser().stream()
                .sorted(Comparator.comparing(League::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        filterLeagues();
    }

    private void filterLeagues() {
        String filter = searchField.getValue();
        if (filter == null || filter.trim().isEmpty()) {
            grid.setItems(allLeagues);
        } else {
            String lowerFilter = filter.toLowerCase().trim();
            grid.setItems(allLeagues.stream()
                    .filter(l -> l.getName() != null && l.getName().toLowerCase().contains(lowerFilter))
                    .collect(Collectors.toList()));
        }
    }
}
