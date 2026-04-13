package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;

@Data
public class CarStatusData {
    private int tractionControl;          // uint8
    private int antiLockBrakes;           // uint8
    private int fuelMix;                  // uint8
    private int frontBrakeBias;           // uint8
    private int pitLimiterStatus;         // uint8
    private float fuelInTank;             // float
    private float fuelCapacity;           // float
    private float fuelRemainingLaps;      // float
    private int maxRPM;                   // uint16
    private int idleRPM;                  // uint16
    private int maxGears;                 // uint8
    private int drsAllowed;               // uint8
    private int drsActivationDistance;    // uint16
    private int actualTyreCompound;       // uint8
    private int visualTyreCompound;       // uint8
    private int tyresAgeLaps;             // uint8
    private int vehicleFiaFlags;          // int8
    private float ersStoreEnergy;         // float
    private int ersDeployMode;            // uint8
    private float ersHarvestedThisLapMGUK; // float
    private float ersHarvestedThisLapMGUH; // float
    private float ersDeployedThisLap;     // float
    private int networkPaused;            // uint8

    public static CarStatusData fromByteBuffer(ByteBuffer buffer) {
        CarStatusData data = new CarStatusData();
        data.setTractionControl(buffer.get() & 0xFF);
        data.setAntiLockBrakes(buffer.get() & 0xFF);
        data.setFuelMix(buffer.get() & 0xFF);
        data.setFrontBrakeBias(buffer.get() & 0xFF);
        data.setPitLimiterStatus(buffer.get() & 0xFF);
        data.setFuelInTank(buffer.getFloat());
        data.setFuelCapacity(buffer.getFloat());
        data.setFuelRemainingLaps(buffer.getFloat());
        data.setMaxRPM(buffer.getShort() & 0xFFFF);
        data.setIdleRPM(buffer.getShort() & 0xFFFF);
        data.setMaxGears(buffer.get() & 0xFF);
        data.setDrsAllowed(buffer.get() & 0xFF);
        data.setDrsActivationDistance(buffer.getShort() & 0xFFFF);
        data.setActualTyreCompound(buffer.get() & 0xFF);
        data.setVisualTyreCompound(buffer.get() & 0xFF);
        data.setTyresAgeLaps(buffer.get() & 0xFF);
        data.setVehicleFiaFlags(buffer.get());
        data.setErsStoreEnergy(buffer.getFloat());
        data.setErsDeployMode(buffer.get() & 0xFF);
        data.setErsHarvestedThisLapMGUK(buffer.getFloat());
        data.setErsHarvestedThisLapMGUH(buffer.getFloat());
        data.setErsDeployedThisLap(buffer.getFloat());
        data.setNetworkPaused(buffer.get() & 0xFF);
        return data;
    }
}
