package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.LapResult;
import be.jabapage.racingleague.f1telemetry.entity.LapTelemetry;
import be.jabapage.racingleague.f1telemetry.model.*;
import be.jabapage.racingleague.f1telemetry.repository.LapResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.LapTelemetryRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TelemetryLiveRecordingServiceTest {

    @Mock
    private LapResultRepository lapResultRepository;

    @Mock
    private LapTelemetryRepository lapTelemetryRepository;

    @InjectMocks
    private TelemetryLiveRecordingService telemetryLiveRecordingService;

    private LeagueSessionState state;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        state = new LeagueSessionState(1L);
        state.setTierId(10L);
        state.setLeagueId(1L);
        state.setCurrentSessionUID(12345L);
        // Set car index 0 as human
        state.getIsHuman()[0] = true;

        PacketSessionData sessionData = new PacketSessionData();
        sessionData.setSessionType(8); // Short Qualifying
        state.setCurrentSession(sessionData);
    }

    @Test
    public void testRecordMotionAndTelemetryThrottled() throws Exception {
        // Setup mock motion packet
        PacketHeader header = new PacketHeader();
        header.setSessionUID(12345L);
        header.setPacketFormat(2025);

        PacketMotionData motionData = new PacketMotionData();
        motionData.setHeader(header);
        CarMotionData carMotion = new CarMotionData();
        carMotion.setWorldPositionX(100.0f);
        carMotion.setWorldPositionY(1.0f);
        carMotion.setWorldPositionZ(200.0f);
        motionData.getCarMotionData().add(carMotion);

        // Setup mock telemetry packet
        PacketCarTelemetryData telemetryData = new PacketCarTelemetryData();
        telemetryData.setHeader(header);
        CarTelemetryData carTelem = new CarTelemetryData();
        carTelem.setSpeed(180);
        carTelem.setThrottle(0.8f);
        carTelem.setBrake(0.0f);
        carTelem.setGear(4);
        carTelem.setDrs(0);
        telemetryData.getCarTelemetryData().add(carTelem);

        // Setup mock lap data in state
        PacketLapData lapData = new PacketLapData();
        LapData ld = new LapData();
        ld.setLapDistance(50.0f);
        ld.setCurrentLapTimeInMS(25000);
        ld.setCurrentLapNum((byte) 1);
        ld.setDriverStatus(1); // Flying lap
        lapData.getLapData().add(ld);
        state.setCurrentLapData(lapData);

        // Record motion
        telemetryLiveRecordingService.recordMotion(state, motionData);

        // Record telemetry (first sample should succeed)
        telemetryLiveRecordingService.recordTelemetry(state, telemetryData);

        // Record telemetry immediately again (should get throttled due to < 50ms)
        telemetryLiveRecordingService.recordTelemetry(state, telemetryData);

        // Mock completed lap logic
        LapResult newBestLapResult = new LapResult();
        newBestLapResult.setId(101L);
        newBestLapResult.setSessionUID(12345L);
        newBestLapResult.setCarIndex(0);
        newBestLapResult.setLapNumber(1);
        newBestLapResult.setLapTimeInMS(90000L);
        newBestLapResult.setIsValid(true);

        when(lapResultRepository.findBySessionUIDAndCarIndex(12345L, 0))
                .thenReturn(Collections.singletonList(newBestLapResult));

        // Let's trigger lap completion (since best lap in state is 0, this is the first best lap)
        telemetryLiveRecordingService.processLapCompleted(state, 0, 1, 90000L, true);

        // Verify that LapTelemetry is saved
        ArgumentCaptor<LapTelemetry> telemetryCaptor = ArgumentCaptor.forClass(LapTelemetry.class);
        verify(lapTelemetryRepository).save(telemetryCaptor.capture());

        LapTelemetry savedTelemetry = telemetryCaptor.getValue();
        assertNotNull(savedTelemetry);
        assertEquals(newBestLapResult, savedTelemetry.getLapResult());

        // Parse columnar JSON to verify values
        TelemetryLiveRecordingService.ColumnarTelemetry columnar =
                objectMapper.readValue(savedTelemetry.getTelemetryData(), TelemetryLiveRecordingService.ColumnarTelemetry.class);

        // Should have exactly 1 sample (the second one was throttled) and normalized to start at 0
        assertEquals(1, columnar.getT().size());
        assertEquals(0L, columnar.getT().get(0));
        assertEquals(0.0f, columnar.getD().get(0));
        assertEquals(100.0f, columnar.getX().get(0));
        assertEquals(200.0f, columnar.getZ().get(0));
        assertEquals(180, columnar.getSpd().get(0));
        assertEquals(0.8f, columnar.getThr().get(0));
        assertEquals(0.0f, columnar.getBrk().get(0));
        assertEquals(4, columnar.getGear().get(0));
    }

    @Test
    public void testLapCompletionReplacesPreviousSlowLapTelemetry() throws Exception {
        // Mock current best lap time in state
        state.getDriverBestLap()[0] = 88000L; // Driver best lap is 88.0s

        // Setup mock lap data in state
        PacketLapData lapData = new PacketLapData();
        LapData ld = new LapData();
        ld.setCurrentLapNum((byte) 2);
        ld.setDriverStatus(1); // Flying lap
        lapData.getLapData().add(ld);
        state.setCurrentLapData(lapData);

        // Record a mock sample
        PacketHeader header = new PacketHeader();
        header.setSessionUID(12345L);
        header.setPacketFormat(2025);
        PacketCarTelemetryData telemetryData = new PacketCarTelemetryData();
        telemetryData.setHeader(header);
        CarTelemetryData carTelem = new CarTelemetryData();
        telemetryData.getCarTelemetryData().add(carTelem);
        telemetryLiveRecordingService.recordTelemetry(state, telemetryData);

        // Driver completes a new lap: 87000ms (Faster than 88000ms best lap - so isNewBest is true)
        LapResult oldLapResult = new LapResult();
        oldLapResult.setId(99L);
        oldLapResult.setSessionUID(12345L);
        oldLapResult.setCarIndex(0);
        oldLapResult.setLapNumber(1);
        oldLapResult.setLapTimeInMS(88000L);

        LapResult newFastestLapResult = new LapResult();
        newFastestLapResult.setId(102L);
        newFastestLapResult.setSessionUID(12345L);
        newFastestLapResult.setCarIndex(0);
        newFastestLapResult.setLapNumber(2);
        newFastestLapResult.setLapTimeInMS(87000L);

        List<LapResult> allLaps = new ArrayList<>();
        allLaps.add(oldLapResult);
        allLaps.add(newFastestLapResult);

        when(lapResultRepository.findBySessionUIDAndCarIndex(12345L, 0)).thenReturn(allLaps);

        // Complete the lap
        telemetryLiveRecordingService.processLapCompleted(state, 0, 2, 87000L, true);

        // Verify new telemetry is saved
        verify(lapTelemetryRepository).save(any(LapTelemetry.class));

        // Verify old telemetry is not deleted during live recording
        verify(lapTelemetryRepository, never()).delete(any(LapTelemetry.class));
    }

    @Test
    public void testLapTelemetryBoundaryCleanup() throws Exception {
        // Setup mock data for activeBuffers containing previous-lap and next-lap leftovers
        String key = "12345_0_3";
        List<TelemetryLiveRecordingService.TelemetrySample> buffer = new ArrayList<>();
        // Leftovers from previous lap (high distance)
        buffer.add(new TelemetryLiveRecordingService.TelemetrySample(80000L, 4300.0f, 10.0f, 20.0f, 150, 0.5f, 0.0f, 5, 0, 0));
        buffer.add(new TelemetryLiveRecordingService.TelemetrySample(80100L, 4305.0f, 10.1f, 20.1f, 152, 0.5f, 0.0f, 5, 0, 0));
        
        // Actual current lap
        buffer.add(new TelemetryLiveRecordingService.TelemetrySample(10L, 5.0f, 1.0f, 2.0f, 50, 0.1f, 0.0f, 1, 0, 0));
        buffer.add(new TelemetryLiveRecordingService.TelemetrySample(100L, 10.0f, 1.1f, 2.1f, 60, 0.2f, 0.0f, 1, 0, 0));
        buffer.add(new TelemetryLiveRecordingService.TelemetrySample(200L, 20.0f, 1.2f, 2.2f, 70, 0.3f, 0.0f, 1, 0, 0));
        
        // Leftovers from next lap (low distance after finish line)
        buffer.add(new TelemetryLiveRecordingService.TelemetrySample(88000L, 4310.0f, 10.2f, 20.2f, 160, 0.6f, 0.0f, 6, 0, 0));
        buffer.add(new TelemetryLiveRecordingService.TelemetrySample(10L, 2.0f, 1.3f, 2.3f, 55, 0.1f, 0.0f, 1, 0, 0));
        buffer.add(new TelemetryLiveRecordingService.TelemetrySample(100L, 7.0f, 1.4f, 2.4f, 65, 0.2f, 0.0f, 1, 0, 0));

        // Inject the buffer into activeBuffers
        telemetryLiveRecordingService.activeBuffers.put(key, buffer);

        LapResult lapResult = new LapResult();
        lapResult.setId(103L);
        lapResult.setSessionUID(12345L);
        lapResult.setCarIndex(0);
        lapResult.setLapNumber(3);
        lapResult.setLapTimeInMS(88000L);
        lapResult.setIsValid(true);

        when(lapResultRepository.findBySessionUIDAndCarIndex(12345L, 0))
                .thenReturn(Collections.singletonList(lapResult));

        // Complete the lap
        telemetryLiveRecordingService.processLapCompleted(state, 0, 3, 88000L, true);

        // Capture saved telemetry
        ArgumentCaptor<LapTelemetry> telemetryCaptor = ArgumentCaptor.forClass(LapTelemetry.class);
        verify(lapTelemetryRepository).save(telemetryCaptor.capture());

        LapTelemetry savedTelemetry = telemetryCaptor.getValue();
        TelemetryLiveRecordingService.ColumnarTelemetry columnar =
                objectMapper.readValue(savedTelemetry.getTelemetryData(), TelemetryLiveRecordingService.ColumnarTelemetry.class);

        // Verify that prefix leftovers (size 2) and suffix leftovers (size 2) are removed
        // and distances/times are normalized starting at 0
        assertEquals(4, columnar.getD().size());
        assertEquals(0.0f, columnar.getD().get(0));
        assertEquals(5.0f, columnar.getD().get(1));
        assertEquals(15.0f, columnar.getD().get(2));
        assertEquals(4305.0f, columnar.getD().get(3));

        assertEquals(0L, columnar.getT().get(0));
        assertEquals(90L, columnar.getT().get(1));
        assertEquals(190L, columnar.getT().get(2));
        assertEquals(88000L, columnar.getT().get(3));
    }

    @Test
    public void testLapTimeResetClearsBuffer() throws Exception {
        PacketHeader header = new PacketHeader();
        header.setSessionUID(12345L);
        header.setPacketFormat(2025);

        PacketCarTelemetryData telemetryData = new PacketCarTelemetryData();
        telemetryData.setHeader(header);
        CarTelemetryData carTelem = new CarTelemetryData();
        carTelem.setSpeed(180);
        carTelem.setThrottle(0.8f);
        carTelem.setBrake(0.0f);
        carTelem.setGear(4);
        carTelem.setDrs(0);
        telemetryData.getCarTelemetryData().add(carTelem);

        PacketLapData lapData = new PacketLapData();
        LapData ld = new LapData();
        ld.setLapDistance(50.0f);
        ld.setCurrentLapTimeInMS(97573);
        ld.setCurrentLapNum((byte) 2);
        ld.setDriverStatus(1); // Flying lap
        lapData.getLapData().add(ld);
        state.setCurrentLapData(lapData);

        telemetryLiveRecordingService.recordTelemetry(state, telemetryData);

        String key = "12345_0_2";
        assertEquals(1, telemetryLiveRecordingService.activeBuffers.get(key).size());
        assertEquals(97573L, telemetryLiveRecordingService.activeBuffers.get(key).get(0).getTimeOffsetInMS());

        ld.setCurrentLapTimeInMS(50);
        ld.setLapDistance(100.0f);

        telemetryLiveRecordingService.recordTelemetry(state, telemetryData);

        assertEquals(1, telemetryLiveRecordingService.activeBuffers.get(key).size());
        assertEquals(50L, telemetryLiveRecordingService.activeBuffers.get(key).get(0).getTimeOffsetInMS());
        assertEquals(100.0f, telemetryLiveRecordingService.activeBuffers.get(key).get(0).getLapDistance());
    }
}

