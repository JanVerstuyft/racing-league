package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Data
public class PacketFinalClassificationData {
    private PacketHeader header;
    private int numCars;
    private List<FinalClassificationData> classificationData = new ArrayList<>();

    public static PacketFinalClassificationData fromByteBuffer(ByteBuffer buffer, PacketHeader header) {
        PacketFinalClassificationData packet = new PacketFinalClassificationData();
        packet.setHeader(header);
        packet.setNumCars(buffer.get() & 0xFF);
        for (int i = 0; i < 22; i++) {
            packet.getClassificationData().add(FinalClassificationData.fromByteBuffer(buffer));
        }
        return packet;
    }
}
