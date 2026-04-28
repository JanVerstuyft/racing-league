package be.jabapage.racingleague.f1telemetry.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionInfo {
    private String sessionType;
    private int currentLap;
    private int totalLaps;
    private int timeLeftSeconds;
    private boolean isRace;
    private int safetyCarStatus; // 0 = no safety car, 1 = full, 2 = virtual, 3 = formation lap
}
