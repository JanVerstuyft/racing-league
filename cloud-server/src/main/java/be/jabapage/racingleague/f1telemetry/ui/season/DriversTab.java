package be.jabapage.racingleague.f1telemetry.ui.season;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.TeamMapping;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class DriversTab extends VerticalLayout {

    private final DriverMappingRepository driverMappingRepository;
    private final TeamMappingRepository teamMappingRepository;
    private final TierRepository tierRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;
    private final Runnable onDataChanged;

    private League league;
    private Tier selectedTier;

    private boolean isOwner() {
        return league != null && securityService.getAuthenticatedUserEntity()
                .map(user -> league.getUser() != null && league.getUser().getId().equals(user.getId()))
                .orElse(false);
    }

    private final Grid<DriverMapping> mappingGrid = new Grid<>(DriverMapping.class, false);
    private final Button addManualDriverBtn = new Button("Add Manual Driver");
    private final Button deleteSelectedMappingsBtn = new Button("Delete Selected");

    private final ComboBox<Tier> tierEditorField = new ComboBox<>();
    private final ComboBox<TeamMapping> teamEditorField = new ComboBox<>();
    private final ComboBox<String> champTeamEditorField = new ComboBox<>();
    private Grid.Column<DriverMapping> champTeamColumn;

    public DriversTab(DriverMappingRepository driverMappingRepository,
                      TeamMappingRepository teamMappingRepository,
                      TierRepository tierRepository,
                      TelemetryProcessingService telemetryProcessingService,
                      SecurityService securityService,
                      Runnable onDataChanged) {
        this.driverMappingRepository = driverMappingRepository;
        this.teamMappingRepository = teamMappingRepository;
        this.tierRepository = tierRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;
        this.onDataChanged = onDataChanged;

        setSizeFull();

        deleteSelectedMappingsBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteSelectedMappingsBtn.setEnabled(false);
        mappingGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        mappingGrid.addSelectionListener(e -> deleteSelectedMappingsBtn.setEnabled(!e.getAllSelectedItems().isEmpty()));

        deleteSelectedMappingsBtn.addClickListener(e -> {
            if (!isOwner()) return;
            var selected = mappingGrid.getSelectedItems();
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Delete " + selected.size() + " Mappings?");
            dialog.setText("Are you sure you want to delete the selected driver mappings?");
            dialog.setCancelable(true);
            dialog.setConfirmText("Delete");
            dialog.setConfirmButtonTheme("error primary");
            dialog.addConfirmListener(ev -> {
                if (!isOwner()) return;
                Notification deletingNote = new Notification("Deleting mappings...");
                deletingNote.setPosition(Notification.Position.TOP_CENTER);
                deletingNote.setDuration(0);
                deletingNote.open();
                try {
                    driverMappingRepository.deleteAll(selected);
                    onDataChanged.run();
                    deletingNote.close();
                    Notification.show(selected.size() + " mappings deleted", 3000, Notification.Position.TOP_CENTER);
                } catch (Exception ex) {
                    deletingNote.close();
                    Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                }
            });
            dialog.open();
        });

        addManualDriverBtn.addClickListener(e -> {
            if (!isOwner()) return;
            if (league == null) return;
            com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();
            dialog.setHeaderTitle("Add Manual Driver");

            TextField nameField = new TextField("Display Name");
            nameField.setWidthFull();

            TextField telemetryNameField = new TextField("Telemetry Name (Optional)");
            telemetryNameField.setWidthFull();

            com.vaadin.flow.component.textfield.IntegerField raceNumField = new com.vaadin.flow.component.textfield.IntegerField("Race #");
            raceNumField.setWidthFull();

            ComboBox<String> countryCombo = new ComboBox<>("Country");
            countryCombo.setItems(CountryProvider.getCountryNames());
            countryCombo.setValue("Unknown");
            countryCombo.setWidthFull();

            ComboBox<Tier> manualTierField = new ComboBox<>("Assign to Tier");
            manualTierField.setItems(tierRepository.findByLeagueOrderByNameAsc(league));
            manualTierField.setItemLabelGenerator(Tier::getName);
            manualTierField.setWidthFull();
            manualTierField.setValue(selectedTier);

            Checkbox reserveField = new Checkbox("Reserve");

            ComboBox<TeamMapping> teamCombo = new ComboBox<>("Team");
            teamCombo.setItems(getTeamsForLeague());
            teamCombo.setItemLabelGenerator(TeamMapping::getTeamName);
            teamCombo.setWidthFull();

            ComboBox<String> champTeamCombo = new ComboBox<>("Championship Team");
            champTeamCombo.setItems("A", "B", "None");
            champTeamCombo.setItemLabelGenerator(val -> {
                if ("A".equals(val)) {
                    return league != null && league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
                } else if ("B".equals(val)) {
                    return league != null && league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
                }
                return "None";
            });
            champTeamCombo.setValue("None");
            champTeamCombo.setWidthFull();
            boolean teamsEnabled = Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
                && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
                && league.getTeamBName() != null && !league.getTeamBName().isEmpty();
            champTeamCombo.setVisible(teamsEnabled);

            reserveField.addValueChangeListener(ev -> {
                if (ev.getValue()) {
                    teamCombo.setValue(null);
                    teamCombo.setEnabled(false);
                    champTeamCombo.setValue("None");
                    champTeamCombo.setEnabled(false);
                } else {
                    teamCombo.setEnabled(true);
                    champTeamCombo.setEnabled(true);
                }
            });

            VerticalLayout dialogLayout = new VerticalLayout(nameField, telemetryNameField, raceNumField, countryCombo, manualTierField, reserveField, teamCombo, champTeamCombo);
            dialog.add(dialogLayout);

            Button saveBtn = new Button("Add", ev -> {
                if (nameField.getValue().isEmpty()) {
                    Notification.show("Please enter a name", 3000, Notification.Position.TOP_CENTER);
                    return;
                }
                DriverMapping mapping = new DriverMapping();
                mapping.setLeague(league);
                mapping.setOverriddenName(nameField.getValue());
                mapping.setTelemetryName(telemetryNameField.getValue().isEmpty() ? nameField.getValue() : telemetryNameField.getValue());
                mapping.setRaceNumber(raceNumField.getValue() != null ? raceNumField.getValue() : 0);
                mapping.setCountry(countryCombo.getValue() != null ? countryCombo.getValue() : "Unknown");
                mapping.setDriverId(255); // Use 255 for manual drivers
                mapping.setTier(manualTierField.getValue());
                mapping.setReserve(reserveField.getValue());
                mapping.setTeamId(teamCombo.getValue() != null ? teamCombo.getValue().getTeamId() : null);
                if (teamsEnabled && !"None".equals(champTeamCombo.getValue()) && !mapping.isReserve()) {
                    mapping.setChampionshipTeam(champTeamCombo.getValue());
                } else {
                    mapping.setChampionshipTeam(null);
                }

                checkTeamCapacityAndSave(mapping, () -> {
                    if (!isOwner()) return;
                    if (!validateChampionshipTeamConstraints(mapping)) {
                        onDataChanged.run();
                        return;
                    }
                    driverMappingRepository.save(mapping);
                    telemetryProcessingService.refreshDriverMappings(league.getId());
                    // Recalculate standings for all tiers
                    List<Tier> tiers = tierRepository.findByLeague(league);
                    for (Tier t : tiers) {
                        telemetryProcessingService.recalculateStandings(t.getId());
                    }
                    onDataChanged.run();
                    dialog.close();
                    Notification.show("Manual driver added", 3000, Notification.Position.TOP_CENTER);
                });
            });
            saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
            dialog.open();
        });

        add(new HorizontalLayout(new H3("Driver Name Overrides"), addManualDriverBtn, deleteSelectedMappingsBtn), 
                new Span("Drivers are automatically discovered when they join a session. Edit the 'Display Name' to override how they appear in the leaderboard and standings."), 
                mappingGrid);

        configureGrid();
    }

    private void configureGrid() {
        mappingGrid.addComponentColumn(m -> {
            Span flagSpan = new Span(CountryProvider.getFlagByName(m.getCountry()));
            flagSpan.getStyle().set("margin-right", "var(--lumo-space-s)");
            Span name = new Span(m.getTelemetryName());
            HorizontalLayout row = new HorizontalLayout(flagSpan, name);
            row.setAlignItems(Alignment.CENTER);
            row.setSpacing(false);
            return row;
        }).setHeader("Telemetry Name");
        mappingGrid.addColumn(DriverMapping::getRaceNumber).setHeader("Race #");
        mappingGrid.addColumn(DriverMapping::getDriverId).setHeader("Driver ID");
        
        Grid.Column<DriverMapping> reserveColumn = mappingGrid.addComponentColumn(item -> {
            Checkbox cb = new Checkbox(item.isReserve());
            cb.setReadOnly(true);
            return cb;
        }).setHeader("Reserve");

        Grid.Column<DriverMapping> teamColumn = mappingGrid.addColumn(m -> {
            if (m.getTeamId() == null) return "None";
            return teamMappingRepository.findByCarType(getCarTypeForLeague()).stream()
                    .filter(t -> Objects.equals(t.getTeamId(), m.getTeamId()))
                    .map(TeamMapping::getTeamName)
                    .findFirst()
                    .orElse("Team " + m.getTeamId());
        }).setHeader("Team");

        champTeamColumn = mappingGrid.addColumn(m -> {
            if ("A".equals(m.getChampionshipTeam())) {
                return league != null && league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
            } else if ("B".equals(m.getChampionshipTeam())) {
                return league != null && league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
            }
            return "None";
        }).setHeader("Championship Team");

        Grid.Column<DriverMapping> overrideColumn = mappingGrid.addColumn(DriverMapping::getOverriddenName).setHeader("Display Name");
        Grid.Column<DriverMapping> countryColumn = mappingGrid.addColumn(DriverMapping::getCountry).setHeader("Country");

        // Column for Tier assignment
        Grid.Column<DriverMapping> tiersColumn = mappingGrid.addColumn(m -> {
            return m.getTier() != null ? m.getTier().getName() : "";
        }).setHeader("Tier");

        Binder<DriverMapping> binder = new Binder<>(DriverMapping.class);
        Editor<DriverMapping> editor = mappingGrid.getEditor();
        editor.setBinder(binder);
        editor.setBuffered(true);

        champTeamEditorField.setItems("A", "B", "None");
        champTeamEditorField.setItemLabelGenerator(val -> {
            if ("A".equals(val)) {
                return league != null && league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
            } else if ("B".equals(val)) {
                return league != null && league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
            }
            return "None";
        });
        champTeamEditorField.setWidthFull();

        binder.forField(champTeamEditorField)
            .withConverter(new com.vaadin.flow.data.converter.Converter<String, String>() {
                @Override
                public com.vaadin.flow.data.binder.Result<String> convertToModel(String value, com.vaadin.flow.data.binder.ValueContext context) {
                    if ("None".equals(value)) return com.vaadin.flow.data.binder.Result.ok(null);
                    return com.vaadin.flow.data.binder.Result.ok(value);
                }

                @Override
                public String convertToPresentation(String value, com.vaadin.flow.data.binder.ValueContext context) {
                    return value == null ? "None" : value;
                }
            })
            .bind(DriverMapping::getChampionshipTeam, DriverMapping::setChampionshipTeam);
        champTeamColumn.setEditorComponent(champTeamEditorField);

        TextField overrideField = new TextField();
        overrideField.setWidthFull();
        binder.forField(overrideField).bind(DriverMapping::getOverriddenName, DriverMapping::setOverriddenName);
        overrideColumn.setEditorComponent(overrideField);

        ComboBox<String> countryField = new ComboBox<>();
        countryField.setItems(CountryProvider.getCountryNames());
        countryField.setWidthFull();
        binder.forField(countryField).bind(DriverMapping::getCountry, DriverMapping::setCountry);
        countryColumn.setEditorComponent(countryField);

        Checkbox reserveField = new Checkbox();
        binder.forField(reserveField).bind(DriverMapping::getReserve, DriverMapping::setReserve);
        reserveColumn.setEditorComponent(reserveField);

        teamEditorField.setItemLabelGenerator(TeamMapping::getTeamName);
        teamEditorField.setWidthFull();

        binder.forField(teamEditorField)
            .withConverter(new com.vaadin.flow.data.converter.Converter<TeamMapping, Integer>() {
                @Override
                public com.vaadin.flow.data.binder.Result<Integer> convertToModel(TeamMapping value, com.vaadin.flow.data.binder.ValueContext context) {
                    return com.vaadin.flow.data.binder.Result.ok(value != null ? value.getTeamId() : null);
                }

                @Override
                public TeamMapping convertToPresentation(Integer value, com.vaadin.flow.data.binder.ValueContext context) {
                    if (value == null) return null;
                    String name = teamMappingRepository.findByCarType(getCarTypeForLeague()).stream()
                        .filter(t -> Objects.equals(t.getTeamId(), value))
                        .map(TeamMapping::getTeamName)
                        .findFirst()
                        .orElse(null);
                    if (name == null) return null;
                    return getTeamsForLeague().stream()
                        .filter(t -> Objects.equals(t.getTeamName(), name))
                        .findFirst()
                        .orElse(null);
                }
            })
            .bind(DriverMapping::getTeamId, DriverMapping::setTeamId);
        teamColumn.setEditorComponent(teamEditorField);

        reserveField.addValueChangeListener(ev -> {
            if (ev.getValue()) {
                teamEditorField.setValue(null);
                teamEditorField.setEnabled(false);
                champTeamEditorField.setValue("None");
                champTeamEditorField.setEnabled(false);
            } else {
                teamEditorField.setEnabled(true);
                champTeamEditorField.setEnabled(true);
            }
        });

        editor.addOpenListener(ev -> {
            DriverMapping item = ev.getItem();
            if (item.isReserve()) {
                teamEditorField.setValue(null);
                teamEditorField.setEnabled(false);
                champTeamEditorField.setValue("None");
                champTeamEditorField.setEnabled(false);
            } else {
                teamEditorField.setEnabled(true);
                champTeamEditorField.setEnabled(true);
            }
        });

        tierEditorField.setItemLabelGenerator(Tier::getName);
        tierEditorField.setWidthFull();
        binder.forField(tierEditorField).bind(DriverMapping::getTier, DriverMapping::setTier);
        tiersColumn.setEditorComponent(tierEditorField);

        Button saveButton = new Button("Save", e -> {
            if (!isOwner()) return;
            try {
                DriverMapping item = editor.getItem();
                editor.save();
                
                if (item.isReserve()) {
                    item.setChampionshipTeam(null);
                }
                
                checkTeamCapacityAndSave(item, () -> {
                    if (!isOwner()) return;
                    if (!validateChampionshipTeamConstraints(item)) {
                        editor.cancel();
                        onDataChanged.run();
                        return;
                    }
                    driverMappingRepository.save(item);
                    telemetryProcessingService.refreshDriverMappings(league.getId());
                    
                    // Recalculate standings for all tiers of the league to ensure consistency
                    List<Tier> tiers = tierRepository.findByLeague(league);
                    for (Tier t : tiers) {
                        telemetryProcessingService.recalculateStandings(t.getId());
                    }
                    
                    Notification.show("Driver and standings updated!", 3000, Notification.Position.TOP_CENTER);
                    onDataChanged.run();
                });
            } finally {
                e.getSource().setEnabled(true);
            }
        });
        saveButton.setDisableOnClick(true);
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        
        Button cancelButton = new Button("Cancel", e -> {
            editor.cancel();
            onDataChanged.run();
        });
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        
        mappingGrid.addComponentColumn(item -> {
            HorizontalLayout actions = new HorizontalLayout();
            
            Button editButton = new Button("Edit");
            editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editButton.addClickListener(e -> {
                if (!isOwner()) return;
                if (editor.isOpen()) editor.cancel();
                mappingGrid.getEditor().editItem(item);
            });

            Button copyBtn = new Button("Copy");
            copyBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
            copyBtn.addClickListener(e -> {
                if (!isOwner()) return;
                if (editor.isOpen()) editor.cancel();
                showCopyDriverDialog(item);
            });
            
            Button deleteBtn = new Button("Delete", e -> {
                if (!isOwner()) return;
                ConfirmDialog dialog = new ConfirmDialog();
                dialog.setHeader("Delete Driver Mapping?");
                dialog.setText("Are you sure you want to delete the mapping for '" + item.getTelemetryName() + "'?");
                dialog.setCancelable(true);
                dialog.setConfirmText("Delete");
                dialog.setConfirmButtonTheme("error primary");
                dialog.addConfirmListener(ev -> {
                    if (!isOwner()) return;
                    Notification deletingNote = new Notification("Deleting...");
                    deletingNote.setPosition(Notification.Position.TOP_CENTER);
                    deletingNote.setDuration(0);
                    deletingNote.open();
                    try {
                        driverMappingRepository.delete(item);
                        onDataChanged.run();
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

            actions.add(editButton, copyBtn, deleteBtn);
            actions.setVisible(isOwner());
            return actions;
        }).setEditorComponent(new HorizontalLayout(saveButton, cancelButton));

        mappingGrid.setItems(Collections.emptyList());
    }

    private void showCopyDriverDialog(DriverMapping source) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Copy Driver to Another Tier");

        ComboBox<Tier> targetTierField = new ComboBox<>("Target Tier");
        targetTierField.setItems(tierRepository.findByLeagueOrderByNameAsc(league).stream()
                .filter(t -> !Objects.equals(t.getId(), source.getTier() != null ? source.getTier().getId() : null))
                .toList());
        targetTierField.setItemLabelGenerator(Tier::getName);
        targetTierField.setWidthFull();

        Checkbox reserveField = new Checkbox("Reserve");
        reserveField.setValue(source.isReserve());
        
        ComboBox<TeamMapping> teamCombo = new ComboBox<>("Team");
        teamCombo.setItems(getTeamsForLeague());
        teamCombo.setItemLabelGenerator(TeamMapping::getTeamName);
        teamCombo.setWidthFull();

        ComboBox<String> champTeamCombo = new ComboBox<>("Championship Team");
        champTeamCombo.setItems("A", "B", "None");
        champTeamCombo.setItemLabelGenerator(val -> {
            if ("A".equals(val)) {
                return league != null && league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
            } else if ("B".equals(val)) {
                return league != null && league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
            }
            return "None";
        });
        champTeamCombo.setValue(source.getChampionshipTeam() != null ? source.getChampionshipTeam() : "None");
        champTeamCombo.setWidthFull();
        boolean teamsEnabled = Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
            && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
            && league.getTeamBName() != null && !league.getTeamBName().isEmpty();
        champTeamCombo.setVisible(teamsEnabled);

        if (source.isReserve()) {
            teamCombo.setValue(null);
            teamCombo.setEnabled(false);
            champTeamCombo.setValue("None");
            champTeamCombo.setEnabled(false);
        }

        reserveField.addValueChangeListener(ev -> {
            if (ev.getValue()) {
                teamCombo.setValue(null);
                teamCombo.setEnabled(false);
                champTeamCombo.setValue("None");
                champTeamCombo.setEnabled(false);
            } else {
                teamCombo.setEnabled(true);
                champTeamCombo.setEnabled(true);
            }
        });

        VerticalLayout layout = new VerticalLayout(targetTierField, reserveField, teamCombo, champTeamCombo);
        dialog.add(layout);

        Button saveBtn = new Button("Copy", ev -> {
            if (!isOwner()) return;
            if (targetTierField.getValue() == null) {
                Notification.show("Please select a target tier", 3000, Notification.Position.TOP_CENTER);
                return;
            }
            
            Optional<DriverMapping> existing = driverMappingRepository.findByTierAndTelemetryNameAndRaceNumberAndDriverIdAndCountry(
                targetTierField.getValue(),
                source.getTelemetryName(),
                source.getRaceNumber(),
                source.getDriverId(),
                source.getCountry()
            );
            if (existing.isPresent()) {
                Notification notification = Notification.show("Driver is already mapped in the target tier!", 3000, Notification.Position.TOP_CENTER);
                notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
                return;
            }
            
            DriverMapping copy = new DriverMapping();
            copy.setLeague(source.getLeague());
            copy.setTelemetryName(source.getTelemetryName());
            copy.setRaceNumber(source.getRaceNumber());
            copy.setDriverId(source.getDriverId());
            copy.setOverriddenName(source.getOverriddenName());
            copy.setCountry(source.getCountry());
            copy.setTier(targetTierField.getValue());
            copy.setReserve(reserveField.getValue());
            copy.setTeamId(teamCombo.getValue() != null ? teamCombo.getValue().getTeamId() : null);
            if (teamsEnabled && !"None".equals(champTeamCombo.getValue()) && !copy.isReserve()) {
                copy.setChampionshipTeam(champTeamCombo.getValue());
            } else {
                copy.setChampionshipTeam(null);
            }

            checkTeamCapacityAndSave(copy, () -> {
                if (!isOwner()) return;
                if (!validateChampionshipTeamConstraints(copy)) {
                    onDataChanged.run();
                    return;
                }
                driverMappingRepository.save(copy);
                telemetryProcessingService.refreshDriverMappings(league.getId());
                
                List<Tier> tiers = tierRepository.findByLeague(league);
                for (Tier t : tiers) {
                    telemetryProcessingService.recalculateStandings(t.getId());
                }
                
                onDataChanged.run();
                dialog.close();
                Notification.show("Driver copied successfully!", 3000, Notification.Position.TOP_CENTER);
            });
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), saveBtn);
        dialog.open();
    }

    private String getCarTypeForLeague() {
        return league != null && league.getCarType() != null ? league.getCarType() : "F1 25";
    }

    private List<TeamMapping> getTeamsForLeague() {
        String carType = getCarTypeForLeague();
        List<TeamMapping> teams = teamMappingRepository.findByCarType(carType);
        if ("F1 26".equals(carType)) {
            teams = teams.stream().filter(t -> t.getTeamId() != null && t.getTeamId() >= 400).toList();
        }
        return teams;
    }

    private List<DriverMapping> getTeamDriversInTier(Tier tier, Integer teamId, Long excludeDriverMappingId) {
        if (tier == null || teamId == null) {
            return Collections.emptyList();
        }
        return driverMappingRepository.findByTier(tier).stream()
                .filter(m -> !Objects.equals(m.getId(), excludeDriverMappingId))
                .filter(m -> !m.isReserve())
                .filter(m -> Objects.equals(m.getTeamId(), teamId))
                .toList();
    }

    private boolean validateChampionshipTeamConstraints(DriverMapping mapping) {
        if (league == null || mapping == null || mapping.getTier() == null) return true;
        
        boolean teamsEnabled = Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
            && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
            && league.getTeamBName() != null && !league.getTeamBName().isEmpty();
            
        if (!teamsEnabled) return true;
        
        if (mapping.isReserve()) {
            return true;
        }
        
        // Simulate mappings in the tier
        List<DriverMapping> simulated = new ArrayList<>();
        boolean found = false;
        for (DriverMapping m : driverMappingRepository.findByTier(mapping.getTier())) {
            if (mapping.getId() != null && Objects.equals(m.getId(), mapping.getId())) {
                simulated.add(mapping);
                found = true;
            } else {
                simulated.add(m);
            }
        }
        if (!found) {
            simulated.add(mapping);
        }
        
        // Enforce reserve cleanups on all mappings in simulation (just to be safe)
        for (DriverMapping m : simulated) {
            if (m.isReserve()) {
                m.setChampionshipTeam(null);
            }
        }
        
        // 1. Max 11 regular players per team
        long countA = simulated.stream()
            .filter(m -> !m.isReserve() && "A".equals(m.getChampionshipTeam()))
            .count();
            
        long countB = simulated.stream()
            .filter(m -> !m.isReserve() && "B".equals(m.getChampionshipTeam()))
            .count();
            
        String teamAName = league.getTeamAName() != null && !league.getTeamAName().isEmpty() ? league.getTeamAName() : "Team A";
        String teamBName = league.getTeamBName() != null && !league.getTeamBName().isEmpty() ? league.getTeamBName() : "Team B";
        
        if (countA > 11) {
            Notification notification = Notification.show(teamAName + " cannot have more than 11 regular players.", 5000, Notification.Position.TOP_CENTER);
            notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            return false;
        }
        if (countB > 11) {
            Notification notification = Notification.show(teamBName + " cannot have more than 11 regular players.", 5000, Notification.Position.TOP_CENTER);
            notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            return false;
        }
        
        // 2. Only 1 split constructor
        Map<Integer, Set<String>> constructorTeams = new HashMap<>();
        for (DriverMapping m : simulated) {
            if (!m.isReserve() && m.getTeamId() != null && m.getChampionshipTeam() != null) {
                constructorTeams.computeIfAbsent(m.getTeamId(), k -> new java.util.HashSet<>())
                                .add(m.getChampionshipTeam());
            }
        }
        
        long splitCount = constructorTeams.values().stream()
            .filter(teams -> teams.contains("A") && teams.contains("B"))
            .count();
            
        if (splitCount > 1) {
            Notification notification = Notification.show("Only 1 constructor can have drivers split between both teams.", 5000, Notification.Position.TOP_CENTER);
            notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            return false;
        }
        
        return true;
    }

    private void checkTeamCapacityAndSave(DriverMapping mapping, Runnable saveAction) {
        if (mapping.isReserve() || mapping.getTeamId() == null || mapping.getTier() == null) {
            saveAction.run();
            return;
        }

        List<DriverMapping> teamDrivers = getTeamDriversInTier(mapping.getTier(), mapping.getTeamId(), mapping.getId());
        if (teamDrivers.size() >= 2) {
            Dialog replacementDialog = new Dialog();
            replacementDialog.setHeaderTitle("Team is Full");
            
            VerticalLayout layout = new VerticalLayout();
            layout.add(new Span("The team already has 2 active drivers in this tier:"));
            for (DriverMapping td : teamDrivers) {
                String displayName = td.getOverriddenName() != null && !td.getOverriddenName().isEmpty()
                        ? td.getOverriddenName() : td.getTelemetryName();
                layout.add(new Span("- " + displayName));
            }
            layout.add(new Span("Choose which driver to replace, or Cancel:"));
            
            HorizontalLayout buttonsLayout = new HorizontalLayout();
            
            List<Button> actionButtons = new ArrayList<>();
            for (DriverMapping td : teamDrivers) {
                String displayName = td.getOverriddenName() != null && !td.getOverriddenName().isEmpty()
                        ? td.getOverriddenName() : td.getTelemetryName();
                Button replaceBtn = new Button("Replace " + displayName);
                replaceBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                replaceBtn.addClickListener(ev -> {
                    actionButtons.forEach(b -> b.setEnabled(false));
                    td.setTeamId(null);
                    td.setChampionshipTeam(null);
                    driverMappingRepository.save(td);
                    replacementDialog.close();
                    saveAction.run();
                });
                actionButtons.add(replaceBtn);
                buttonsLayout.add(replaceBtn);
            }
            
            Button cancelBtn = new Button("Cancel", ev -> replacementDialog.close());
            buttonsLayout.add(cancelBtn);
            
            layout.add(buttonsLayout);
            replacementDialog.add(layout);
            replacementDialog.open();
        } else {
            saveAction.run();
        }
    }

    public void update(League league, Tier selectedTier, List<Tier> leagueTiers) {
        this.league = league;
        this.selectedTier = selectedTier;

        if (league == null || selectedTier == null) return;

        boolean isOwner = isOwner();
        addManualDriverBtn.setVisible(isOwner);
        deleteSelectedMappingsBtn.setVisible(isOwner);

        mappingGrid.setItems(driverMappingRepository.findByTier(selectedTier).stream()
                .sorted(Comparator.comparing(m -> m.getOverriddenName() != null ? m.getOverriddenName() : m.getTelemetryName()))
                .collect(Collectors.toList()));

        boolean teamsEnabled = league != null && Boolean.TRUE.equals(league.getUseChampionshipTeams()) 
            && league.getTeamAName() != null && !league.getTeamAName().isEmpty() 
            && league.getTeamBName() != null && !league.getTeamBName().isEmpty();
        
        if (champTeamColumn != null) {
            champTeamColumn.setVisible(teamsEnabled);
        }

        tierEditorField.setItems(leagueTiers);
        teamEditorField.setItems(getTeamsForLeague());
    }
}
