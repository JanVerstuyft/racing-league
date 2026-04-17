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
}
