package be.jabapage.racingleague.f1telemetry.ui;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.League;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.server.StreamResource;
import java.io.ByteArrayInputStream;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.ManualPenalty;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.repository.ManualPenaltyRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
import java.util.Collections;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@AnonymousAllowed
@PageTitle("Manage Event Penalties | F1 Telemetry")
@Route(value = "event-penalties")
public class EventPenaltiesView extends VerticalLayout implements HasUrlParameter<Long> {

    private final EventRepository eventRepository;
    private final ManualPenaltyRepository manualPenaltyRepository;
    private final DriverMappingRepository driverMappingRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;

    private final HorizontalLayout logoContainer = new HorizontalLayout();
    private final H2 eventHeader = new H2();
    private final RouterLink backToSeason = new RouterLink("Back to Season", SeasonDetailsView.class, 0L);

    private final Grid<ManualPenalty> penaltyGrid = new Grid<>(ManualPenalty.class, false);

    // Form fields
    private final ComboBox<SessionResult> sessionCombo = new ComboBox<>("Session/Race");
    private final ComboBox<DriverMapping> driverCombo = new ComboBox<>("Driver");
    private final IntegerField secondsField = new IntegerField("Time Penalty (seconds)");
    private final IntegerField pointsField = new IntegerField("Points Deduction (PD)");
    private final TextArea commentField = new TextArea("Comment (Reason)");
    private final Button addPenaltyBtn = new Button("Add Penalty");

    private Long currentEventId;
    private Event currentEvent;

    public EventPenaltiesView(EventRepository eventRepository,
                              ManualPenaltyRepository manualPenaltyRepository,
                              DriverMappingRepository driverMappingRepository,
                              TelemetryProcessingService telemetryProcessingService,
                              SecurityService securityService) {
        this.eventRepository = eventRepository;
        this.manualPenaltyRepository = manualPenaltyRepository;
        this.driverMappingRepository = driverMappingRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;

        setSizeFull();

        HorizontalLayout nav = new HorizontalLayout(backToSeason);
        if (!securityService.getAuthenticatedUser().isPresent()) {
            nav.add(new RouterLink("Login", LoginView.class));
        }
        nav.add(new RouterLink("Documentation", DocumentationView.class));
        nav.setSpacing(true);

        logoContainer.setAlignItems(Alignment.CENTER);
        HorizontalLayout titleLayout = new HorizontalLayout(logoContainer, eventHeader);
        titleLayout.setAlignItems(Alignment.CENTER);
        titleLayout.setSpacing(true);

        add(nav, titleLayout);

        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();

        // Configure Form
        if (loggedIn) {
            H3 formTitle = new H3("Add New Penalty");
            FormLayout formLayout = new FormLayout();
            formLayout.add(sessionCombo, driverCombo, secondsField, pointsField, commentField);
            formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
            
            driverCombo.setItemLabelGenerator(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty() 
                    ? m.getOverriddenName() 
                    : m.getTelemetryName());
            driverCombo.setRequired(true);
            
            secondsField.setHelperText("Can be positive or negative (to reduce/remove existing penalties)");
            pointsField.setHelperText("Subtracted from the final race points");

            addPenaltyBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            addPenaltyBtn.addClickListener(e -> addPenalty());

            VerticalLayout formContainer = new VerticalLayout(formTitle, formLayout, addPenaltyBtn);
            formContainer.setPadding(false);
            formContainer.setSpacing(true);
            formContainer.setWidth("450px");

            HorizontalLayout splitLayout = new HorizontalLayout();
            splitLayout.setSizeFull();
            
            configureGrid(true);
            splitLayout.add(penaltyGrid, formContainer);
            splitLayout.setFlexGrow(1, penaltyGrid);
            splitLayout.setFlexGrow(0, formContainer);
            add(splitLayout);
        } else {
            configureGrid(false);
            add(penaltyGrid);
        }
    }

