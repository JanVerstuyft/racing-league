package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Data
public class PacketHeader {
    private int packetFormat;           // uint16
    private int gameYear;               // uint8
    private int gameMajorVersion;       // uint8
    private int gameMinorVersion;       // uint8
    private int packetVersion;           // uint8
    private int packetId;                // uint8
    private long sessionUID;             // uint64
    private float sessionTime;           // float
    private long frameIdentifier;        // uint32
    private long overallFrameIdentifier; // uint32
    private int playerCarIndex;          // uint8
    private int secondaryPlayerCarIndex; // uint8

    public static PacketHeader fromByteBuffer(ByteBuffer buffer) {
        int originalPosition = buffer.position();
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        PacketHeader header = new PacketHeader();
        header.setPacketFormat(buffer.getShort() & 0xFFFF);
        header.setGameYear(buffer.get() & 0xFF);
        header.setGameMajorVersion(buffer.get() & 0xFF);
        header.setGameMinorVersion(buffer.get() & 0xFF);
        header.setPacketVersion(buffer.get() & 0xFF);
        header.setPacketId(buffer.get() & 0xFF);
        header.setSessionUID(buffer.getLong());
        header.setSessionTime(buffer.getFloat());
        header.setFrameIdentifier(buffer.getInt() & 0xFFFFFFFFL);
        header.setOverallFrameIdentifier(buffer.getInt() & 0xFFFFFFFFL);
        header.setPlayerCarIndex(buffer.get() & 0xFF);
        header.setSecondaryPlayerCarIndex(buffer.get() & 0xFF);
        
        // Return to start so subsequent consumers can read the whole packet if needed, 
        // OR we just ensure we know it's 29 bytes.
        // Actually, most fromByteBuffer methods expect the buffer to be at the start of THEIR data.
        // PacketSessionData.fromByteBuffer(buffer, header) expects buffer to be at byte 29.
        // So we should stay at position + 29.
        return header;
    }
}
