package be.jabapage.racingleague.f1telemetry.ui.tabs;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Collectors;

public class DriversTab extends VerticalLayout {

    private final DriverMappingRepository driverMappingRepository;
    private final TierRepository tierRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;

    private League league;
    private Tier tier;
    private final Grid<DriverMapping> mappingGrid = new Grid<>(DriverMapping.class, false);
    private final Button addManualDriverBtn = new Button("Add Manual Driver");
    private final Button deleteSelectedMappingsBtn = new Button("Delete Selected");

    public DriversTab(DriverMappingRepository driverMappingRepository,
                        TierRepository tierRepository,
                        TelemetryProcessingService telemetryProcessingService,
                        SecurityService securityService) {
        this.driverMappingRepository = driverMappingRepository;
        this.tierRepository = tierRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;

        setSizeFull();
        configureGrid();

        deleteSelectedMappingsBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteSelectedMappingsBtn.setEnabled(false);
        mappingGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        mappingGrid.addSelectionListener(e -> deleteSelectedMappingsBtn.setEnabled(!e.getAllSelectedItems().isEmpty()));

        deleteSelectedMappingsBtn.addClickListener(e -> deleteSelectedMappings());

        add(new HorizontalLayout(new H3("Driver Name Overrides"), addManualDriverBtn, deleteSelectedMappingsBtn),
                new Span("Drivers are managed per tier. Use the editor to override display names, mark as reserve, or move drivers between tiers (Promote/Demote)."),
                mappingGrid);

        addManualDriverBtn.addClickListener(e -> showAddManualDriverDialog());
    }

