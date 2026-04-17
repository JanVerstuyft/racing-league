package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;

@Data
public class FinalClassificationData {
    private int position;               // uint8
    private int numLaps;                // uint8
    private int gridPosition;           // uint8
    private int points;                 // uint8
    private int numPitStops;            // uint8
    private int resultStatus;           // uint8
    private int resultReason;           // uint8
    private long bestLapTimeInMS;       // uint32
    private double totalRaceTime;       // double
    private int penaltiesTime;          // uint8
    private int numPenalties;           // uint8
    private int numTyreStints;          // uint8
    private int[] tyreStintsActual = new int[8]; // uint8[8]
    private int[] tyreStintsVisual = new int[8]; // uint8[8]
    private int[] tyreStintsEndLaps = new int[8]; // uint8[8]

    public static FinalClassificationData fromByteBuffer(ByteBuffer buffer) {
        FinalClassificationData data = new FinalClassificationData();
        data.setPosition(buffer.get() & 0xFF);
        data.setNumLaps(buffer.get() & 0xFF);
        data.setGridPosition(buffer.get() & 0xFF);
        data.setPoints(buffer.get() & 0xFF);
        data.setNumPitStops(buffer.get() & 0xFF);
        data.setResultStatus(buffer.get() & 0xFF);
        data.setResultReason(buffer.get() & 0xFF);
        data.setBestLapTimeInMS(buffer.getInt() & 0xFFFFFFFFL);
        data.setTotalRaceTime(buffer.getDouble());
        data.setPenaltiesTime(buffer.get() & 0xFF);
        data.setNumPenalties(buffer.get() & 0xFF);
        data.setNumTyreStints(buffer.get() & 0xFF);
        for (int i = 0; i < 8; i++) data.tyreStintsActual[i] = buffer.get() & 0xFF;
        for (int i = 0; i < 8; i++) data.tyreStintsVisual[i] = buffer.get() & 0xFF;
        for (int i = 0; i < 8; i++) data.tyreStintsEndLaps[i] = buffer.get() & 0xFF;
        return data;
    }
}
