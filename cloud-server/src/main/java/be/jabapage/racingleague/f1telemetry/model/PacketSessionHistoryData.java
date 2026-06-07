package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Data
public class PacketSessionHistoryData {
    private PacketHeader header;
    private int carIdx;                  // Index of the car this lap data relates to
    private int numLaps;                 // Num laps in the data (including current partial lap)
    private int numTyreStints;           // Number of tyre stints in the data
    private int bestLapTimeLapNum;       // Lap the best lap time was achieved on
    private int bestSector1LapNum;       // Lap the best Sector 1 time was achieved on
    private int bestSector2LapNum;       // Lap the best Sector 2 time was achieved on
    private int bestSector3LapNum;       // Lap the best Sector 3 time was achieved on
    private List<LapHistoryData> lapHistoryData = new ArrayList<>();
    private List<TyreStintHistoryData> tyreStintsHistoryData = new ArrayList<>();

    public static PacketSessionHistoryData fromByteBuffer(ByteBuffer buffer, PacketHeader header) {
        PacketSessionHistoryData packet = new PacketSessionHistoryData();
        packet.setHeader(header);
        packet.setCarIdx(buffer.get() & 0xFF);
        packet.setNumLaps(buffer.get() & 0xFF);
        packet.setNumTyreStints(buffer.get() & 0xFF);
        packet.setBestLapTimeLapNum(buffer.get() & 0xFF);
        packet.setBestSector1LapNum(buffer.get() & 0xFF);
        packet.setBestSector2LapNum(buffer.get() & 0xFF);
        packet.setBestSector3LapNum(buffer.get() & 0xFF);

        for (int i = 0; i < 100; i++) {
            packet.getLapHistoryData().add(LapHistoryData.fromByteBuffer(buffer));
        }

        for (int i = 0; i < 8; i++) {
            packet.getTyreStintsHistoryData().add(TyreStintHistoryData.fromByteBuffer(buffer));
        }

        return packet;
    }
}
