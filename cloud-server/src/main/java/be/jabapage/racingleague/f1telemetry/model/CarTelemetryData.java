package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;

@Data
public class CarTelemetryData {
    private int speed;
    private float throttle;
    private float brake;
    private int gear;
    private int drs;
}
