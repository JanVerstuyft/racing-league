package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.LapResult;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.model.*;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.LapResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TelemetryPacketProcessorTest {

    @Mock private LeagueRepository leagueRepository;
    @Mock private TierRepository tierRepository;
    @Mock private DriverMappingRepository driverMappingRepository;
    @Mock private LapResultRepository lapResultRepository;
    @Mock private TelemetryStateService telemetryStateService;
    @Mock private TelemetryResultsService telemetryResultsService;
    @Mock private LiveDashboardService liveDashboardService;
    @Mock private Broadcaster broadcaster;

    @InjectMocks
    private TelemetryPacketProcessor telemetryPacketProcessor;

    private LeagueSessionState state;
    private PacketHeader header;
    private ByteBuffer buffer;

    private MockedStatic<PacketSessionData> mockedSessionData;
    private MockedStatic<PacketLapData> mockedLapData;
    private MockedStatic<PacketEventData> mockedEventData;
    private MockedStatic<PacketParticipantsData> mockedParticipantsData;
    private MockedStatic<PacketCarStatusData> mockedCarStatusData;
    private MockedStatic<PacketCarDamageData> mockedCarDamageData;
    private MockedStatic<PacketFinalClassificationData> mockedFinalClassificationData;
    private MockedStatic<PacketSessionHistoryData> mockedSessionHistoryData;

    @BeforeEach
    public void setUp() {
        state = new LeagueSessionState(1L);
        state.setTierId(10L);
        state.setLeagueId(1L);
        state.setCurrentSessionUID(12345L);

        header = new PacketHeader();
        header.setSessionUID(12345L);

        buffer = ByteBuffer.allocate(10);

        // Open mocked statics
        mockedSessionData = Mockito.mockStatic(PacketSessionData.class);
        mockedLapData = Mockito.mockStatic(PacketLapData.class);
        mockedEventData = Mockito.mockStatic(PacketEventData.class);
        mockedParticipantsData = Mockito.mockStatic(PacketParticipantsData.class);
        mockedCarStatusData = Mockito.mockStatic(PacketCarStatusData.class);
        mockedCarDamageData = Mockito.mockStatic(PacketCarDamageData.class);
        mockedFinalClassificationData = Mockito.mockStatic(PacketFinalClassificationData.class);
        mockedSessionHistoryData = Mockito.mockStatic(PacketSessionHistoryData.class);
    }

    @AfterEach
    public void tearDown() {
        // Close mocked statics to avoid thread leak
        mockedSessionData.close();
        mockedLapData.close();
        mockedEventData.close();
        mockedParticipantsData.close();
        mockedCarStatusData.close();
        mockedCarDamageData.close();
        mockedFinalClassificationData.close();
        mockedSessionHistoryData.close();
    }

    @Test
    public void testProcessPacketUnknownToken() {
        when(telemetryStateService.getOrCreateState("unknown")).thenReturn(null);

        telemetryPacketProcessor.processPacket("unknown", header, buffer);

        verifyNoInteractions(liveDashboardService);
    }

    @Test
    public void testProcessPacketSessionChangeResetsState() {
        when(telemetryStateService.getOrCreateState("test-token")).thenReturn(state);
        header.setSessionUID(99999L); // Different UID

        telemetryPacketProcessor.processPacket("test-token", header, buffer);

        assertEquals(99999L, state.getCurrentSessionUID());
        verify(broadcaster).broadcastLeaderboard(eq(10L), eq(Collections.emptyList()));
        verify(telemetryStateService).clearState(10L);
    }

    @Test
    public void testProcessPacketTimeoutResetsState() {
        when(telemetryStateService.getOrCreateState("test-token")).thenReturn(state);
        state.setLastPacketTime(System.currentTimeMillis() - 10000); // 10s ago (threshold is 5s)
        header.setSessionUID(12345L); // Same UID but timed out

        telemetryPacketProcessor.processPacket("test-token", header, buffer);

        verify(broadcaster).broadcastLeaderboard(eq(10L), eq(Collections.emptyList()));
        verify(telemetryStateService).clearState(10L);
    }

    @Test
    public void testProcessPacketSessionData() {
        when(telemetryStateService.getOrCreateState("test-token")).thenReturn(state);
        header.setPacketId((byte) 1);

        PacketSessionData sessionData = new PacketSessionData();
        mockedSessionData.when(() -> PacketSessionData.fromByteBuffer(buffer, header)).thenReturn(sessionData);

        telemetryPacketProcessor.processPacket("test-token", header, buffer);

        assertSame(sessionData, state.getCurrentSession());
        verify(liveDashboardService).broadcastSessionInfo(state);
    }

    @Test
    public void testProcessPacketLapDataSavesLapResultOnLapIncrease() {
        when(telemetryStateService.getOrCreateState("test-token")).thenReturn(state);
        header.setPacketId((byte) 2);

        PacketLapData lapData = new PacketLapData();
        LapData ld = new LapData();
        ld.setCarPosition((byte) 1);
        ld.setCurrentLapNum((byte) 2);
        ld.setLastLapTimeInMS(90000L);
        ld.setSector1TimeMinutesPart(0);
        ld.setSector1TimeMSPart(30000);
        ld.setSector2TimeMinutesPart(0);
        ld.setSector2TimeMSPart(40000);
        ld.setNumPitStops((byte) 0);
        lapData.getLapData().add(ld);
        lapData.setHeader(header);

        mockedLapData.when(() -> PacketLapData.fromByteBuffer(buffer, header)).thenReturn(lapData);

        // Pre-configure state for car 0 so last lap was lap 1 (triggers save)
        state.getLastLapNum()[0] = 1;
        state.getLastS1()[0] = 30000L;
        state.getLastS2()[0] = 40000L;
        state.getLapInvalid()[0] = false;

        telemetryPacketProcessor.processPacket("test-token", header, buffer);

        verify(lapResultRepository).save(any(LapResult.class));
        assertEquals(2, state.getLastLapNum()[0]);
        verify(liveDashboardService).broadcastLeaderboard(state);
        verify(liveDashboardService).broadcastSessionInfo(state);
    }

    @Test
    public void testProcessPacketEventSENDSavesResults() {
        when(telemetryStateService.getOrCreateState("test-token")).thenReturn(state);
        header.setPacketId((byte) 3);

        PacketEventData eventData = new PacketEventData();
        eventData.setEventStringCode("SEND");
        mockedEventData.when(() -> PacketEventData.fromByteBuffer(buffer, header)).thenReturn(eventData);

        telemetryPacketProcessor.processPacket("test-token", header, buffer);

        verify(telemetryResultsService).saveResultsFromLiveState(state, header.getSessionUID());
    }

    @Test
    public void testProcessPacketEventDRS() {
        when(telemetryStateService.getOrCreateState("test-token")).thenReturn(state);
        header.setPacketId((byte) 3);

        PacketEventData eventData = new PacketEventData();
        eventData.setEventStringCode("DRSE");
        mockedEventData.when(() -> PacketEventData.fromByteBuffer(buffer, header)).thenReturn(eventData);

        telemetryPacketProcessor.processPacket("test-token", header, buffer);

        assertTrue(state.isDrsEnabled());
        verify(liveDashboardService).broadcastSessionInfo(state);
    }

    @Test
    public void testProcessPacketParticipantsAutoDiscover() {
        when(telemetryStateService.getOrCreateState("test-token")).thenReturn(state);
        header.setPacketId((byte) 4);

        League league = new League();
        league.setId(1L);
        when(leagueRepository.findById(1L)).thenReturn(Optional.of(league));

        Tier tier = new Tier();
        tier.setId(10L);
        when(tierRepository.findById(10L)).thenReturn(Optional.of(tier));

        PacketParticipantsData participants = new PacketParticipantsData();
        ParticipantData p = new ParticipantData();
        p.setName("Lewis");
        p.setRaceNumber((short) 44);
        p.setDriverId((byte) 1);
        p.setNationality((byte) 1); // UK
        p.setAiControlled((byte) 0);
        participants.getParticipants().add(p);

        mockedParticipantsData.when(() -> PacketParticipantsData.fromByteBuffer(buffer, header)).thenReturn(participants);
        when(driverMappingRepository.findByLeagueAndTelemetryNameAndRaceNumberAndDriverIdAndCountry(
                eq(league), eq("Lewis"), eq(44), eq(1), eq("American")
        )).thenReturn(Optional.empty()); // New driver mapping!

        telemetryPacketProcessor.processPacket("test-token", header, buffer);

        verify(driverMappingRepository).save(any(DriverMapping.class));
        assertTrue(state.getIsHuman()[0]);
    }

    @Test
    public void testProcessPacketFinalClassification() {
        when(telemetryStateService.getOrCreateState("test-token")).thenReturn(state);
        header.setPacketId((byte) 8);

        PacketFinalClassificationData classification = new PacketFinalClassificationData();
        mockedFinalClassificationData.when(() -> PacketFinalClassificationData.fromByteBuffer(buffer, header)).thenReturn(classification);

        telemetryPacketProcessor.processPacket("test-token", header, buffer);

        verify(telemetryResultsService).handleFinalClassification(state, classification);
        verify(telemetryStateService).clearState(10L); // Cleared by tier ID after save
    }

    @Test
    public void testProcessPacketSessionHistory() {
        when(telemetryStateService.getOrCreateState("test-token")).thenReturn(state);
        header.setPacketId((byte) 11);

        PacketSessionHistoryData history = new PacketSessionHistoryData();
        history.setCarIdx(2);
        history.setNumLaps(3);
        history.setBestLapTimeLapNum(2);
        history.setBestSector1LapNum(1);
        history.setBestSector2LapNum(2);
        history.setBestSector3LapNum(2);

        LapHistoryData lap1 = new LapHistoryData();
        lap1.setLapTimeInMS(95000L);
        lap1.setSector1TimeMinutesPart(0);
        lap1.setSector1TimeMSPart(32000);
        lap1.setSector2TimeMinutesPart(0);
        lap1.setSector2TimeMSPart(42000);
        lap1.setSector3TimeMinutesPart(0);
        lap1.setSector3TimeMSPart(21000);
        lap1.setLapValidBitFlags(0x0F); // all valid

        LapHistoryData lap2 = new LapHistoryData();
        lap2.setLapTimeInMS(90000L);
        lap2.setSector1TimeMinutesPart(0);
        lap2.setSector1TimeMSPart(30000);
        lap2.setSector2TimeMinutesPart(0);
        lap2.setSector2TimeMSPart(40000);
        lap2.setSector3TimeMinutesPart(0);
        lap2.setSector3TimeMSPart(20000);
        lap2.setLapValidBitFlags(0x0F); // all valid

        LapHistoryData lap3 = new LapHistoryData();
        lap3.setLapTimeInMS(92000L);
        lap3.setLapValidBitFlags(0x0F);

        history.getLapHistoryData().add(lap1);
        history.getLapHistoryData().add(lap2);
        history.getLapHistoryData().add(lap3);

        mockedSessionHistoryData.when(() -> PacketSessionHistoryData.fromByteBuffer(buffer, header)).thenReturn(history);

        telemetryPacketProcessor.processPacket("test-token", header, buffer);

        assertEquals(90000L, state.getDriverBestLap()[2]);
        assertEquals(32000L, state.getDriverBestS1()[2]); // lap 1 has best sector 1 in history (bestSector1LapNum=1)
        assertEquals(40000L, state.getDriverBestS2()[2]); // lap 2 has best sector 2 in history
        assertEquals(20000L, state.getDriverBestS3()[2]); // lap 2 has best sector 3 in history

        assertEquals(90000L, state.getSessionBestLap());
        assertEquals(32000L, state.getSessionBestS1());
        assertEquals(40000L, state.getSessionBestS2());
        assertEquals(20000L, state.getSessionBestS3());

        verify(liveDashboardService).broadcastLeaderboard(state);
    }
}
