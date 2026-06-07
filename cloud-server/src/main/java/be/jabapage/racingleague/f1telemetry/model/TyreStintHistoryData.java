package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;

@Data
public class TyreStintHistoryData {
    private int endLap;               // uint8
    private int tyreActualCompound;   // uint8
    private int tyreVisualCompound;   // uint8

    public static TyreStintHistoryData fromByteBuffer(ByteBuffer buffer) {
        TyreStintHistoryData data = new TyreStintHistoryData();
        data.setEndLap(buffer.get() & 0xFF);
        data.setTyreActualCompound(buffer.get() & 0xFF);
        data.setTyreVisualCompound(buffer.get() & 0xFF);
        return data;
    }
}
