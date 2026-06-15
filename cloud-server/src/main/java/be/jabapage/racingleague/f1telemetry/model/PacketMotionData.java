package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Data
public class PacketMotionData {
    private PacketHeader header;
    private List<CarMotionData> carMotionData = new ArrayList<>();

    public static PacketMotionData fromByteBuffer(ByteBuffer buffer, PacketHeader header) {
        PacketMotionData packet = new PacketMotionData();
        packet.setHeader(header);
        int maxCars = header.getPacketFormat() == 2026 ? 24 : 22;
        int carMotionDataSize = header.getPacketFormat() == 2026 ? 54 : 60;
        
        for (int i = 0; i < maxCars; i++) {
            CarMotionData data = new CarMotionData();
            data.setWorldPositionX(buffer.getFloat());
            data.setWorldPositionY(buffer.getFloat());
            data.setWorldPositionZ(buffer.getFloat());
            // skip the rest of the fields for this car:
            // velocity, forward/right dir, g-force, angles
            buffer.position(buffer.position() + (carMotionDataSize - 12));
            packet.getCarMotionData().add(data);
        }
        return packet;
    }
}
