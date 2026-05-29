package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;

@Data
public class ConsistencyStats {
    private String driverName;
    private boolean ai;
    private String teamName;
    private String country;
    private double rating;
    private double avgDiff;
    private double s1Rating;
    private double s2Rating;
    private double s3Rating;
    
    // Internal fields for calculation
    private double s1AvgDiff;
    private double s2AvgDiff;
    private double s3AvgDiff;
}
