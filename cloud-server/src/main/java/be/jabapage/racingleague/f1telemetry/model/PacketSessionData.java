package be.jabapage.racingleague.f1telemetry.model;

import lombok.Data;
import java.nio.ByteBuffer;

@Data
public class PacketSessionData {
    private PacketHeader header;
    private int weather;                          // uint8
    private int trackTemperature;                 // int8
    private int airTemperature;                   // int8
    private int totalLaps;                        // uint8
    private int trackLength;                      // uint16
    private int sessionType;                      // uint8
    private int trackId;                          // int8
    private int formula;                          // uint8
    private int sessionTimeLeft;                  // uint16
    private int sessionDuration;                  // uint16
    private int pitSpeedLimit;                    // uint8
    private int gamePaused;                       // uint8
    private int isSpectating;                     // uint8
    private int spectatorCarIndex;                // uint8
    private int sliProNativeSupport;              // uint8
    private int numMarshalZones;                  // uint8
    // MarshalZone m_marshalZones[21] - skipping for now but need to advance buffer
    private int safetyCarStatus;                  // uint8
    private int networkGame;                      // uint8
    private int numWeatherForecastSamples;        // uint8
    private java.util.List<WeatherForecastSample> weatherForecastSamples = new java.util.ArrayList<>();
    private int forecastAccuracy;                 // uint8
    private int aiDifficulty;                     // uint8
    private long seasonLinkIdentifier;            // uint32
    private long weekendLinkIdentifier;           // uint32
    private long sessionLinkIdentifier;           // uint32
    private int pitStopWindowIdealLap;            // uint8
    private int pitStopWindowLatestLap;           // uint8
    private int pitStopRejoinPosition;            // uint8
    private int steeringAssist;                   // uint8
    private int brakingAssist;                    // uint8
    private int gearboxAssist;                    // uint8
    private int pitAssist;                        // uint8
    private int pitReleaseAssist;                 // uint8
    private int ERSAssist;                        // uint8
    private int DRSAssist;                        // uint8
    private int dynamicRacingLine;                // uint8
    private int dynamicRacingLineType;            // uint8
    private int gameMode;                         // uint8
    private int ruleSet;                          // uint8
    private long timeOfDay;                       // uint32
    private int sessionLength;                    // uint8
    private int speedUnitsLeadPlayer;             // uint8
    private int temperatureUnitsLeadPlayer;       // uint8
    private int speedUnitsSecondaryPlayer;        // uint8
    private int temperatureUnitsSecondaryPlayer;  // uint8
    private int numSafetyCarPeriods;              // uint8
    private int numVirtualSafetyCarPeriods;       // uint8
    private int numRedFlagPeriods;                // uint8
    private int equalCarPerformance;              // uint8
    private int recoveryMode;                     // uint8
    private int flashbackLimit;                   // uint8
    private int surfaceType;                      // uint8
    private int lowFuelMode;                      // uint8
    private int raceStarts;                       // uint8
    private int tyreTemperature;                  // uint8
    private int pitLaneTyreSim;                   // uint8
    private int carDamage;                        // uint8
    private int carDamageRate;                    // uint8
    private int collisions;                       // uint8
    private int collisionsOffForFirstLapOnly;     // uint8
    private int mpUnsafePitRelease;               // uint8
    private int mpOffForGriefing;                 // uint8
    private int cornerCuttingStringency;          // uint8
    private int parcFermeRules;                   // uint8
    private int pitStopExperience;                // uint8
    private int safetyCar;                        // uint8
    private int safetyCarExperience;              // uint8
    private int formationLap;                     // uint8
    private int formationLapExperience;           // uint8
    private int redFlags;                         // uint8
    private int affectsLicenceLevelSolo;          // uint8
    private int affectsLicenceLevelMP;            // uint8
    private int numSessionsInWeekend;             // uint8
    private int[] weekendStructure = new int[12]; // uint8[12]
    private float sector2LapDistanceStart;        // float
    private float sector3LapDistanceStart;        // float

