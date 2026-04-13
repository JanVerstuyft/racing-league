package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Data
public class ParticipantData {
    private int aiControlled;   // uint8
    private int driverId;       // uint8
    private int networkId;      // uint8
    private int teamId;         // uint8
    private int myTeam;         // uint8
    private int raceNumber;     // uint8
    private int nationality;    // uint8
    private String name;        // char[32]
    private int yourTelemetry;  // uint8
    private int showOnlineNames; // uint8
    private int platform;       // uint8

    public static ParticipantData fromByteBuffer(ByteBuffer buffer) {
        ParticipantData data = new ParticipantData();
        data.setAiControlled(buffer.get() & 0xFF);
        data.setDriverId(buffer.get() & 0xFF);
        data.setNetworkId(buffer.get() & 0xFF);
        data.setTeamId(buffer.get() & 0xFF);
        data.setMyTeam(buffer.get() & 0xFF);
        data.setRaceNumber(buffer.get() & 0xFF);
        data.setNationality(buffer.get() & 0xFF);
        
        byte[] nameBytes = new byte[32];
        buffer.get(nameBytes);
        data.setName(new String(nameBytes, StandardCharsets.UTF_8).trim());
        
        data.setYourTelemetry(buffer.get() & 0xFF);
        data.setShowOnlineNames(buffer.get() & 0xFF);
        data.setPlatform(buffer.get() & 0xFF);
        return data;
    }
}
