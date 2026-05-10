package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;

@Data
public class WeatherForecastSample {
    private int sessionType;              // uint8
    private int timeOffset;               // uint8
    private int weather;                  // uint8
    private int trackTemperature;         // int8
    private int trackTemperatureChange;   // int8
    private int airTemperature;           // int8
    private int airTemperatureChange;     // int8
    private int rainPercentage;           // uint8

    public static WeatherForecastSample fromByteBuffer(ByteBuffer buffer) {
        WeatherForecastSample sample = new WeatherForecastSample();
        sample.setSessionType(buffer.get() & 0xFF);
        sample.setTimeOffset(buffer.get() & 0xFF);
        sample.setWeather(buffer.get() & 0xFF);
        sample.setTrackTemperature(buffer.get());
        sample.setTrackTemperatureChange(buffer.get());
        sample.setAirTemperature(buffer.get());
        sample.setAirTemperatureChange(buffer.get());
        sample.setRainPercentage(buffer.get() & 0xFF);
        return sample;
    }
}
