package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;

@Data
public class DriverBoardState {
    private int position;
    private String name;
    private String team;
    private String tyreCompound;
    private int tyreAge;
    private int pitStops;
    private String gapToLeader;
    private String gapToFront;
    private int penalties;
    private int resultStatus;
    private boolean ai;
    
    // Qualifying fields
    private boolean qualifying;
    private String bestLapTime;
    private String gapToLeaderBest;
    private String s1Time;
    private String s2Time;
    private String s3Time;
    private boolean bestS1;
    private boolean bestS2;
    private boolean bestS3;
}
