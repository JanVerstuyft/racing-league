package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Data
public class PacketCarTelemetryData {
    private PacketHeader header;
    private List<CarTelemetryData> carTelemetryData = new ArrayList<>();

    public static PacketCarTelemetryData fromByteBuffer(ByteBuffer buffer, PacketHeader header) {
        PacketCarTelemetryData packet = new PacketCarTelemetryData();
        packet.setHeader(header);
        int maxCars = header.getPacketFormat() == 2026 ? 24 : 22;
        int carTelemetryDataSize = header.getPacketFormat() == 2026 ? 59 : 60;
        
        for (int i = 0; i < maxCars; i++) {
            CarTelemetryData data = new CarTelemetryData();
            data.setSpeed(buffer.getShort() & 0xFFFF);
            data.setThrottle(buffer.getFloat());
            buffer.getFloat(); // skip steer
            data.setBrake(buffer.getFloat());
            buffer.get(); // skip clutch
            data.setGear(buffer.get());
            buffer.getShort(); // skip engineRPM
            data.setDrs(buffer.get() & 0xFF); // 19 bytes parsed!
            
            // skip the rest of this car's telemetry data
            buffer.position(buffer.position() + (carTelemetryDataSize - 19));
            packet.getCarTelemetryData().add(data);
        }
        return packet;
    }
}
