package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;

@Data
public class CarDamageData {
    private float[] tyresWear = new float[4];           // float[4]
    private int[] tyresDamage = new int[4];            // uint8[4]
    private int[] brakesDamage = new int[4];           // uint8[4]
    private int frontLeftWingDamage;                   // uint8
    private int frontRightWingDamage;                  // uint8
    private int rearWingDamage;                        // uint8
    private int drsFault;                              // uint8
    private int ersFault;                              // uint8
    private int gearBoxDamage;                         // uint8
    private int engineDamage;                          // uint8
    private int engineMGUHDamage;                      // uint8
    private int engineESDamage;                        // uint8
    private int engineCEControlElectronicsDamage;      // uint8
    private int engineICEInternalCombustionEngineDamage; // uint8
    private int engineMGUKDamage;                      // uint8
    private int engineTCStoreDamage;                   // uint8
    private int engineTCTurboDamage;                   // uint8
    private int engineControlUnitDamage;               // uint8
    private int[] tyreBlisterPercentage = new int[4];  // uint8[4]

    public static CarDamageData fromByteBuffer(ByteBuffer buffer) {
        CarDamageData data = new CarDamageData();
        for (int i = 0; i < 4; i++) data.tyresWear[i] = buffer.getFloat();
        for (int i = 0; i < 4; i++) data.tyresDamage[i] = buffer.get() & 0xFF;
        for (int i = 0; i < 4; i++) data.brakesDamage[i] = buffer.get() & 0xFF;
        data.setFrontLeftWingDamage(buffer.get() & 0xFF);
        data.setFrontRightWingDamage(buffer.get() & 0xFF);
        data.setRearWingDamage(buffer.get() & 0xFF);
        data.setDrsFault(buffer.get() & 0xFF);
        data.setErsFault(buffer.get() & 0xFF);
        data.setGearBoxDamage(buffer.get() & 0xFF);
        data.setEngineDamage(buffer.get() & 0xFF);
        data.setEngineMGUHDamage(buffer.get() & 0xFF);
        data.setEngineESDamage(buffer.get() & 0xFF);
        data.setEngineCEControlElectronicsDamage(buffer.get() & 0xFF);
        data.setEngineICEInternalCombustionEngineDamage(buffer.get() & 0xFF);
        data.setEngineMGUKDamage(buffer.get() & 0xFF);
        data.setEngineTCStoreDamage(buffer.get() & 0xFF);
        data.setEngineTCTurboDamage(buffer.get() & 0xFF);
        data.setEngineControlUnitDamage(buffer.get() & 0xFF);
        for (int i = 0; i < 4; i++) data.tyreBlisterPercentage[i] = buffer.get() & 0xFF;
        return data;
    }
}
