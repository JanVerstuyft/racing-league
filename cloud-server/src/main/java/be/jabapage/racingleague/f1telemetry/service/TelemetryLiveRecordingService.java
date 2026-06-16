package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.LapResult;
import be.jabapage.racingleague.f1telemetry.entity.LapTelemetry;
import be.jabapage.racingleague.f1telemetry.model.*;
import be.jabapage.racingleague.f1telemetry.repository.LapResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.LapTelemetryRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TelemetryLiveRecordingService {

    @Autowired
    private LapResultRepository lapResultRepository;

    @Autowired
    private LapTelemetryRepository lapTelemetryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Map: "sessionUID_carIndex_lapNumber" -> List of samples for the current active lap
    final Map<String, List<TelemetrySample>> activeBuffers = new ConcurrentHashMap<>();

    // Map: "sessionUID_carIndex_lapNumber" -> latest position from PacketMotionData
    private final Map<String, float[]> latestPositions = new ConcurrentHashMap<>();

    // Map: "sessionUID_carIndex_lapNumber" -> last recorded game lap time (ms)
    private final Map<String, Long> lastRecordedLapTimes = new ConcurrentHashMap<>();

    @Data
    @AllArgsConstructor
    public static class TelemetrySample {
        private long timeOffsetInMS;
        private float lapDistance;
        private float x;
        private float z;
        private int speed;
        private float throttle;
        private float brake;
        private int gear;
        private int drs;
        private int ers;
    }

    @Data
    public static class ColumnarTelemetry {
        private List<Long> t = new ArrayList<>();
        private List<Float> d = new ArrayList<>();
        private List<Float> x = new ArrayList<>();
        private List<Float> z = new ArrayList<>();
        private List<Integer> spd = new ArrayList<>();
        private List<Float> thr = new ArrayList<>();
        private List<Float> brk = new ArrayList<>();
        private List<Integer> gear = new ArrayList<>();
        private List<Integer> ers = new ArrayList<>();
        private List<Integer> drs = new ArrayList<>();
    }

    public void recordMotion(LeagueSessionState state, PacketMotionData motionData) {
        if (state.getCurrentSession() == null) {
            return;
        }
        int sessionType = state.getCurrentSession().getSessionType();
        if (sessionType < 5 || sessionType > 14) {
            return; // Only qualifying sessions
        }
        long sessionUID = motionData.getHeader().getSessionUID();
        int maxCars = motionData.getCarMotionData().size();
        for (int i = 0; i < maxCars; i++) {
            if (i >= state.getIsHuman().length || !state.getIsHuman()[i]) {
                continue; // Only record human drivers
            }
            CarMotionData carMotion = motionData.getCarMotionData().get(i);
            int driverStatus = 0;
            int lapNumber = 1;
            if (state.getCurrentLapData() != null && i < state.getCurrentLapData().getLapData().size()) {
                LapData ld = state.getCurrentLapData().getLapData().get(i);
                driverStatus = ld.getDriverStatus();
                lapNumber = ld.getCurrentLapNum();
            }
            String key = sessionUID + "_" + i + "_" + lapNumber;
            if (driverStatus != 1) {
                continue; // Only record motion during flying laps
            }
            latestPositions.put(key, new float[]{carMotion.getWorldPositionX(), carMotion.getWorldPositionZ()});
        }
    }

    public void recordTelemetry(LeagueSessionState state, PacketCarTelemetryData telemetryData) {
        if (state.getCurrentSession() == null) {
            return;
        }
        int sessionType = state.getCurrentSession().getSessionType();
        if (sessionType < 5 || sessionType > 14) {
            return; // Only qualifying sessions
        }
        long sessionUID = telemetryData.getHeader().getSessionUID();
        int maxCars = telemetryData.getCarTelemetryData().size();

        for (int i = 0; i < maxCars; i++) {
            if (i >= state.getIsHuman().length || !state.getIsHuman()[i]) {
                continue; // Only record human drivers
            }

            // Get current lap distance, lap time, lap number and driver status from state
            float lapDistance = 0.0f;
            long lapTimeInMS = 0;
            int lapNumber = 1;
            int driverStatus = 0;
            if (state.getCurrentLapData() != null && i < state.getCurrentLapData().getLapData().size()) {
                LapData ld = state.getCurrentLapData().getLapData().get(i);
                lapDistance = ld.getLapDistance();
                lapTimeInMS = ld.getCurrentLapTimeInMS();
                lapNumber = ld.getCurrentLapNum();
                driverStatus = ld.getDriverStatus();
            }

            String key = sessionUID + "_" + i + "_" + lapNumber;
            if (driverStatus != 1) {
                continue; // Only record telemetry during flying laps
            }

            Long lastRecordedTime = lastRecordedLapTimes.get(key);
            if (lastRecordedTime != null && (lapTimeInMS - lastRecordedTime < 50)) {
                continue; // Throttle recording to 20Hz (every 50ms of game time)
            }

            // Get latest coordinates
            float[] pos = latestPositions.get(key);
            float x = (pos != null) ? pos[0] : 0.0f;
            float z = (pos != null) ? pos[1] : 0.0f;

            CarTelemetryData carTelem = telemetryData.getCarTelemetryData().get(i);
            
            // Get ERS mode
            int ers = 0;
            if (state.getCurrentCarStatus() != null && i < state.getCurrentCarStatus().getCarStatusData().size()) {
                ers = state.getCurrentCarStatus().getCarStatusData().get(i).getErsDeployMode();
            }

            TelemetrySample sample = new TelemetrySample(
                    lapTimeInMS,
                    lapDistance,
                    x,
                    z,
                    carTelem.getSpeed(),
                    carTelem.getThrottle(),
                    carTelem.getBrake(),
                    carTelem.getGear(),
                    carTelem.getDrs(),
                    ers
            );

            activeBuffers.computeIfAbsent(key, k -> new ArrayList<>()).add(sample);
            lastRecordedLapTimes.put(key, lapTimeInMS);
        }
    }

    @Transactional
    public void processLapCompleted(LeagueSessionState state, int carIndex, int lapNumber, long lapTimeInMS, boolean isValid) {
        long sessionUID = state.getCurrentSessionUID();
        String key = sessionUID + "_" + carIndex + "_" + lapNumber;
        List<TelemetrySample> completedLapSamples = activeBuffers.remove(key); // Retrieve and clear active buffer
        lastRecordedLapTimes.remove(key);

        if (state.getCurrentSession() == null) {
            return;
        }
        int sessionType = state.getCurrentSession().getSessionType();
        if (sessionType < 5 || sessionType > 14) {
            return; // Only qualifying sessions
        }

        if (completedLapSamples == null || completedLapSamples.isEmpty() || lapTimeInMS <= 0) {
            log.debug("Discarded telemetry for invalid/empty lap {} (Driver: {}, Time: {}ms)", lapNumber, carIndex, lapTimeInMS);
            return;
        }

        log.info("Saving telemetry for completed lap {} of driver index {} (Time: {}ms)...", lapNumber, carIndex, lapTimeInMS);
        try {
            // 1. Find the newly saved LapResult in the DB
            List<LapResult> lapResults = lapResultRepository.findBySessionUIDAndCarIndex(sessionUID, carIndex);
            LapResult targetLapResult = lapResults.stream()
                    .filter(lr -> lr.getLapNumber() != null && lr.getLapNumber() == lapNumber)
                    .findFirst()
                    .orElse(null);

            if (targetLapResult == null) {
                log.warn("Could not find LapResult in DB for sessionUID: {}, carIndex: {}, lapNumber: {}", sessionUID, carIndex, lapNumber);
                return;
            }

            // 2. Clean up boundary leftovers (prefix/suffix within the first/last 50 samples)
            List<TelemetrySample> cleanedSamples = new ArrayList<>(completedLapSamples);
            
            // Check for drop near the beginning (prefix)
            int prefixLimit = Math.min(50, cleanedSamples.size());
            for (int i = 0; i < prefixLimit - 1; i++) {
                float diff = cleanedSamples.get(i).getLapDistance() - cleanedSamples.get(i + 1).getLapDistance();
                if (diff > 500.0f) {
                    // Discard prefix samples from the previous lap
                    cleanedSamples = new ArrayList<>(cleanedSamples.subList(i + 1, cleanedSamples.size()));
                    break;
                }
            }
            
            // Check for drop near the end (suffix)
            int n = cleanedSamples.size();
            int suffixStart = Math.max(0, n - 50);
            for (int i = n - 2; i >= suffixStart; i--) {
                float diff = cleanedSamples.get(i).getLapDistance() - cleanedSamples.get(i + 1).getLapDistance();
                if (diff > 500.0f) {
                    // Discard suffix samples from the next lap
                    cleanedSamples = new ArrayList<>(cleanedSamples.subList(0, i + 1));
                    break;
                }
            }

            // Normalize timestamps and distances
            if (cleanedSamples.size() > 1) {
                long tStart = cleanedSamples.get(0).getTimeOffsetInMS();
                long tEnd = cleanedSamples.get(cleanedSamples.size() - 1).getTimeOffsetInMS();
                long tRange = tEnd - tStart;
                float dStart = cleanedSamples.get(0).getLapDistance();

                if (tRange > 0) {
                    for (TelemetrySample sample : cleanedSamples) {
                        long rawT = sample.getTimeOffsetInMS();
                        long normalizedT = Math.round((double) (rawT - tStart) * lapTimeInMS / tRange);
                        sample.setTimeOffsetInMS(normalizedT);
                        sample.setLapDistance(sample.getLapDistance() - dStart);
                    }
                } else {
                    for (TelemetrySample sample : cleanedSamples) {
                        sample.setTimeOffsetInMS(0);
                        sample.setLapDistance(sample.getLapDistance() - dStart);
                    }
                }
            } else if (!cleanedSamples.isEmpty()) {
                TelemetrySample single = cleanedSamples.get(0);
                single.setTimeOffsetInMS(0);
                single.setLapDistance(0.0f);
            }

            // 3. Serialize samples to columnar JSON
            ColumnarTelemetry columnar = new ColumnarTelemetry();
            for (TelemetrySample sample : cleanedSamples) {
                columnar.getT().add(sample.getTimeOffsetInMS());
                columnar.getD().add(sample.getLapDistance());
                columnar.getX().add(sample.getX());
                columnar.getZ().add(sample.getZ());
                columnar.getSpd().add(sample.getSpeed());
                columnar.getThr().add(sample.getThrottle());
                columnar.getBrk().add(sample.getBrake());
                columnar.getGear().add(sample.getGear());
                columnar.getErs().add(sample.getErs());
                columnar.getDrs().add(sample.getDrs());
            }
            String jsonData = objectMapper.writeValueAsString(columnar);

            // 3. Save new LapTelemetry (or update existing to prevent unique constraint violations)
            LapTelemetry telemetry = lapTelemetryRepository.findByLapResultId(targetLapResult.getId())
                    .orElseGet(LapTelemetry::new);
            telemetry.setLapResult(targetLapResult);
            telemetry.setTelemetryData(jsonData);
            lapTelemetryRepository.save(telemetry);
        } catch (Exception e) {
            log.error("Failed to save lap telemetry", e);
        }
    }

    public void clearSessionBuffers(long sessionUID) {
        activeBuffers.keySet().removeIf(key -> key.startsWith(sessionUID + "_"));
        latestPositions.keySet().removeIf(key -> key.startsWith(sessionUID + "_"));
        lastRecordedLapTimes.keySet().removeIf(key -> key.startsWith(sessionUID + "_"));
        log.info("Cleared live telemetry buffers for session UID: {}", sessionUID);
    }

    public void clearSessionBuffersForCar(long sessionUID, int carIndex, int lapNumber) {
        String key = sessionUID + "_" + carIndex + "_" + lapNumber;
        activeBuffers.remove(key);
        latestPositions.remove(key);
        lastRecordedLapTimes.remove(key);
        log.info("Cleared live telemetry buffer for driver index {} (Lap: {})", carIndex, lapNumber);
    }
}
