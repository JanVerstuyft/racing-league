package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;

@Data
public class LapData {
    private long lastLapTimeInMS;        // uint32
    private long currentLapTimeInMS;     // uint32
    private int sector1TimeMSPart;       // uint16
    private int sector1TimeMinutesPart;  // uint8
    private int sector2TimeMSPart;       // uint16
    private int sector2TimeMinutesPart;  // uint8
    private int deltaToCarInFrontMSPart; // uint16
    private int deltaToCarInFrontMinutesPart; // uint8
    private int deltaToRaceLeaderMSPart; // uint16
    private int deltaToRaceLeaderMinutesPart; // uint8
    private float lapDistance;           // float
    private float totalDistance;         // float
    private float safetyCarDelta;        // float
    private int carPosition;             // uint8
    private int currentLapNum;           // uint8
    private int pitStatus;               // uint8
    private int numPitStops;             // uint8
    private int sector;                  // uint8
    private int currentLapInvalid;       // uint8
    private int penalties;               // uint8
    private int totalWarnings;           // uint8
    private int cornerCuttingWarnings;   // uint8
    private int numUnservedDriveThroughPens; // uint8
    private int numUnservedStopGoPens;       // uint8
    private int gridPosition;            // uint8
    private int driverStatus;            // uint8
    private int resultStatus;            // uint8
    private int pitLaneTimerActive;      // uint8
    private int pitLaneTimeInLaneInMS;   // uint16
    private int pitStopTimerInMS;        // uint16
    private int pitStopShouldServePen;   // uint8
    private float speedTrapFastestSpeed; // float
    private int speedTrapFastestLap;     // uint8

    public long getSector1TimeInMS() {
        return sector1TimeMinutesPart * 60000L + sector1TimeMSPart;
    }

    public long getSector2TimeInMS() {
        return sector2TimeMinutesPart * 60000L + sector2TimeMSPart;
    }

    public long getDeltaToCarInFrontInMS() {
        return deltaToCarInFrontMinutesPart * 60000L + deltaToCarInFrontMSPart;
    }

    public long getDeltaToRaceLeaderInMS() {
        return deltaToRaceLeaderMinutesPart * 60000L + deltaToRaceLeaderMSPart;
    }

    public static LapData fromByteBuffer(ByteBuffer buffer) {
        LapData data = new LapData();
        data.setLastLapTimeInMS(buffer.getInt() & 0xFFFFFFFFL);
        data.setCurrentLapTimeInMS(buffer.getInt() & 0xFFFFFFFFL);
        data.setSector1TimeMSPart(buffer.getShort() & 0xFFFF);
        data.setSector1TimeMinutesPart(buffer.get() & 0xFF);
        data.setSector2TimeMSPart(buffer.getShort() & 0xFFFF);
        data.setSector2TimeMinutesPart(buffer.get() & 0xFF);
        data.setDeltaToCarInFrontMSPart(buffer.getShort() & 0xFFFF);
        data.setDeltaToCarInFrontMinutesPart(buffer.get() & 0xFF);
        data.setDeltaToRaceLeaderMSPart(buffer.getShort() & 0xFFFF);
        data.setDeltaToRaceLeaderMinutesPart(buffer.get() & 0xFF);
        data.setLapDistance(buffer.getFloat());
        data.setTotalDistance(buffer.getFloat());
        data.setSafetyCarDelta(buffer.getFloat());
        data.setCarPosition(buffer.get() & 0xFF);
        data.setCurrentLapNum(buffer.get() & 0xFF);
        data.setPitStatus(buffer.get() & 0xFF);
        data.setNumPitStops(buffer.get() & 0xFF);
        data.setSector(buffer.get() & 0xFF);
        data.setCurrentLapInvalid(buffer.get() & 0xFF);
        data.setPenalties(buffer.get() & 0xFF);
        data.setTotalWarnings(buffer.get() & 0xFF);
        data.setCornerCuttingWarnings(buffer.get() & 0xFF);
        data.setNumUnservedDriveThroughPens(buffer.get() & 0xFF);
        data.setNumUnservedStopGoPens(buffer.get() & 0xFF);
        data.setGridPosition(buffer.get() & 0xFF);
        data.setDriverStatus(buffer.get() & 0xFF);
        data.setResultStatus(buffer.get() & 0xFF);
        data.setPitLaneTimerActive(buffer.get() & 0xFF);
        data.setPitLaneTimeInLaneInMS(buffer.getShort() & 0xFFFF);
        data.setPitStopTimerInMS(buffer.getShort() & 0xFFFF);
        data.setPitStopShouldServePen(buffer.get() & 0xFF);
        data.setSpeedTrapFastestSpeed(buffer.getFloat());
        data.setSpeedTrapFastestLap(buffer.get() & 0xFF);
        return data;
    }
}