    private void configureGrid(boolean allowDelete) {
        penaltyGrid.setSizeFull();
        penaltyGrid.addComponentColumn(p -> {
            DriverMapping dm = p.getDriverMapping();
            String nameText = dm.getOverriddenName() != null && !dm.getOverriddenName().isEmpty() 
                    ? dm.getOverriddenName() 
                    : dm.getTelemetryName();

            Span flagSpan = new Span(CountryProvider.getFlagByName(dm.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            Span name = new Span(nameText);
            HorizontalLayout row = new HorizontalLayout(flagSpan, name);
            row.setAlignItems(Alignment.CENTER);
            row.setSpacing(false);
            return row;
        }).setHeader("Driver").setAutoWidth(true);

        penaltyGrid.addColumn(p -> {
            if (p.getSessionResult() == null || currentEvent == null) return "-";
            java.util.Set<Integer> sessionTypes = currentEvent.getSessionResults().stream()
                    .map(SessionResult::getSessionType)
                    .collect(Collectors.toSet());
            return EventResultsView.getDynamicSessionName(p.getSessionResult().getSessionType(), sessionTypes);
        }).setHeader("Session").setAutoWidth(true);

        penaltyGrid.addColumn(p -> p.getSeconds() != null ? p.getSeconds() + "s" : "-")
                .setHeader("Seconds").setAutoWidth(true);

        penaltyGrid.addColumn(p -> p.getPointDeduction() != null ? p.getPointDeduction() + " PD" : "-")
                .setHeader("Point Deduction").setAutoWidth(true);

        penaltyGrid.addColumn(ManualPenalty::getComment)
                .setHeader("Comment").setFlexGrow(1);

        if (allowDelete) {
            penaltyGrid.addComponentColumn(p -> {
                Button deleteBtn = new Button("Delete", e -> {
                    ConfirmDialog dialog = new ConfirmDialog();
                    dialog.setHeader("Delete Penalty?");
                    dialog.setText("Are you sure you want to delete this penalty?");
                    dialog.setCancelable(true);
                    dialog.setConfirmText("Delete");
                    dialog.setConfirmButtonTheme("error primary");
                    dialog.addConfirmListener(ev -> {
                        manualPenaltyRepository.delete(p);
                        telemetryProcessingService.recalculateStandings(currentEvent.getTier().getId());
                        refreshPenalties();
                        Notification.show("Penalty deleted and standings recalculated", 3000, Notification.Position.TOP_CENTER);
                    });
                    dialog.open();
                });
                deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
                return deleteBtn;
            }).setHeader("Actions").setAutoWidth(true);
        }
    }

    private void addPenalty() {
        if (sessionCombo.getValue() == null) {
            Notification.show("Please select a session/race", 3000, Notification.Position.TOP_CENTER);
            return;
        }

        if (driverCombo.getValue() == null) {
            Notification.show("Please select a driver", 3000, Notification.Position.TOP_CENTER);
            return;
        }

        if (secondsField.getValue() == null && pointsField.getValue() == null) {
            Notification.show("Please enter seconds or a points deduction", 3000, Notification.Position.TOP_CENTER);
            return;
        }

        ManualPenalty penalty = new ManualPenalty();
        penalty.setSessionResult(sessionCombo.getValue());
        penalty.setDriverMapping(driverCombo.getValue());
        penalty.setSeconds(secondsField.getValue());
        penalty.setPointDeduction(pointsField.getValue());
        penalty.setComment(commentField.getValue());

        manualPenaltyRepository.save(penalty);
        
        // Recalculate tier standings
        telemetryProcessingService.recalculateStandings(currentEvent.getTier().getId());

        // Reset form
        sessionCombo.clear();
        driverCombo.clear();
        secondsField.clear();
        pointsField.clear();
        commentField.clear();

        refreshPenalties();
        Notification.show("Penalty added and standings recalculated", 3000, Notification.Position.TOP_CENTER);
    }

    @Override
    public void setParameter(BeforeEvent event, Long parameter) {
        this.currentEventId = parameter;
        eventRepository.findById(parameter).ifPresentOrElse(e -> {
            this.currentEvent = e;
            eventHeader.setText("Manage Penalties: " + currentEvent.getEventName());
            backToSeason.setRoute(SeasonDetailsView.class, currentEvent.getTier().getLeague().getId());
            updateLogo();
            
            // Populate session ComboBox (filter to race sessions only)
            List<SessionResult> raceSessions = currentEvent.getSessionResults().stream()
                    .filter(s -> (s.getSessionType() >= 15 && s.getSessionType() <= 17) || s.getSessionType() == 19)
                    .collect(Collectors.toList());
            sessionCombo.setItems(raceSessions);
            java.util.Set<Integer> sessionTypes = currentEvent.getSessionResults().stream()
                    .map(SessionResult::getSessionType)
                    .collect(Collectors.toSet());
            sessionCombo.setItemLabelGenerator(s -> EventResultsView.getDynamicSessionName(s.getSessionType(), sessionTypes));
            sessionCombo.setRequired(true);
            
            // Populate driver ComboBox
            driverCombo.setItems(driverMappingRepository.findByLeague(currentEvent.getTier().getLeague()).stream()
                    .sorted(Comparator.comparing(m -> m.getOverriddenName() != null ? m.getOverriddenName() : m.getTelemetryName()))
                    .collect(Collectors.toList()));
            
            refreshPenalties();
        }, () -> {
            event.forwardTo(SeasonListView.class);
        });
    }

    private void refreshPenalties() {
        if (currentEvent != null) {
            if (currentEvent.getSessionResults().isEmpty()) {
                penaltyGrid.setItems(Collections.emptyList());
            } else {
                List<ManualPenalty> penalties = manualPenaltyRepository.findBySessionResultIn(currentEvent.getSessionResults());
                penaltyGrid.setItems(penalties);
            }
        }
    }

    private void updateLogo() {
        logoContainer.removeAll();
        if (currentEvent != null && currentEvent.getTier() != null && currentEvent.getTier().getLeague() != null) {
            League league = currentEvent.getTier().getLeague();
            if (league.getLogo() != null) {
                StreamResource resource = new StreamResource("logo-" + league.getId() + "-" + System.currentTimeMillis() + ".png",
                        () -> new ByteArrayInputStream(league.getLogo()));
                Image logoImg = new Image(resource, "logo");
                logoImg.setHeight("40px");
                logoContainer.add(logoImg);
            }
        }
    }
}
