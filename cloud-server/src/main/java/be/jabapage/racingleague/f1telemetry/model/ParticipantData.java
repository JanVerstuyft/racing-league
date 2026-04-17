package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Data
public class ParticipantData {
    private int aiControlled;           // uint8
    private int driverId;               // uint8
    private int networkId;              // uint8
    private int teamId;                  // uint8
    private int myTeam;                  // uint8
    private int raceNumber;             // uint8
    private int nationality;            // uint8
    private String name;                // char[32]
    private int yourTelemetry;          // uint8
    private int showOnlineNames;        // uint8
    private int techLevel;              // uint16
    private int platform;               // uint8
    private int numColours;             // uint8
    private LiveryColour[] liveryColours = new LiveryColour[4];

    @Data
    public static class LiveryColour {
        private int red;
        private int green;
        private int blue;
    }

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
        String name = new String(nameBytes, StandardCharsets.UTF_8).trim();
        int nullIndex = name.indexOf('\0');
        if (nullIndex != -1) {
            name = name.substring(0, nullIndex);
        }
        data.setName(name);

        data.setYourTelemetry(buffer.get() & 0xFF);
        data.setShowOnlineNames(buffer.get() & 0xFF);
        data.setTechLevel(buffer.getShort() & 0xFFFF);
        data.setPlatform(buffer.get() & 0xFF);
        data.setNumColours(buffer.get() & 0xFF);
        for (int i = 0; i < 4; i++) {
            LiveryColour color = new LiveryColour();
            color.setRed(buffer.get() & 0xFF);
            color.setGreen(buffer.get() & 0xFF);
            color.setBlue(buffer.get() & 0xFF);
            data.liveryColours[i] = color;
        }
        return data;
    }
}
