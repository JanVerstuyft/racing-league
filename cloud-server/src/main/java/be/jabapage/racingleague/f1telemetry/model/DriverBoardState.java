package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DriverBoardState {
    private int position;
    
    @EqualsAndHashCode.Include
    private String name;
    
    @EqualsAndHashCode.Include
    private int raceNumber;
    private String team;
    private int teamId;
    private String country;
    private String tyreCompound;
    private int tyreAge;
    private int pitStops;
    private String gapToLeader;
    private String gapToFront;
    private int penalties;
    private int warnings;
    private int tyreWear;
    private int ersPercentage;
    private boolean ersActive;
    private int resultStatus;
    private boolean ai;
    private boolean showTyreWear;
    private boolean showErs;
    
    // Qualifying fields
    private boolean qualifying;
    private String bestLapTime;
    private boolean bestLap;
    private String gapToLeaderBest;
    private String s1Time;
    private String s2Time;
    private String s3Time;
    private boolean bestS1;
    private boolean bestS2;
    private boolean bestS3;
}
