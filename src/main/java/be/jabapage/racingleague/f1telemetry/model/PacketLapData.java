package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Data
public class PacketLapData {
    private PacketHeader header;
    private List<LapData> lapData = new ArrayList<>();
    private int timeTrialPBCarIdx;     // uint8
    private int timeTrialRivalCarIdx;  // uint8

    public static PacketLapData fromByteBuffer(ByteBuffer buffer, PacketHeader header) {
        PacketLapData packet = new PacketLapData();
        packet.setHeader(header);
        for (int i = 0; i < 22; i++) {
            packet.getLapData().add(LapData.fromByteBuffer(buffer));
        }
        packet.setTimeTrialPBCarIdx(buffer.get() & 0xFF);
        packet.setTimeTrialRivalCarIdx(buffer.get() & 0xFF);
        return packet;
    }
}
