package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;

@Data
public class CarDamageData {
    private float[] tyresWear = new float[4];
    private int[] tyresDamage = new int[4];
    private int[] brakesDamage = new int[4];
    private int[] tyreBlisters = new int[4];
    private int frontLeftWingDamage;
    private int frontRightWingDamage;
    private int rearWingDamage;
    private int floorDamage;
    private int diffuserDamage;
    private int sidepodDamage;
    private int drsFault;
    private int ersFault;
    private int gearBoxDamage;
    private int engineDamage;
    private int engineMGUHWear;
    private int engineESWear;
    private int engineCEWear;
    private int engineICEWear;
    private int engineMGUKWear;
    private int engineTCWear;
    private int engineBlown;
    private int engineSeized;

    public static CarDamageData fromByteBuffer(ByteBuffer buffer) {
        CarDamageData data = new CarDamageData();
        for (int i = 0; i < 4; i++) data.tyresWear[i] = buffer.getFloat();
        for (int i = 0; i < 4; i++) data.tyresDamage[i] = buffer.get() & 0xFF;
        for (int i = 0; i < 4; i++) data.brakesDamage[i] = buffer.get() & 0xFF;
        for (int i = 0; i < 4; i++) data.tyreBlisters[i] = buffer.get() & 0xFF;
        data.setFrontLeftWingDamage(buffer.get() & 0xFF);
        data.setFrontRightWingDamage(buffer.get() & 0xFF);
        data.setRearWingDamage(buffer.get() & 0xFF);
        data.setFloorDamage(buffer.get() & 0xFF);
        data.setDiffuserDamage(buffer.get() & 0xFF);
        data.setSidepodDamage(buffer.get() & 0xFF);
        data.setDrsFault(buffer.get() & 0xFF);
        data.setErsFault(buffer.get() & 0xFF);
        data.setGearBoxDamage(buffer.get() & 0xFF);
        data.setEngineDamage(buffer.get() & 0xFF);
        data.setEngineMGUHWear(buffer.get() & 0xFF);
        data.setEngineESWear(buffer.get() & 0xFF);
        data.setEngineCEWear(buffer.get() & 0xFF);
        data.setEngineICEWear(buffer.get() & 0xFF);
        data.setEngineMGUKWear(buffer.get() & 0xFF);
        data.setEngineTCWear(buffer.get() & 0xFF);
        data.setEngineBlown(buffer.get() & 0xFF);
        data.setEngineSeized(buffer.get() & 0xFF);
        return data;
    }
}
