package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.util.Map;

@Data
public class RacePaceStats {
    private String driverName;
    private boolean ai;
    private String teamName;
    private String country;
    private double pureRacePace; // combined weighted average sectors
    private Map<String, Double> tyreUsage; // Compound Name -> Percentage
    private double sectorPerformance; // 0-10 scale
    private double s1Performance;
    private double s2Performance;
    private double s3Performance;
    private double s1Pace;
    private double s2Pace;
    private double s3Pace;
}
