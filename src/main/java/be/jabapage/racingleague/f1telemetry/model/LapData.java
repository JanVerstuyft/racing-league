package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;

@Data
public class LapData {
    private long lastLapTimeInMS;        // uint32
    private long currentLapTimeInMS;     // uint32
    private int sector1TimeInMS;        // uint16
    private int sector2TimeInMS;        // uint16
    private int deltaToCarInFrontInMS;   // uint16
    private int deltaToRaceLeaderInMS;   // uint16
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

    public static LapData fromByteBuffer(ByteBuffer buffer) {
        LapData data = new LapData();
        data.setLastLapTimeInMS(buffer.getInt() & 0xFFFFFFFFL);
        data.setCurrentLapTimeInMS(buffer.getInt() & 0xFFFFFFFFL);
        data.setSector1TimeInMS(buffer.getShort() & 0xFFFF);
        data.setSector2TimeInMS(buffer.getShort() & 0xFFFF);
        data.setDeltaToCarInFrontInMS(buffer.getShort() & 0xFFFF);
        data.setDeltaToRaceLeaderInMS(buffer.getShort() & 0xFFFF);
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
        return data;
    }
}
