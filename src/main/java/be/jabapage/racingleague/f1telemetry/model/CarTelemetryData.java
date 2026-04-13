package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;

@Data
public class CarTelemetryData {
    private int speed;             // uint16
    private float throttle;        // float
    private float steer;           // float
    private float brake;           // float
    private int clutch;            // uint8
    private int gear;              // int8
    private int engineRPM;         // uint16
    private int drs;               // uint8
    private int revLightsPercent;  // uint8
    private int revLightsBitValue; // uint16
    private int[] brakesTemperature = new int[4]; // uint16[4]
    private int[] tyresSurfaceTemperature = new int[4]; // uint8[4]
    private int[] tyresInnerTemperature = new int[4]; // uint8[4]
    private int engineTemperature; // uint16
    private float[] tyresPressure = new float[4]; // float[4]
    private int[] surfaceType = new int[4]; // uint8[4]

    public static CarTelemetryData fromByteBuffer(ByteBuffer buffer) {
        CarTelemetryData data = new CarTelemetryData();
        data.setSpeed(buffer.getShort() & 0xFFFF);
        data.setThrottle(buffer.getFloat());
        data.setSteer(buffer.getFloat());
        data.setBrake(buffer.getFloat());
        data.setClutch(buffer.get() & 0xFF);
        data.setGear(buffer.get());
        data.setEngineRPM(buffer.getShort() & 0xFFFF);
        data.setDrs(buffer.get() & 0xFF);
        data.setRevLightsPercent(buffer.get() & 0xFF);
        data.setRevLightsBitValue(buffer.getShort() & 0xFFFF);
        for (int i = 0; i < 4; i++) data.brakesTemperature[i] = buffer.getShort() & 0xFFFF;
        for (int i = 0; i < 4; i++) data.tyresSurfaceTemperature[i] = buffer.get() & 0xFF;
        for (int i = 0; i < 4; i++) data.tyresInnerTemperature[i] = buffer.get() & 0xFF;
        data.setEngineTemperature(buffer.getShort() & 0xFFFF);
        for (int i = 0; i < 4; i++) data.tyresPressure[i] = buffer.getFloat();
        for (int i = 0; i < 4; i++) data.surfaceType[i] = buffer.get() & 0xFF;
        return data;
    }
}
