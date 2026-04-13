package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Data
public class PacketParticipantsData {
    private PacketHeader header;
    private int numActiveCars;
    private List<ParticipantData> participants = new ArrayList<>();

    public static PacketParticipantsData fromByteBuffer(ByteBuffer buffer, PacketHeader header) {
        PacketParticipantsData packet = new PacketParticipantsData();
        packet.setHeader(header);
        packet.setNumActiveCars(buffer.get() & 0xFF);
        for (int i = 0; i < 22; i++) {
            packet.getParticipants().add(ParticipantData.fromByteBuffer(buffer));
        }
        return packet;
    }
}
