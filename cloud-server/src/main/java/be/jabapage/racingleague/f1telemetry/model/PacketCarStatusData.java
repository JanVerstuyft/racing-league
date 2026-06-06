package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Data
public class PacketCarStatusData {
    private PacketHeader header;
    private List<CarStatusData> carStatusData = new ArrayList<>();

    public static PacketCarStatusData fromByteBuffer(ByteBuffer buffer, PacketHeader header) {
        PacketCarStatusData packet = new PacketCarStatusData();
        packet.setHeader(header);
        int maxCars = header.getPacketFormat() == 2026 ? 24 : 22;
        for (int i = 0; i < maxCars; i++) {
            packet.getCarStatusData().add(CarStatusData.fromByteBuffer(buffer, header.getPacketFormat()));
        }
        return packet;
    }
}
