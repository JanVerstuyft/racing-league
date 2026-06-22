package be.jabapage.racingleague.f1telemetry.ui.season;

import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.ExtraPointRule;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.SessionPointConfig;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.repository.ExtraPointRuleRepository;
import be.jabapage.racingleague.f1telemetry.repository.SessionPointConfigRepository;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import be.jabapage.racingleague.f1telemetry.security.SecurityService;
import be.jabapage.racingleague.f1telemetry.service.TelemetryProcessingService;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PointsTab extends VerticalLayout {

    private final SessionPointConfigRepository sessionPointConfigRepository;
    private final ExtraPointRuleRepository extraPointRuleRepository;
    private final TierRepository tierRepository;
    private final TelemetryProcessingService telemetryProcessingService;
    private final SecurityService securityService;
    private final Runnable onDataChanged;

    private League league;
    private Integer selectedSessionType = null;
    private final List<SessionPointConfig> currentEditingConfigs = new ArrayList<>();
    private boolean pointsChanged = false;

    private final Grid<SessionPointConfig> pointsGrid = new Grid<>(SessionPointConfig.class, false);
    private final Tabs sessionTypeTabs = new Tabs();
    private final Button addSessionTypeBtn = new Button("Add Session Type");
    private final Button savePointsBtn = new Button("Save & Recalculate");
    private final Button deleteSessionBtn = new Button("Remove All Overrides for Session");
    private final Grid<ExtraPointRule> extraPointRulesGrid = new Grid<>(ExtraPointRule.class, false);
    private final Button addExtraRuleBtn = new Button("Add Extra Point Rule");

    private Grid.Column<ExtraPointRule> extraRulesActionColumn;
    private Grid.Column<ExtraPointRule> extraRulesExpressionColumn;

    public PointsTab(SessionPointConfigRepository sessionPointConfigRepository,
                     ExtraPointRuleRepository extraPointRuleRepository,
                     TierRepository tierRepository,
                     TelemetryProcessingService telemetryProcessingService,
                     SecurityService securityService,
                     Runnable onDataChanged) {
        this.sessionPointConfigRepository = sessionPointConfigRepository;
        this.extraPointRuleRepository = extraPointRuleRepository;
        this.tierRepository = tierRepository;
        this.telemetryProcessingService = telemetryProcessingService;
        this.securityService = securityService;
        this.onDataChanged = onDataChanged;

        setSizeFull();

        add(new H3("Points Configuration Overrides"));
        add(new Span("By default, the standard F1 point system is used for Race sessions. Use this section to overrule points for any session type."));

        HorizontalLayout pointsHeader = new HorizontalLayout(sessionTypeTabs, addSessionTypeBtn);
        pointsHeader.setAlignItems(Alignment.END);
        pointsHeader.setWidthFull();
        pointsHeader.setFlexGrow(1, sessionTypeTabs);

        add(pointsHeader);

        pointsGrid.setWidth("550px");

        VerticalLayout bonusSidebar = new VerticalLayout();
        bonusSidebar.setWidthFull();
        bonusSidebar.setPadding(false);
        bonusSidebar.setSpacing(true);

        bonusSidebar.add(new H3("Extra Point Rules"));
        extraPointRulesGrid.setWidthFull();
        extraPointRulesGrid.setMinHeight("250px");
        bonusSidebar.add(extraPointRulesGrid, addExtraRuleBtn);

        addExtraRuleBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addExtraRuleBtn.addClickListener(e -> showAddExtraPointRuleDialog());

        HorizontalLayout gridAndBonus = new HorizontalLayout(pointsGrid, bonusSidebar);
        gridAndBonus.setWidthFull();
        gridAndBonus.setAlignItems(Alignment.START);
        gridAndBonus.setFlexGrow(0, pointsGrid);
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

        configureGrids();
    }

    private void configureGrids() {
        pointsGrid.addColumn(SessionPointConfig::getPosition).setHeader("Pos").setWidth("70px").setFlexGrow(0);
        pointsGrid.addComponentColumn(config -> {
            com.vaadin.flow.component.textfield.IntegerField field = new com.vaadin.flow.component.textfield.IntegerField();
            field.setValue(config.getPoints());
            field.setStepButtonsVisible(true);
            boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
            field.setReadOnly(!loggedIn);
            field.addValueChangeListener(e -> {
                if (loggedIn) {
                    config.setPoints(e.getValue() != null ? e.getValue() : 0);
                    pointsChanged = true;
                }
            });
            field.setWidthFull();
            return field;
        }).setHeader("Points Awarded");
        pointsGrid.setSelectionMode(Grid.SelectionMode.NONE);

        extraPointRulesGrid.addColumn(ExtraPointRule::getRuleName).setHeader("Name").setAutoWidth(true);
        extraPointRulesGrid.addColumn(r -> r.getMetric().getDisplayName()).setHeader("Metric").setAutoWidth(true);
        extraRulesExpressionColumn = extraPointRulesGrid.addComponentColumn(r -> {
            Span span = new Span(r.getMetricExpression());
            span.getElement().setProperty("title", r.getMetricExpression());
            span.getStyle().set("white-space", "nowrap");
            span.getStyle().set("overflow", "hidden");
            span.getStyle().set("text-overflow", "ellipsis");
            span.getStyle().set("display", "block");
            span.getStyle().set("max-width", "100%");
            return span;
        }).setHeader("Expression").setWidth("200px").setFlexGrow(1);
        extraPointRulesGrid.addColumn(r -> {
            if (r.getRuleType() == ExtraPointRule.RuleType.THRESHOLD_BELOW || r.getRuleType() == ExtraPointRule.RuleType.THRESHOLD_ABOVE) {
                return r.getRuleType().getDisplayName() + ": " + r.getThresholdValue();
            }
            return r.getRuleType().getDisplayName();
        }).setHeader("Condition").setAutoWidth(true);
        extraPointRulesGrid.addColumn(r -> r.getPoints() + " pt" + (r.getPoints() == 1 ? "" : "s")).setHeader("Points").setWidth("80px").setFlexGrow(0);
        extraRulesActionColumn = extraPointRulesGrid.addComponentColumn(r -> {
            Button delBtn = new Button("Delete", ev -> {
                extraPointRuleRepository.delete(r);
                List<Tier> tiers = tierRepository.findByLeague(league);
                for (Tier t : tiers) {
                    telemetryProcessingService.recalculateStandings(t.getId());
                }
                onDataChanged.run();
                Notification.show("Rule deleted and standings recalculated", 3000, Notification.Position.TOP_CENTER);
            });
            delBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            return delBtn;
        }).setHeader("Action").setWidth("100px").setFlexGrow(0);
        extraPointRulesGrid.setSelectionMode(Grid.SelectionMode.NONE);
    }

    private void loadPointsForSessionType(Integer type) {
        selectedSessionType = type;
        List<SessionPointConfig> dbConfigs = sessionPointConfigRepository.findByLeague(league).stream()
                .filter(c -> c.getSessionType().equals(type))
                .sorted(Comparator.comparing(SessionPointConfig::getPosition))
                .toList();

        currentEditingConfigs.clear();
        if (dbConfigs.isEmpty()) {
            boolean isRace = (type >= 15 && type <= 17);
            boolean isSprint = type == 19;
            int[] racePoints = {25, 18, 15, 12, 10, 8, 6, 4, 2, 1};
            int[] sprintPoints = {8, 7, 6, 5, 4, 3, 2, 1};
            for (int p = 1; p <= 20; p++) {
                SessionPointConfig c = new SessionPointConfig();
                c.setLeague(league);
                c.setSessionType(type);
                c.setPosition(p);
                int points = 0;
                if (isRace && p <= 10) points = racePoints[p-1];
                if (isSprint && p <= 8) points = sprintPoints[p-1];
                c.setPoints(points);
                currentEditingConfigs.add(c);
            }
        } else {
            dbConfigs.forEach(db -> {
                SessionPointConfig clone = new SessionPointConfig();
                clone.setId(db.getId());
                clone.setLeague(league);
                clone.setSessionType(db.getSessionType());
                clone.setPosition(db.getPosition());
                clone.setPoints(db.getPoints());
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
                    currentEditingConfigs.add(c);
                }
            }
            currentEditingConfigs.sort(Comparator.comparing(SessionPointConfig::getPosition));
        }

        pointsGrid.setItems(currentEditingConfigs);
        pointsChanged = false;
        List<ExtraPointRule> extraRules = extraPointRuleRepository.findByLeagueAndSessionType(league, type);
        extraPointRulesGrid.setItems(extraRules);
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

        List<SessionPointConfig> currentInDb = sessionPointConfigRepository.findByLeague(league).stream()
                .filter(c -> c.getSessionType().equals(selectedSessionType))
                .toList();

        if (currentInDb.isEmpty()) {
            Notification.show("Warning: No saved overrides exist for this session type yet. Saving current view.", 5000, Notification.Position.TOP_CENTER);
        }

        List<SessionPointConfig> toDelete = sessionPointConfigRepository.findByLeague(league).stream()
                .filter(c -> c.getSessionType().equals(selectedSessionType))
                .toList();
        sessionPointConfigRepository.deleteAll(toDelete);

        currentEditingConfigs.forEach(c -> {
            c.setId(null);
        });
        sessionPointConfigRepository.saveAll(currentEditingConfigs);

        List<Tier> tiers = tierRepository.findByLeague(league);
        for (Tier t : tiers) {
            telemetryProcessingService.recalculateStandings(t.getId());
        }

        onDataChanged.run();
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

            List<Tier> tiers = tierRepository.findByLeague(league);
            for (Tier t : tiers) {
                telemetryProcessingService.recalculateStandings(t.getId());
            }

            selectedSessionType = null;
            onDataChanged.run();
            Notification.show("Overrides removed", 3000, Notification.Position.TOP_CENTER);
        });
        dialog.open();
    }

    private void showAddExtraPointRuleDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add Extra Point Rule");

        TextField nameField = new TextField("Rule Name");
        nameField.setRequired(true);
        nameField.setWidthFull();

        ComboBox<ExtraPointRule.Metric> metricCombo = new ComboBox<>("Metric");
        metricCombo.setItems(ExtraPointRule.Metric.values());
        metricCombo.setItemLabelGenerator(ExtraPointRule.Metric::getDisplayName);
        metricCombo.setWidthFull();
        metricCombo.setRequired(true);

        TextField metricExpressionField = new TextField("SpEL Expression");
        metricExpressionField.setWidthFull();
        metricExpressionField.setRequired(true);

        ComboBox<ExtraPointRule.RuleType> ruleTypeCombo = new ComboBox<>("Rule Type");
        ruleTypeCombo.setItems(ExtraPointRule.RuleType.values());
        ruleTypeCombo.setItemLabelGenerator(ExtraPointRule.RuleType::getDisplayName);
        ruleTypeCombo.setWidthFull();
        ruleTypeCombo.setRequired(true);

        com.vaadin.flow.component.textfield.NumberField thresholdField = new com.vaadin.flow.component.textfield.NumberField("Threshold Value");
        thresholdField.setWidthFull();
        thresholdField.setVisible(false);

        com.vaadin.flow.component.textfield.IntegerField pointsField = new com.vaadin.flow.component.textfield.IntegerField("Points");
        pointsField.setValue(1);
        pointsField.setStepButtonsVisible(true);
        pointsField.setWidthFull();

        Checkbox mustFinishCb = new Checkbox("Must finish session", true);
        Checkbox onlyForPointScorersCb = new Checkbox("Only for point scorers (e.g. top 10)", true);
        Checkbox excludeAiCb = new Checkbox("Exclude AI drivers", true);

        metricCombo.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                if (nameField.getValue().isEmpty()) {
                    nameField.setValue(e.getValue().getDisplayName());
                }
                if (e.getValue() == ExtraPointRule.Metric.CUSTOM) {
                    metricExpressionField.setReadOnly(false);
                    metricExpressionField.setValue("");
                } else {
                    metricExpressionField.setValue(e.getValue().getDefaultExpression());
                    metricExpressionField.setReadOnly(true);
                }
                switch (e.getValue()) {
                    case PLACES_GAINED:
                        ruleTypeCombo.setValue(ExtraPointRule.RuleType.HIGHEST_VALUE);
                        break;
                    case FASTEST_LAP:
                        ruleTypeCombo.setValue(ExtraPointRule.RuleType.LOWEST_VALUE);
                        break;
                    case PENALTIES:
                    case WARNINGS:
                    case PENALTIES_AND_WARNINGS:
                        ruleTypeCombo.setValue(ExtraPointRule.RuleType.THRESHOLD_BELOW);
                        thresholdField.setValue(0.0);
                        break;
                    case GAP_TO_PREVIOUS:
                        ruleTypeCombo.setValue(ExtraPointRule.RuleType.LOWEST_VALUE);
                        break;
                    case CUSTOM:
                        break;
                }
            }
        });

        ruleTypeCombo.addValueChangeListener(e -> {
            boolean isThreshold = e.getValue() == ExtraPointRule.RuleType.THRESHOLD_BELOW || e.getValue() == ExtraPointRule.RuleType.THRESHOLD_ABOVE;
            thresholdField.setVisible(isThreshold);
        });

        VerticalLayout dialogLayout = new VerticalLayout(
            nameField, metricCombo, metricExpressionField, ruleTypeCombo, thresholdField, pointsField,
            mustFinishCb, onlyForPointScorersCb, excludeAiCb
        );
        dialog.add(dialogLayout);

        Button saveBtn = new Button("Add", ev -> {
            if (nameField.getValue().isEmpty() || metricCombo.getValue() == null || metricExpressionField.getValue().isEmpty() || ruleTypeCombo.getValue() == null) {
                Notification.show("Please fill in all required fields", 3000, Notification.Position.TOP_CENTER);
                return;
            }

            String exprStr = metricExpressionField.getValue();
            try {
                org.springframework.expression.Expression expression = 
                    new org.springframework.expression.spel.standard.SpelExpressionParser().parseExpression(exprStr);
                
                DriverResult dummyDriver = new DriverResult();
                dummyDriver.setPosition(1);
                dummyDriver.setGridPosition(2);
                dummyDriver.setBestLapTime(90.0f);
                dummyDriver.setPenalties(0);
                dummyDriver.setWarnings(0);
                dummyDriver.setTotalTime(3600.0);
                dummyDriver.setNumLaps(50);
                dummyDriver.setAi(false);
                dummyDriver.setDriverName("Test");
                dummyDriver.setTelemetryName("Test");
                dummyDriver.setResultStatus(3);
                
                DriverResult prevDriver = new DriverResult();
                prevDriver.setPosition(1);
                prevDriver.setGridPosition(2);
                prevDriver.setBestLapTime(91.0f);
                prevDriver.setPenalties(0);
                prevDriver.setWarnings(0);
                prevDriver.setTotalTime(3599.0);
                prevDriver.setNumLaps(50);
                prevDriver.setAi(false);
                prevDriver.setDriverName("Prev");
                prevDriver.setTelemetryName("Prev");
                prevDriver.setResultStatus(3);
                
                SessionResult dummySession = new SessionResult();
                dummySession.setSessionType(20);
                dummySession.setTrackId("austria");

                org.springframework.expression.spel.support.StandardEvaluationContext context = 
                    new org.springframework.expression.spel.support.StandardEvaluationContext(dummyDriver);
                context.setVariable("driver", dummyDriver);
                context.setVariable("session", dummySession);
                context.setVariable("previous", prevDriver);
                
                expression.getValue(context);
            } catch (Exception ex) {
                Notification.show("Invalid SpEL expression: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER);
                return;
            }

            ExtraPointRule rule = new ExtraPointRule();
            rule.setLeague(league);
            rule.setSessionType(selectedSessionType);
            rule.setRuleName(nameField.getValue());
            rule.setMetric(metricCombo.getValue());
            rule.setMetricExpression(metricExpressionField.getValue());
            rule.setRuleType(ruleTypeCombo.getValue());
            rule.setThresholdValue(thresholdField.getValue());
            rule.setPoints(pointsField.getValue() != null ? pointsField.getValue() : 0);
            rule.setMustFinish(mustFinishCb.getValue());
            rule.setOnlyForPointScorers(onlyForPointScorersCb.getValue());
            rule.setExcludeAi(excludeAiCb.getValue());

            extraPointRuleRepository.save(rule);
            
            List<Tier> tiers = tierRepository.findByLeague(league);
            for (Tier t : tiers) {
                telemetryProcessingService.recalculateStandings(t.getId());
            }

            onDataChanged.run();
            dialog.close();
            Notification.show("Extra point rule added and standings recalculated", 3000, Notification.Position.TOP_CENTER);
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", ev -> dialog.close()), saveBtn);
        dialog.open();
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

    public void update(League league) {
        this.league = league;

        if (league == null) return;

        boolean loggedIn = securityService.getAuthenticatedUser().isPresent();
        addSessionTypeBtn.setVisible(loggedIn);
        savePointsBtn.setVisible(loggedIn);
        deleteSessionBtn.setVisible(loggedIn);
        addExtraRuleBtn.setVisible(loggedIn);
        if (extraRulesActionColumn != null) {
            extraRulesActionColumn.setVisible(loggedIn);
        }
        if (extraRulesExpressionColumn != null) {
            extraRulesExpressionColumn.setVisible(loggedIn);
        }

        refreshPointsTabs();
    }
}
