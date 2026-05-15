package be.jabapage.racingleague.f1telemetry.ui.tabs;

import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.ui.EventResultsView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.RouterLink;

import java.util.stream.Collectors;

public class RaceWeekendsTab extends VerticalLayout {

    private final EventRepository eventRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;
    
    private League league;
    private final Grid<Event> eventGrid = new Grid<>(Event.class, false);
    private final Button addManualWeekendBtn = new Button("Add Manual Weekend");

    public RaceWeekendsTab(EventRepository eventRepository,
                            TelemetryProcessingService telemetryProcessingService,
                            SecurityService securityService) {
        this.eventRepository = eventRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;

        setSizeFull();
        configureGrid();

        add(new HorizontalLayout(new H3("Race Weekends"), addManualWeekendBtn), eventGrid);

        addManualWeekendBtn.addClickListener(e -> showAddManualWeekendDialog());
    }

    private void configureGrid() {
        eventGrid.addColumn(Event::getEventName).setHeader("Event");
        eventGrid.addColumn(event -> {
            return event.getSessionResults().stream()
                    .map(SessionResult::getSessionType)
                    .map(type -> TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(type, "S" + type))
                    .distinct()
                    .collect(Collectors.joining(", "));
        }).setHeader("Sessions");

        eventGrid.addComponentColumn(event -> {
            HorizontalLayout actions = new HorizontalLayout();
            RouterLink resultsLink = new RouterLink("Results", EventResultsView.class, event.getId());
            actions.add(resultsLink);

            if (securityService.getAuthenticatedUser().isPresent()) {
                Button deleteBtn = new Button("Delete", e -> {
                    ConfirmDialog dialog = new ConfirmDialog();
                    dialog.setHeader("Delete Weekend?");
                    dialog.setText("Are you sure you want to delete the weekend '" + event.getEventName() + "'? Standings will be automatically recalculated.");
                    dialog.setCancelable(true);
                    dialog.setConfirmText("Delete");
                    dialog.setConfirmButtonTheme("error primary");
                    dialog.addConfirmListener(ev -> {
                        Notification deletingNote = new Notification("Deleting weekend...");
                        deletingNote.setPosition(Notification.Position.TOP_CENTER);
                        deletingNote.setDuration(0);
                        deletingNote.open();
                        try {
                            eventRepository.delete(event);
                            telemetryProcessingService.recalculateStandings(league.getId());
                            updateData();
                            deletingNote.close();
                            Notification.show("Weekend deleted and standings recalculated", 3000, Notification.Position.TOP_CENTER);
                        } catch (Exception ex) {
                            deletingNote.close();
                            Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                        }
                    });
                    dialog.open();
                });
                deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
                actions.add(deleteBtn);
            }

            actions.setAlignItems(FlexComponent.Alignment.CENTER);
            return actions;
        }).setHeader("Actions");
    }

    private void showAddManualWeekendDialog() {
        if (league == null) return;
        
        com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();
        dialog.setHeaderTitle("Add Manual Weekend");
        
        com.vaadin.flow.component.combobox.ComboBox<Integer> trackCombo = new com.vaadin.flow.component.combobox.ComboBox<>("Track");
        trackCombo.setItems(java.util.stream.IntStream.rangeClosed(0, 41).boxed().toList());
        trackCombo.setItemLabelGenerator(id -> TelemetryProcessingService.TRACK_NAMES.getOrDefault(id, "Track " + id));
        trackCombo.setWidthFull();

        TextField nameField = new TextField("Event Name (e.g. Belgian Grand Prix)");
        nameField.setWidthFull();

        trackCombo.addValueChangeListener(ev -> {
            if (ev.getValue() != null && (nameField.getValue() == null || nameField.getValue().isEmpty())) {
                nameField.setValue(TelemetryProcessingService.TRACK_NAMES.getOrDefault(ev.getValue(), "") + " Grand Prix");
            }
        });

        VerticalLayout dialogLayout = new VerticalLayout(trackCombo, nameField);
        dialog.add(dialogLayout);

        Button saveBtn = new Button("Add", ev -> {
            if (trackCombo.getValue() == null || nameField.getValue().isEmpty()) {
                Notification.show("Please fill in all fields", 3000, Notification.Position.TOP_CENTER);
                return;
            }
            Event newEvent = new Event();
            newEvent.setLeague(league);
            newEvent.setTrackId(String.valueOf(trackCombo.getValue()));
            newEvent.setEventName(nameField.getValue());
            eventRepository.save(newEvent);
            updateData();
            dialog.close();
            Notification.show("Manual weekend added", 3000, Notification.Position.TOP_CENTER);
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        Button cancelBtn = new Button("Cancel", ev -> dialog.close());
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    public void setLeague(League league) {
        this.league = league;
        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        addManualWeekendBtn.setVisible(loggedIn);
        updateData();
    }

    public void updateData() {
        if (league == null) return;
        eventGrid.setItems(eventRepository.findByLeague(league));
    }
}
