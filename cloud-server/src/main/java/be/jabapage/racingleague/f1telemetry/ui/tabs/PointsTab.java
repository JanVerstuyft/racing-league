package be.jabapage.racingleague.f1telemetry.ui.tabs;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.SessionPointConfig;
import be.jabapage.racingleague.f1telemetry.repository.SessionPointConfigRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PointsTab extends VerticalLayout {

    private final SessionPointConfigRepository sessionPointConfigRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;

    private League league;
    private final Grid<SessionPointConfig> pointsGrid = new Grid<>(SessionPointConfig.class, false);
    private final Tabs sessionTypeTabs = new Tabs();
    private final Button addSessionTypeBtn = new Button("Add Session Type");
    private final Button savePointsBtn = new Button("Save & Recalculate");
    private final Button deleteSessionBtn = new Button("Remove All Overrides for Session");
    private final IntegerField fastestLapPointsField = new IntegerField("Fastest Lap Bonus");
    private final IntegerField noPenaltyPointsField = new IntegerField("No Penalties Bonus");
    
    private Integer selectedSessionType = null;
    private final List<SessionPointConfig> currentEditingConfigs = new ArrayList<>();
    private boolean pointsChanged = false;

    public PointsTab(SessionPointConfigRepository sessionPointConfigRepository,
                     TelemetryProcessingService telemetryProcessingService,
                     SecurityService securityService) {
        this.sessionPointConfigRepository = sessionPointConfigRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;

        setSizeFull();
        configureGrid();

        add(new H3("Points Configuration Overrides"));
        add(new Span("By default, the standard F1 point system is used for Race sessions. Use this section to overrule points for any session type."));

        HorizontalLayout pointsHeader = new HorizontalLayout(sessionTypeTabs, addSessionTypeBtn);
        pointsHeader.setAlignItems(Alignment.END);
        pointsHeader.setWidthFull();
        pointsHeader.setFlexGrow(1, sessionTypeTabs);
        add(pointsHeader);

        pointsGrid.setWidthFull();

        VerticalLayout bonusSidebar = new VerticalLayout();
        bonusSidebar.setWidthFull();
        bonusSidebar.setPadding(false);
        bonusSidebar.setSpacing(true);

        bonusSidebar.add(new H3("Session Bonuses"));
        Span bonusNote = new Span("General bonuses awarded only to drivers who finish in a point-scoring position (standard for Races).");
        bonusNote.getStyle().set("font-size", "var(--lumo-font-size-s)");
        bonusNote.getStyle().set("color", "var(--lumo-secondary-text-color)");
        bonusSidebar.add(bonusNote, fastestLapPointsField, noPenaltyPointsField);

        fastestLapPointsField.setStepButtonsVisible(true);
        fastestLapPointsField.setWidthFull();
        noPenaltyPointsField.setStepButtonsVisible(true);
        noPenaltyPointsField.setWidthFull();

        fastestLapPointsField.addValueChangeListener(e -> pointsChanged = true);
        noPenaltyPointsField.addValueChangeListener(e -> pointsChanged = true);

        HorizontalLayout gridAndBonus = new HorizontalLayout(pointsGrid, bonusSidebar);
        gridAndBonus.setWidthFull();
        gridAndBonus.setAlignItems(Alignment.START);
        gridAndBonus.setFlexGrow(1, pointsGrid);
        gridAndBonus.setFlexGrow(1, bonusSidebar);
        add(gridAndBonus);

        HorizontalLayout pointsActions = new HorizontalLayout(savePointsBtn, deleteSessionBtn);
        add(pointsActions);

        sessionTypeTabs.addSelectedChangeListener(e -> {
            if (e.getSelectedTab() == null) return;
            Integer type = (Integer) ComponentUtil.getData(e.getSelectedTab(), Integer.class);
            loadPointsForSessionType(type);
        });

        savePointsBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        savePointsBtn.addClickListener(e -> saveCurrentPoints());

        deleteSessionBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        deleteSessionBtn.addClickListener(e -> deleteCurrentSessionPoints());

        addSessionTypeBtn.addClickListener(e -> showAddSessionTypeDialog());
    }

    private void configureGrid() {
        pointsGrid.addColumn(SessionPointConfig::getPosition).setHeader("Pos").setWidth("70px").setFlexGrow(0);
        pointsGrid.addComponentColumn(config -> {
            IntegerField field = new IntegerField();
            field.setValue(config.getPoints());
            field.setStepButtonsVisible(true);
            field.addValueChangeListener(e -> {
                config.setPoints(e.getValue() != null ? e.getValue() : 0);
                pointsChanged = true;
            });
            field.setWidthFull();
            return field;
        }).setHeader("Points Awarded");
        pointsGrid.setSelectionMode(Grid.SelectionMode.NONE);
    }

    public void setLeague(League league) {
        this.league = league;
        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        addSessionTypeBtn.setVisible(loggedIn);
        savePointsBtn.setVisible(loggedIn);
        deleteSessionBtn.setVisible(loggedIn);
        updateData();
    }

    public void updateData() {
        if (league == null) return;
        refreshPointsTabs();
    }

    private void refreshPointsTabs() {
        sessionTypeTabs.removeAll();
        List<SessionPointConfig> allConfigs = sessionPointConfigRepository.findByLeague(league);
        Set<Integer> configuredTypes = allConfigs.stream()
                .map(SessionPointConfig::getSessionType)
                .collect(Collectors.toSet());

        if (selectedSessionType != null) {
            configuredTypes.add(selectedSessionType);
        }

        configuredTypes.stream().sorted().forEach(type -> {
            Tab tab = new Tab(TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(type, "Session " + type));
            ComponentUtil.setData(tab, Integer.class, type);
            sessionTypeTabs.add(tab);
        });

        if (selectedSessionType != null) {
            for (int i = 0; i < sessionTypeTabs.getComponentCount(); i++) {
                Tab t = (Tab) sessionTypeTabs.getComponentAt(i);
                if (selectedSessionType.equals(ComponentUtil.getData(t, Integer.class))) {
                    sessionTypeTabs.setSelectedTab(t);
                    return;
                }
            }
        }

        if (sessionTypeTabs.getComponentCount() > 0) {
            sessionTypeTabs.setSelectedIndex(0);
            Tab selected = sessionTypeTabs.getSelectedTab();
            Integer type = (Integer) ComponentUtil.getData(selected, Integer.class);
            loadPointsForSessionType(type);
        } else {
            pointsGrid.setItems(java.util.Collections.emptyList());
            selectedSessionType = null;
        }
    }

    private void loadPointsForSessionType(Integer type) {
        selectedSessionType = type;
        List<SessionPointConfig> dbConfigs = sessionPointConfigRepository.findByLeague(league).stream()
                .filter(c -> c.getSessionType().equals(type))
                .sorted(Comparator.comparing(SessionPointConfig::getPosition))
                .toList();

        currentEditingConfigs.clear();
        if (dbConfigs.isEmpty()) {
            fastestLapPointsField.setValue(0);
            noPenaltyPointsField.setValue(0);
            boolean isRace = type >= 15 && type <= 17;
            int[] racePoints = {25, 18, 15, 12, 10, 8, 6, 4, 2, 1};
            for (int p = 1; p <= 20; p++) {
                SessionPointConfig c = new SessionPointConfig();
                c.setLeague(league);
                c.setSessionType(type);
                c.setPosition(p);
                c.setFastestLapPoints(0);
                c.setNoPenaltyPoints(0);
                int points = 0;
                if (isRace && p <= 10) points = racePoints[p - 1];
                c.setPoints(points);
                currentEditingConfigs.add(c);
            }
        } else {
            SessionPointConfig first = dbConfigs.get(0);
            fastestLapPointsField.setValue(first.getFastestLapPoints() != null ? first.getFastestLapPoints() : 0);
            noPenaltyPointsField.setValue(first.getNoPenaltyPoints() != null ? first.getNoPenaltyPoints() : 0);

            dbConfigs.forEach(db -> {
                SessionPointConfig clone = new SessionPointConfig();
                clone.setId(db.getId());
                clone.setLeague(league);
                clone.setSessionType(db.getSessionType());
                clone.setPosition(db.getPosition());
                clone.setPoints(db.getPoints());
                clone.setFastestLapPoints(db.getFastestLapPoints());
                clone.setNoPenaltyPoints(db.getNoPenaltyPoints());
                currentEditingConfigs.add(clone);
            });
            Set<Integer> existingPos = currentEditingConfigs.stream().map(SessionPointConfig::getPosition).collect(Collectors.toSet());
            for (int p = 1; p <= 20; p++) {
                if (!existingPos.contains(p)) {
                    SessionPointConfig c = new SessionPointConfig();
                    c.setLeague(league);
                    c.setSessionType(type);
                    c.setPosition(p);
                    c.setPoints(0);
                    c.setFastestLapPoints(fastestLapPointsField.getValue());
                    c.setNoPenaltyPoints(noPenaltyPointsField.getValue());
                    currentEditingConfigs.add(c);
                }
            }
            currentEditingConfigs.sort(Comparator.comparing(SessionPointConfig::getPosition));
        }

        pointsGrid.setItems(currentEditingConfigs);
        pointsChanged = false;
    }

    private void showAddSessionTypeDialog() {
        com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();
        dialog.setHeaderTitle("Add Points for Session Type");

        Set<Integer> configuredTypes = sessionPointConfigRepository.findByLeague(league).stream()
                .map(SessionPointConfig::getSessionType)
                .collect(Collectors.toSet());

        ComboBox<Integer> typeCombo = new ComboBox<>("Session Type");
        typeCombo.setItems(TelemetryProcessingService.SESSION_TYPE_NAMES.keySet().stream()
                .filter(t -> !configuredTypes.contains(t))
                .sorted().toList());
        typeCombo.setItemLabelGenerator(t -> TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(t, "Session " + t));
        typeCombo.setWidthFull();

        dialog.add(new VerticalLayout(new Span("Choose a session type to configure custom points. It will be initialized with defaults."), typeCombo));

        Button addBtn = new Button("Add", e -> {
            if (typeCombo.getValue() != null) {
                selectedSessionType = typeCombo.getValue();
                loadPointsForSessionType(selectedSessionType);
                refreshPointsTabs();
                dialog.close();
            }
        });
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), addBtn);
        dialog.open();
    }

    private void saveCurrentPoints() {
        if (selectedSessionType == null) return;

        List<SessionPointConfig> toDelete = sessionPointConfigRepository.findByLeague(league).stream()
                .filter(c -> c.getSessionType().equals(selectedSessionType))
                .toList();
        sessionPointConfigRepository.deleteAll(toDelete);

        currentEditingConfigs.forEach(c -> {
            c.setId(null);
            c.setFastestLapPoints(fastestLapPointsField.getValue() != null ? fastestLapPointsField.getValue() : 0);
            c.setNoPenaltyPoints(noPenaltyPointsField.getValue() != null ? noPenaltyPointsField.getValue() : 0);
        });
        sessionPointConfigRepository.saveAll(currentEditingConfigs);

        telemetryProcessingService.recalculateStandings(league.getId());
        updateData();
        Notification.show("Points saved and standings recalculated", 3000, Notification.Position.TOP_CENTER);
        pointsChanged = false;
    }

    private void deleteCurrentSessionPoints() {
        if (selectedSessionType == null) return;

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Remove All Overrides?");
        dialog.setText("Are you sure you want to remove all custom point overrides for " +
                TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(selectedSessionType, "this session") + "? It will revert to defaults.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Remove");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            List<SessionPointConfig> toDelete = sessionPointConfigRepository.findByLeague(league).stream()
                    .filter(c -> c.getSessionType().equals(selectedSessionType))
                    .toList();
            sessionPointConfigRepository.deleteAll(toDelete);
            telemetryProcessingService.recalculateStandings(league.getId());
            selectedSessionType = null;
            updateData();
            Notification.show("Overrides removed", 3000, Notification.Position.TOP_CENTER);
        });
        dialog.open();
    }
}
