package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;

@Data
public class CarMotionData {
    private float worldPositionX;
    private float worldPositionY;
    private float worldPositionZ;
}
