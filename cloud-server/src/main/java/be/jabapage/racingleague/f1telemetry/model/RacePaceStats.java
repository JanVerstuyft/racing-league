package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.util.Map;

@Data
public class RacePaceStats {
    private String driverName;
    private String teamName;
    private double pureRacePace; // combined weighted average sectors
    private Map<String, Double> tyreUsage; // Compound Name -> Percentage
    private double sectorPerformance; // 0-10 scale
    private double s1Pace;
    private double s2Pace;
    private double s3Pace;
}
