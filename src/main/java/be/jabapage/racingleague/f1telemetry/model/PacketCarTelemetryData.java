package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Data
public class PacketCarTelemetryData {
    private PacketHeader header;
    private List<CarTelemetryData> carTelemetryData = new ArrayList<>();
    private int mfdPanelIndex;          // uint8
    private int mfdPanelIndexSecondaryPlayer; // uint8
    private int suggestedGear;          // int8

    public static PacketCarTelemetryData fromByteBuffer(ByteBuffer buffer, PacketHeader header) {
        PacketCarTelemetryData packet = new PacketCarTelemetryData();
        packet.setHeader(header);
        for (int i = 0; i < 22; i++) {
            packet.getCarTelemetryData().add(CarTelemetryData.fromByteBuffer(buffer));
        }
        packet.setMfdPanelIndex(buffer.get() & 0xFF);
        packet.setMfdPanelIndexSecondaryPlayer(buffer.get() & 0xFF);
        packet.setSuggestedGear(buffer.get());
        return packet;
    }
}
