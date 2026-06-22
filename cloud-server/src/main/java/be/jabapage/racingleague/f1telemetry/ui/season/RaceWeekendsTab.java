package be.jabapage.racingleague.f1telemetry.ui.season;

import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.ui.EventPenaltiesView;
import be.jabapage.racingleague.f1telemetry.ui.EventResultsView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.RouterLink;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RaceWeekendsTab extends VerticalLayout {

    private final EventRepository eventRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;
    private final Runnable onDataChanged;

    private Tier selectedTier;
    private List<Event> currentEvents = Collections.emptyList();

    private final Grid<Event> eventGrid = new Grid<>(Event.class, false);
    private final Button addManualWeekendBtn = new Button("Add Manual Weekend");
    private Grid.Column<Event> actionsColumn;

    public RaceWeekendsTab(EventRepository eventRepository,
                            TelemetryProcessingService telemetryProcessingService,
                            SecurityService securityService,
                            Runnable onDataChanged) {
        this.eventRepository = eventRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;
        this.onDataChanged = onDataChanged;

        setSizeFull();
        configureGrid();

        addManualWeekendBtn.addClickListener(e -> {
            if (selectedTier == null) return;

            com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();
            dialog.setHeaderTitle("Add Manual Weekend");

            com.vaadin.flow.component.combobox.ComboBox<Integer> trackCombo = new com.vaadin.flow.component.combobox.ComboBox<>("Track");
            trackCombo.setItems(java.util.stream.IntStream.rangeClosed(0, 42).boxed().toList());
            trackCombo.setItemLabelGenerator(id -> TelemetryProcessingService.TRACK_NAMES.getOrDefault(id, "Track " + id));
            trackCombo.setWidthFull();

            TextField nameField = new TextField("Event Name (e.g. Belgian Grand Prix)");
            nameField.setWidthFull();

            DatePicker plannedDatePicker = new DatePicker("Planned Date");
            plannedDatePicker.setWidthFull();

            trackCombo.addValueChangeListener(ev -> {
                if (ev.getValue() != null && (nameField.getValue() == null || nameField.getValue().isEmpty())) {
                    nameField.setValue(TelemetryProcessingService.TRACK_NAMES.getOrDefault(ev.getValue(), "") + " Grand Prix");
                }
            });

            VerticalLayout dialogLayout = new VerticalLayout(trackCombo, nameField, plannedDatePicker);
            dialog.add(dialogLayout);

            Button saveBtn = new Button("Add", ev -> {
                if (trackCombo.getValue() == null || nameField.getValue().isEmpty()) {
                    Notification.show("Please fill in all fields", 3000, Notification.Position.TOP_CENTER);
                    return;
                }
                Event newEvent = new Event();
                newEvent.setTier(selectedTier);
                newEvent.setTrackId(String.valueOf(trackCombo.getValue()));
                newEvent.setEventName(nameField.getValue());
                newEvent.setPlannedDate(plannedDatePicker.getValue());
                eventRepository.save(newEvent);
                onDataChanged.run();
                dialog.close();
                Notification.show("Manual weekend added", 3000, Notification.Position.TOP_CENTER);
            });
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

            Button cancelBtn = new Button("Cancel", ev -> dialog.close());
            dialog.getFooter().add(cancelBtn, saveBtn);
            dialog.open();
        });

        add(new HorizontalLayout(new H3("Race Weekends"), addManualWeekendBtn), eventGrid);
    }

    private void configureGrid() {
        eventGrid.addComponentColumn(event -> new RouterLink(event.getEventName(), EventResultsView.class, event.getId()))
                .setHeader("Event")
                .setSortable(true)
                .setComparator(Comparator.comparing(Event::getEventName));
        eventGrid.addColumn(event -> event.getPlannedDate() != null ? event.getPlannedDate().toString() : "TBD")
                .setHeader("Planned Date")
                .setSortable(true)
                .setComparator(Comparator.comparing(event -> event.getPlannedDate() != null ? event.getPlannedDate() : LocalDate.MIN));
        eventGrid.addColumn(event -> {
            java.util.Set<Integer> types = event.getSessionResults().stream()
                    .map(SessionResult::getSessionType)
                    .collect(Collectors.toSet());
            Map<Integer, Integer> sortOrder = Map.ofEntries(
                    Map.entry(1, 1), Map.entry(2, 2), Map.entry(3, 3), Map.entry(4, 4),
                    Map.entry(5, 5), Map.entry(6, 6), Map.entry(7, 7), Map.entry(8, 8), Map.entry(9, 9),
                    Map.entry(10, 10), Map.entry(11, 11), Map.entry(12, 12), Map.entry(13, 13), Map.entry(14, 14),
                    Map.entry(15, 15), Map.entry(16, 16), Map.entry(17, 17),
                    Map.entry(18, 18), Map.entry(19, 19)
            );
            return event.getSessionResults().stream()
                    .map(SessionResult::getSessionType)
                    .distinct()
                    .sorted(Comparator.comparingInt(type -> sortOrder.getOrDefault(type, 99)))
                    .map(type -> EventResultsView.getDynamicSessionName(type, types))
                    .collect(Collectors.joining(", "));
        }).setHeader("Sessions");

        eventGrid.addComponentColumn(event -> {
            Span statusBadge = new Span();
            if (Boolean.TRUE.equals(event.getFinalized())) {
                statusBadge.setText("Final");
                statusBadge.getElement().getThemeList().add("badge success");
            } else {
                statusBadge.setText("Provisional");
                statusBadge.getElement().getThemeList().add("badge error");
            }
            return statusBadge;
        }).setHeader("Status").setAutoWidth(true).setFlexGrow(0);

        actionsColumn = eventGrid.addComponentColumn(event -> {
            HorizontalLayout actions = new HorizontalLayout();
            RouterLink resultsLink = new RouterLink("Results", EventResultsView.class, event.getId());
            actions.add(resultsLink);

            if (securityService.getAuthenticatedUser().isPresent()) {
                RouterLink penaltiesLink = new RouterLink("Penalties", EventPenaltiesView.class, event.getId());
                actions.add(penaltiesLink);

                int index = currentEvents.indexOf(event);

                Button moveUpBtn = new Button();
                moveUpBtn.setIcon(com.vaadin.flow.component.icon.VaadinIcon.ARROW_UP.create());
                moveUpBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                moveUpBtn.setEnabled(index > 0);
                moveUpBtn.setTooltipText("Move Up");
                moveUpBtn.addClickListener(e -> moveEventUp(event));

                Button moveDownBtn = new Button();
                moveDownBtn.setIcon(com.vaadin.flow.component.icon.VaadinIcon.ARROW_DOWN.create());
                moveDownBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                moveDownBtn.setEnabled(index >= 0 && index < currentEvents.size() - 1);
                moveDownBtn.setTooltipText("Move Down");
                moveDownBtn.addClickListener(e -> moveEventDown(event));

                actions.add(moveUpBtn, moveDownBtn);

                Button toggleFinalizedBtn = new Button();
                toggleFinalizedBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
                if (Boolean.TRUE.equals(event.getFinalized())) {
                    toggleFinalizedBtn.setText("Reopen");
                    toggleFinalizedBtn.setIcon(com.vaadin.flow.component.icon.VaadinIcon.UNLOCK.create());
                    toggleFinalizedBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                } else {
                    toggleFinalizedBtn.setText("Mark Final");
                    toggleFinalizedBtn.setIcon(com.vaadin.flow.component.icon.VaadinIcon.LOCK.create());
                    toggleFinalizedBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
                }

                toggleFinalizedBtn.addClickListener(e -> {
                    boolean newStatus = !Boolean.TRUE.equals(event.getFinalized());
                    String statusWord = newStatus ? "final" : "provisional";
                    ConfirmDialog dialog = new ConfirmDialog();
                    dialog.setHeader("Mark Event as " + statusWord.substring(0, 1).toUpperCase() + statusWord.substring(1) + "?");
                    dialog.setText("Are you sure you want to mark '" + event.getEventName() + "' as " + statusWord + "? Standings will be automatically recalculated.");
                    dialog.setCancelable(true);
                    dialog.setConfirmText("Yes");
                    dialog.addConfirmListener(ev -> {
                        event.setFinalized(newStatus);
                        eventRepository.save(event);
                        telemetryProcessingService.recalculateStandings(event.getTier().getId());
                        onDataChanged.run();
                        Notification.show("Event marked as " + statusWord + " and standings recalculated", 3000, Notification.Position.TOP_CENTER);
                    });
                    dialog.open();
                });
                actions.add(toggleFinalizedBtn);

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
                            telemetryProcessingService.deleteEvent(event.getId());
                            onDataChanged.run();
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

                Button editBtn = new Button("Edit", e -> showEditEventDialog(event));
                editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
                actions.add(editBtn, deleteBtn);
            }

            actions.setAlignItems(FlexComponent.Alignment.CENTER);
            return actions;
        }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);
    }

    private void showEditEventDialog(Event event) {
        com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();
        dialog.setHeaderTitle("Edit Weekend");

        com.vaadin.flow.component.combobox.ComboBox<Integer> trackCombo = new com.vaadin.flow.component.combobox.ComboBox<>("Track");
        trackCombo.setItems(java.util.stream.IntStream.rangeClosed(0, 42).boxed().toList());
        trackCombo.setItemLabelGenerator(id -> TelemetryProcessingService.TRACK_NAMES.getOrDefault(id, "Track " + id));
        trackCombo.setWidthFull();
        if (event.getTrackId() != null) {
            try {
                trackCombo.setValue(Integer.parseInt(event.getTrackId()));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        TextField nameField = new TextField("Event Name");
        nameField.setWidthFull();
        nameField.setValue(event.getEventName() != null ? event.getEventName() : "");

        DatePicker plannedDatePicker = new DatePicker("Planned Date");
        plannedDatePicker.setWidthFull();
        plannedDatePicker.setValue(event.getPlannedDate());

        trackCombo.addValueChangeListener(ev -> {
            if (ev.getValue() != null && (nameField.getValue() == null || nameField.getValue().isEmpty())) {
                nameField.setValue(TelemetryProcessingService.TRACK_NAMES.getOrDefault(ev.getValue(), "") + " Grand Prix");
            }
        });

        VerticalLayout dialogLayout = new VerticalLayout(trackCombo, nameField, plannedDatePicker);
        dialog.add(dialogLayout);

        Button saveBtn = new Button("Save", ev -> {
            if (trackCombo.getValue() == null || nameField.getValue().isEmpty()) {
                Notification.show("Please fill in all fields", 3000, Notification.Position.TOP_CENTER);
                return;
            }
            event.setTrackId(String.valueOf(trackCombo.getValue()));
            event.setEventName(nameField.getValue());
            event.setPlannedDate(plannedDatePicker.getValue());
            eventRepository.save(event);
            onDataChanged.run();
            dialog.close();
            Notification.show("Weekend updated", 3000, Notification.Position.TOP_CENTER);
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancel", ev -> dialog.close());
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void moveEventUp(Event event) {
        if (selectedTier == null) return;
        List<Event> events = eventRepository.findByTier(selectedTier);
        int index = events.indexOf(event);
        if (index > 0) {
            for (int i = 0; i < events.size(); i++) {
                events.get(i).setDisplayOrder(i);
            }
            events.get(index).setDisplayOrder(index - 1);
            events.get(index - 1).setDisplayOrder(index);
            eventRepository.saveAll(events);
            onDataChanged.run();
            Notification.show("Event order updated", 2000, Notification.Position.TOP_CENTER);
        }
    }

    private void moveEventDown(Event event) {
        if (selectedTier == null) return;
        List<Event> events = eventRepository.findByTier(selectedTier);
        int index = events.indexOf(event);
        if (index >= 0 && index < events.size() - 1) {
            for (int i = 0; i < events.size(); i++) {
                events.get(i).setDisplayOrder(i);
            }
            events.get(index).setDisplayOrder(index + 1);
            events.get(index + 1).setDisplayOrder(index);
            eventRepository.saveAll(events);
            onDataChanged.run();
            Notification.show("Event order updated", 2000, Notification.Position.TOP_CENTER);
        }
    }

    public void update(Tier selectedTier, List<Event> currentEvents) {
        this.selectedTier = selectedTier;
        this.currentEvents = currentEvents;
        eventGrid.setItems(currentEvents);

        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        addManualWeekendBtn.setVisible(loggedIn);
        if (actionsColumn != null) {
            actionsColumn.setVisible(loggedIn);
        }
    }
}