    public static PacketSessionData fromByteBuffer(ByteBuffer buffer, PacketHeader header) {
        PacketSessionData packet = new PacketSessionData();
        packet.setHeader(header);
        packet.setWeather(buffer.get() & 0xFF);
        packet.setTrackTemperature(buffer.get());
        packet.setAirTemperature(buffer.get());
        packet.setTotalLaps(buffer.get() & 0xFF);
        packet.setTrackLength(buffer.getShort() & 0xFFFF);
        packet.setSessionType(buffer.get() & 0xFF);
        packet.setTrackId(buffer.get());
        packet.setFormula(buffer.get() & 0xFF);
        packet.setSessionTimeLeft(buffer.getShort() & 0xFFFF);
        packet.setSessionDuration(buffer.getShort() & 0xFFFF);
        packet.setPitSpeedLimit(buffer.get() & 0xFF);
        packet.setGamePaused(buffer.get() & 0xFF);
        packet.setIsSpectating(buffer.get() & 0xFF);
        packet.setSpectatorCarIndex(buffer.get() & 0xFF);
        packet.setSliProNativeSupport(buffer.get() & 0xFF);
        packet.setNumMarshalZones(buffer.get() & 0xFF);
        // Skip MarshalZones: 21 * (float(4) + int8(1)) = 105 bytes
        buffer.position(buffer.position() + 105);
        packet.setSafetyCarStatus(buffer.get() & 0xFF);
        packet.setNetworkGame(buffer.get() & 0xFF);
        packet.setNumWeatherForecastSamples(buffer.get() & 0xFF);
        for (int i = 0; i < 64; i++) {
            packet.getWeatherForecastSamples().add(WeatherForecastSample.fromByteBuffer(buffer));
        }
        packet.setForecastAccuracy(buffer.get() & 0xFF);
        packet.setAiDifficulty(buffer.get() & 0xFF);
        packet.setSeasonLinkIdentifier(buffer.getInt() & 0xFFFFFFFFL);
        packet.setWeekendLinkIdentifier(buffer.getInt() & 0xFFFFFFFFL);
        packet.setSessionLinkIdentifier(buffer.getInt() & 0xFFFFFFFFL);
        packet.setPitStopWindowIdealLap(buffer.get() & 0xFF);
        packet.setPitStopWindowLatestLap(buffer.get() & 0xFF);
        packet.setPitStopRejoinPosition(buffer.get() & 0xFF);
        packet.setSteeringAssist(buffer.get() & 0xFF);
        packet.setBrakingAssist(buffer.get() & 0xFF);
        packet.setGearboxAssist(buffer.get() & 0xFF);
        packet.setPitAssist(buffer.get() & 0xFF);
        packet.setPitReleaseAssist(buffer.get() & 0xFF);
        packet.setERSAssist(buffer.get() & 0xFF);
        packet.setDRSAssist(buffer.get() & 0xFF);
        packet.setDynamicRacingLine(buffer.get() & 0xFF);
        packet.setDynamicRacingLineType(buffer.get() & 0xFF);
        packet.setGameMode(buffer.get() & 0xFF);
        packet.setRuleSet(buffer.get() & 0xFF);
        packet.setTimeOfDay(buffer.getInt() & 0xFFFFFFFFL);
        packet.setSessionLength(buffer.get() & 0xFF);
        packet.setSpeedUnitsLeadPlayer(buffer.get() & 0xFF);
        packet.setTemperatureUnitsLeadPlayer(buffer.get() & 0xFF);
        packet.setSpeedUnitsSecondaryPlayer(buffer.get() & 0xFF);
        packet.setTemperatureUnitsSecondaryPlayer(buffer.get() & 0xFF);
        packet.setNumSafetyCarPeriods(buffer.get() & 0xFF);
        packet.setNumVirtualSafetyCarPeriods(buffer.get() & 0xFF);
        packet.setNumRedFlagPeriods(buffer.get() & 0xFF);
        packet.setEqualCarPerformance(buffer.get() & 0xFF);
        packet.setRecoveryMode(buffer.get() & 0xFF);
        packet.setFlashbackLimit(buffer.get() & 0xFF);
        packet.setSurfaceType(buffer.get() & 0xFF);
        packet.setLowFuelMode(buffer.get() & 0xFF);
        packet.setRaceStarts(buffer.get() & 0xFF);
        packet.setTyreTemperature(buffer.get() & 0xFF);
        packet.setPitLaneTyreSim(buffer.get() & 0xFF);
        packet.setCarDamage(buffer.get() & 0xFF);
        packet.setCarDamageRate(buffer.get() & 0xFF);
        packet.setCollisions(buffer.get() & 0xFF);
        packet.setCollisionsOffForFirstLapOnly(buffer.get() & 0xFF);
        packet.setMpUnsafePitRelease(buffer.get() & 0xFF);
        packet.setMpOffForGriefing(buffer.get() & 0xFF);
        packet.setCornerCuttingStringency(buffer.get() & 0xFF);
        packet.setParcFermeRules(buffer.get() & 0xFF);
        packet.setPitStopExperience(buffer.get() & 0xFF);
        packet.setSafetyCar(buffer.get() & 0xFF);
        packet.setSafetyCarExperience(buffer.get() & 0xFF);
        packet.setFormationLap(buffer.get() & 0xFF);
        packet.setFormationLapExperience(buffer.get() & 0xFF);
        packet.setRedFlags(buffer.get() & 0xFF);
        packet.setAffectsLicenceLevelSolo(buffer.get() & 0xFF);
        packet.setAffectsLicenceLevelMP(buffer.get() & 0xFF);
        packet.setNumSessionsInWeekend(buffer.get() & 0xFF);
        for (int i = 0; i < 12; i++) packet.weekendStructure[i] = buffer.get() & 0xFF;
        packet.setSector2LapDistanceStart(buffer.getFloat());
        packet.setSector3LapDistanceStart(buffer.getFloat());
        return packet;
    }
}
