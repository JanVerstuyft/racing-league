package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;

@Data
public class LongestStintStats {
    private String driverName;
    private boolean ai;
    private String teamName;
    private int laps;
    private String tyreCompound;
    private double avgLapTime;
    private double avgS1;
    private double avgS2;
    private double avgS3;
}
