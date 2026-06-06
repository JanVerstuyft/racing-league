package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Data
public class PacketCarDamageData {
    private PacketHeader header;
    private List<CarDamageData> carDamageData = new ArrayList<>();

    public static PacketCarDamageData fromByteBuffer(ByteBuffer buffer, PacketHeader header) {
        PacketCarDamageData packet = new PacketCarDamageData();
        packet.setHeader(header);
        int maxCars = header.getPacketFormat() == 2026 ? 24 : 22;
        for (int i = 0; i < maxCars; i++) {
            packet.getCarDamageData().add(CarDamageData.fromByteBuffer(buffer));
        }
        return packet;
    }
}
