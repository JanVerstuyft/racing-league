package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.model.PacketCarStatusData;
import be.jabapage.racingleague.f1telemetry.model.PacketLapData;
import be.jabapage.racingleague.f1telemetry.model.PacketParticipantsData;
import be.jabapage.racingleague.f1telemetry.model.PacketSessionData;
import lombok.Data;

import java.util.Arrays;

@Data
public class LeagueSessionState {
    private Long leagueId;
    private boolean hideAi;
    private PacketSessionData currentSession;
    private PacketParticipantsData currentParticipants;
    private PacketLapData currentLapData;
    private PacketCarStatusData currentCarStatus;

    // Mapping: "telemetryName|raceNumber|driverId" -> "overriddenName"
    private final java.util.Map<String, String> driverNameOverrides = new java.util.concurrent.ConcurrentHashMap<>();

    private final int[] lastLapNum = new int[22];
    private final long[] lastS1 = new long[22];
    private final long[] lastS2 = new long[22];
    private final int[] lastTyre = new int[22];
    private final boolean[] lapInvalid = new boolean[22];

    private final long[] driverBestLap = new long[22];
    private final long[] driverBestS1 = new long[22];
    private final long[] driverBestS2 = new long[22];
    private final long[] driverBestS3 = new long[22];

    private long sessionBestS1 = Long.MAX_VALUE;
    private long sessionBestS2 = Long.MAX_VALUE;
    private long sessionBestS3 = Long.MAX_VALUE;
    private long sessionBestLap = Long.MAX_VALUE;

    private long currentSessionUID = -1;
    private long lastPacketTime = 0;

    public LeagueSessionState(Long leagueId) {
        this.leagueId = leagueId;
        reset();
    }

    public void reset() {
        Arrays.fill(lastLapNum, 0);
        Arrays.fill(lastS1, 0);
        Arrays.fill(lastS2, 0);
        Arrays.fill(lastTyre, 0);
        Arrays.fill(lapInvalid, false);
        Arrays.fill(driverBestLap, 0);
        Arrays.fill(driverBestS1, 0);
        Arrays.fill(driverBestS2, 0);
        Arrays.fill(driverBestS3, 0);
        sessionBestS1 = Long.MAX_VALUE;
        sessionBestS2 = Long.MAX_VALUE;
        sessionBestS3 = Long.MAX_VALUE;
        sessionBestLap = Long.MAX_VALUE;
        currentSession = null;
        currentParticipants = null;
        currentLapData = null;
        currentCarStatus = null;
        currentSessionUID = -1;
        lastPacketTime = 0;
    }
}
