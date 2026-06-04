package be.jabapage.racingleague.f1telemetry.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "extra_point_rule")
public class ExtraPointRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id")
    private League league;

    @Column(name = "session_type")
    private Integer sessionType;

    @Column(name = "rule_name")
    private String ruleName;

    @Column(name = "metric")
    @Enumerated(EnumType.STRING)
    private Metric metric;

    @Column(name = "metric_expression", length = 1000)
    private String metricExpression;

    @Column(name = "rule_type")
    @Enumerated(EnumType.STRING)
    private RuleType ruleType;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "points")
    private Integer points = 0;

    @Column(name = "must_finish")
    private Boolean mustFinish = true;

    @Column(name = "only_for_point_scorers")
    private Boolean onlyForPointScorers = true;

    @Column(name = "exclude_ai")
    private Boolean excludeAi = true;

    public enum Metric {
        PLACES_GAINED("Most Places Gained", "gridPosition != null && gridPosition > 0 ? gridPosition - position : null"),
        FASTEST_LAP("Fastest Lap", "bestLapTime != null && bestLapTime > 0 ? bestLapTime : null"),
        PENALTIES("Cleanest Driver (Penalties Only)", "penalties"),
        WARNINGS("Cleanest Driver (Warnings Only)", "warnings"),
        PENALTIES_AND_WARNINGS("Cleanest Driver (Penalties & Warnings)", "penalties + warnings"),
        GAP_TO_PREVIOUS("Closest Gap to Car Ahead", "#previous != null && numLaps != null && #previous.numLaps != null && numLaps == #previous.numLaps && totalTime != null && #previous.totalTime != null ? totalTime - #previous.totalTime : null"),
        CUSTOM("Custom Expression", "");

        private final String displayName;
        private final String defaultExpression;

        Metric(String displayName, String defaultExpression) {
            this.displayName = displayName;
            this.defaultExpression = defaultExpression;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDefaultExpression() {
            return defaultExpression;
        }
    }

    public enum RuleType {
        HIGHEST_VALUE("Highest Value"),
        LOWEST_VALUE("Lowest Value"),
        THRESHOLD_BELOW("Threshold (Below or Equal)"),
        THRESHOLD_ABOVE("Threshold (Above or Equal)");

        private final String displayName;

        RuleType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExtraPointRule that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
