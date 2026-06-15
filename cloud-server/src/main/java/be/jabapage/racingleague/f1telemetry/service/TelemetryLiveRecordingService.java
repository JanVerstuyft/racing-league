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

    // Map: "sessionUID_carIndex" -> List of samples for the current active lap
    final Map<String, List<TelemetrySample>> activeBuffers = new ConcurrentHashMap<>();

    // Map: "sessionUID_carIndex" -> latest position from PacketMotionData
    private final Map<String, float[]> latestPositions = new ConcurrentHashMap<>();

    // Map: "sessionUID_carIndex" -> timestamp (ms) of the last sample recorded
    private final Map<String, Long> lastSampleTimes = new ConcurrentHashMap<>();

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
            String key = sessionUID + "_" + i;
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
        long now = System.currentTimeMillis();

        for (int i = 0; i < maxCars; i++) {
            if (i >= state.getIsHuman().length || !state.getIsHuman()[i]) {
                continue; // Only record human drivers
            }

            String key = sessionUID + "_" + i;
            Long lastSampleTime = lastSampleTimes.get(key);
            if (lastSampleTime != null && (now - lastSampleTime < 50)) {
                continue; // Throttle recording to 20Hz (every 50ms)
            }

            // Get current lap distance and lap time from state
            float lapDistance = 0.0f;
            long lapTimeInMS = 0;
            if (state.getCurrentLapData() != null && i < state.getCurrentLapData().getLapData().size()) {
                LapData ld = state.getCurrentLapData().getLapData().get(i);
                lapDistance = ld.getLapDistance();
                lapTimeInMS = ld.getCurrentLapTimeInMS();
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
            lastSampleTimes.put(key, now);
        }
    }

    @Transactional
    public void processLapCompleted(LeagueSessionState state, int carIndex, int lapNumber, long lapTimeInMS, boolean isValid) {
        long sessionUID = state.getCurrentSessionUID();
        String key = sessionUID + "_" + carIndex;
        List<TelemetrySample> completedLapSamples = activeBuffers.remove(key); // Retrieve and clear active buffer
        lastSampleTimes.remove(key);

        if (state.getCurrentSession() == null) {
            return;
        }
        int sessionType = state.getCurrentSession().getSessionType();
        if (sessionType < 5 || sessionType > 14) {
            return; // Only qualifying sessions
        }

        if (completedLapSamples == null || completedLapSamples.isEmpty() || !isValid || lapTimeInMS <= 0) {
            log.debug("Discarded telemetry for invalid/empty lap {} (Driver: {}, Time: {}ms)", lapNumber, carIndex, lapTimeInMS);
            return;
        }

        // Check if this is the new fastest lap for this driver in this session
        long currentBest = state.getDriverBestLap()[carIndex];
        boolean isNewBest = currentBest == 0 || lapTimeInMS <= currentBest;

        if (isNewBest) {
            log.info("New fastest lap detected for driver index {} (Lap: {}, Time: {}ms). Saving telemetry...", carIndex, lapNumber, lapTimeInMS);
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

                // 4. Delete telemetry for previous best laps (keep only the single best lap)
                for (LapResult lr : lapResults) {
                    if (lr.getId() != null && !lr.getId().equals(targetLapResult.getId())) {
                        lapTelemetryRepository.findByLapResultId(lr.getId()).ifPresent(oldTelem -> {
                            lapTelemetryRepository.delete(oldTelem);
                            log.info("Deleted old telemetry for lap result ID: {}", lr.getId());
                        });
                    }
                }
            } catch (Exception e) {
                log.error("Failed to save lap telemetry", e);
            }
        } else {
            log.debug("Lap {} is not the best lap (Best: {}ms). Discarding telemetry.", lapNumber, currentBest);
        }
    }

    public void clearSessionBuffers(long sessionUID) {
        activeBuffers.keySet().removeIf(key -> key.startsWith(sessionUID + "_"));
        latestPositions.keySet().removeIf(key -> key.startsWith(sessionUID + "_"));
        lastSampleTimes.keySet().removeIf(key -> key.startsWith(sessionUID + "_"));
        log.info("Cleared live telemetry buffers for session UID: {}", sessionUID);
    }
}
