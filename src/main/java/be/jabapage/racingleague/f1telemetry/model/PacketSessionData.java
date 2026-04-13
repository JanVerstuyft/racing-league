package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;

@Data
public class PacketSessionData {
    private PacketHeader header;
    private int weather;              // uint8
    private int trackTemperature;     // int8
    private int airTemperature;       // int8
    private int totalLaps;            // uint8
    private int trackLength;          // uint16
    private int sessionType;          // uint8
    private int trackId;              // int8
    // ... many more fields, but these are the ones we need for now

    public static PacketSessionData fromByteBuffer(ByteBuffer buffer, PacketHeader header) {
        PacketSessionData packet = new PacketSessionData();
        packet.setHeader(header);
        packet.setWeather(buffer.get() & 0xFF);
        packet.setTrackTemperature(buffer.get());
        packet.setAirTemperature(buffer.get());
        packet.setTotalLaps(buffer.get() & 0xFF);
        packet.setTrackLength(buffer.getShort() & 0xFFFF);
        packet.setSessionType(buffer.get() & 0xFF);
        packet.setTrackId(buffer.get());
        return packet;
    }
}
