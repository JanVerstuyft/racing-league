package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;

@Data
public class LapHistoryData {
    private long lapTimeInMS;            // uint32
    private int sector1TimeMSPart;       // uint16
    private int sector1TimeMinutesPart;  // uint8
    private int sector2TimeMSPart;       // uint16
    private int sector2TimeMinutesPart;  // uint8
    private int sector3TimeMSPart;       // uint16
    private int sector3TimeMinutesPart;  // uint8
    private int lapValidBitFlags;        // uint8

    public long getSector1TimeInMS() {
        return sector1TimeMinutesPart * 60000L + sector1TimeMSPart;
    }

    public long getSector2TimeInMS() {
        return sector2TimeMinutesPart * 60000L + sector2TimeMSPart;
    }

    public long getSector3TimeInMS() {
        return sector3TimeMinutesPart * 60000L + sector3TimeMSPart;
    }

    public static LapHistoryData fromByteBuffer(ByteBuffer buffer) {
        LapHistoryData data = new LapHistoryData();
        data.setLapTimeInMS(buffer.getInt() & 0xFFFFFFFFL);
        data.setSector1TimeMSPart(buffer.getShort() & 0xFFFF);
        data.setSector1TimeMinutesPart(buffer.get() & 0xFF);
        data.setSector2TimeMSPart(buffer.getShort() & 0xFFFF);
        data.setSector2TimeMinutesPart(buffer.get() & 0xFF);
        data.setSector3TimeMSPart(buffer.getShort() & 0xFFFF);
        data.setSector3TimeMinutesPart(buffer.get() & 0xFF);
        data.setLapValidBitFlags(buffer.get() & 0xFF);
        return data;
    }
}
