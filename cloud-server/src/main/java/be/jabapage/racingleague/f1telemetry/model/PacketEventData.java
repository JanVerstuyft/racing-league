package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Data
public class PacketEventData {
    private PacketHeader header;
    private String eventStringCode;

    public static PacketEventData fromByteBuffer(ByteBuffer buffer, PacketHeader header) {
        PacketEventData packet = new PacketEventData();
        packet.setHeader(header);
        
        byte[] codeBytes = new byte[4];
        buffer.get(codeBytes);
        packet.setEventStringCode(new String(codeBytes, StandardCharsets.UTF_8));
        
        return packet;
    }
}