    private void configureGrid() {
        mappingGrid.addColumn(DriverMapping::getTelemetryName).setHeader("Telemetry Name");
        mappingGrid.addColumn(DriverMapping::getRaceNumber).setHeader("Race #");

        Grid.Column<DriverMapping> tierColumn = mappingGrid.addColumn(m -> m.getTier() != null ? m.getTier().getName() : "").setHeader("Tier");

        Grid.Column<DriverMapping> reserveColumn = mappingGrid.addComponentColumn(item -> {
            Checkbox cb = new Checkbox(item.isReserve());
            cb.setReadOnly(true);
            return cb;
        }).setHeader("Reserve");

        Grid.Column<DriverMapping> overrideColumn = mappingGrid.addColumn(DriverMapping::getOverriddenName).setHeader("Display Name");

        Binder<DriverMapping> binder = new Binder<>(DriverMapping.class);
        Editor<DriverMapping> editor = mappingGrid.getEditor();
        editor.setBinder(binder);
        editor.setBuffered(true);

        com.vaadin.flow.component.combobox.ComboBox<Tier> tierField = new com.vaadin.flow.component.combobox.ComboBox<>();
        tierField.setItemLabelGenerator(Tier::getName);
        tierField.setWidthFull();
        binder.forField(tierField).bind(DriverMapping::getTier, DriverMapping::setTier);
        tierColumn.setEditorComponent(tierField);

        TextField overrideField = new TextField();
        overrideField.setWidthFull();
        binder.forField(overrideField).bind(DriverMapping::getOverriddenName, DriverMapping::setOverriddenName);
        overrideColumn.setEditorComponent(overrideField);

        Checkbox reserveField = new Checkbox();
        binder.forField(reserveField).bind(DriverMapping::getReserve, DriverMapping::setReserve);
        reserveColumn.setEditorComponent(reserveField);

        Button saveButton = new Button("Save", e -> {
            try {
                DriverMapping item = editor.getItem();
                Tier oldTier = tier;
                editor.save();
                driverMappingRepository.save(item);
                
                // Refresh mappings for both tiers if changed
                if (item.getTier() != null) {
                    telemetryProcessingService.refreshDriverMappings(item.getTier().getId());
                }
                if (oldTier != null && !oldTier.equals(item.getTier())) {
                    telemetryProcessingService.refreshDriverMappings(oldTier.getId());
                }
                
                telemetryProcessingService.recalculateStandings(league.getId());
                Notification.show("Driver updated and standings recalculated!", 3000, Notification.Position.TOP_CENTER);
                updateData();
            } finally {
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        Button cancelButton = new Button("Cancel", e -> editor.cancel());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        mappingGrid.addComponentColumn(item -> {
            HorizontalLayout actions = new HorizontalLayout();

            Button editButton = new Button("Edit");
            editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editButton.addClickListener(e -> {
                if (editor.isOpen()) editor.cancel();
                if (league != null) {
                    tierField.setItems(tierRepository.findByLeagueId(league.getId()));
                }
                mappingGrid.getEditor().editItem(item);
            });

            Button deleteBtn = new Button("Delete", e -> {
                ConfirmDialog dialog = new ConfirmDialog();
                dialog.setHeader("Delete Driver Mapping?");
                dialog.setText("Are you sure you want to delete the mapping for '" + item.getTelemetryName() + "'?");
                dialog.setCancelable(true);
                dialog.setConfirmText("Delete");
                dialog.setConfirmButtonTheme("error primary");
                dialog.addConfirmListener(ev -> {
                    Notification deletingNote = new Notification("Deleting...");
                    deletingNote.setPosition(Notification.Position.TOP_CENTER);
                    deletingNote.setDuration(0);
                    deletingNote.open();
                    try {
                        driverMappingRepository.delete(item);
                        updateData();
                        deletingNote.close();
                        Notification.show("Driver mapping deleted", 3000, Notification.Position.TOP_CENTER);
                    } catch (Exception ex) {
                        deletingNote.close();
                        Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                    }
                });
                dialog.open();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            actions.add(editButton, deleteBtn);
            actions.setVisible(securityService.getAuthenticatedUser().isPresent());
            return actions;
        }).setEditorComponent(new HorizontalLayout(saveButton, cancelButton));
    }

    private void deleteSelectedMappings() {
        var selected = mappingGrid.getSelectedItems();
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete " + selected.size() + " Mappings?");
        dialog.setText("Are you sure you want to delete the selected driver mappings?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(ev -> {
            Notification deletingNote = new Notification("Deleting mappings...");
            deletingNote.setPosition(Notification.Position.TOP_CENTER);
            deletingNote.setDuration(0);
            deletingNote.open();
            try {
                driverMappingRepository.deleteAll(selected);
                updateData();
                deletingNote.close();
                Notification.show(selected.size() + " mappings deleted", 3000, Notification.Position.TOP_CENTER);
            } catch (Exception ex) {
                deletingNote.close();
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
            }
        });
        dialog.open();
    }

    private void showAddManualDriverDialog() {
        if (tier == null) return;
        com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();
        dialog.setHeaderTitle("Add Manual Driver to " + tier.getName());

        TextField nameField = new TextField("Display Name");
        nameField.setWidthFull();

        TextField telemetryNameField = new TextField("Telemetry Name (Optional)");
        telemetryNameField.setWidthFull();

        com.vaadin.flow.component.textfield.IntegerField raceNumField = new com.vaadin.flow.component.textfield.IntegerField("Race #");
        raceNumField.setWidthFull();

        VerticalLayout dialogLayout = new VerticalLayout(nameField, telemetryNameField, raceNumField);
        dialog.add(dialogLayout);

        Button saveBtn = new Button("Add", ev -> {
            if (nameField.getValue().isEmpty()) {
                Notification.show("Please enter a name", 3000, Notification.Position.TOP_CENTER);
                return;
            }
            DriverMapping mapping = new DriverMapping();
            mapping.setTier(tier);
            mapping.setOverriddenName(nameField.getValue());
            mapping.setTelemetryName(telemetryNameField.getValue().isEmpty() ? nameField.getValue() : telemetryNameField.getValue());
            mapping.setRaceNumber(raceNumField.getValue() != null ? raceNumField.getValue() : 0);
            mapping.setDriverId(255); // Use 255 for manual drivers
            driverMappingRepository.save(mapping);
            updateData();
            dialog.close();
            Notification.show("Manual driver added", 3000, Notification.Position.TOP_CENTER);
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
        dialog.open();
    }

    public void setLeague(League league) {
        this.league = league;
        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        addManualDriverBtn.setVisible(loggedIn);
        deleteSelectedMappingsBtn.setVisible(loggedIn);
        updateData();
    }

    public void setTier(Tier tier) {
        this.tier = tier;
        updateData();
    }

    public void updateData() {
        if (tier == null) {
            mappingGrid.setItems(Collections.emptyList());
            return;
        }
        mappingGrid.setItems(driverMappingRepository.findByTier(tier).stream()
                .sorted(Comparator.comparing(m -> m.getOverriddenName() != null ? m.getOverriddenName() : m.getTelemetryName()))
                .collect(Collectors.toList()));
    }
}
