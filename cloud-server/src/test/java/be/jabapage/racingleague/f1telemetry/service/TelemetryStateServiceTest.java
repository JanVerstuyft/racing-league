package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.LiveState;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.model.*;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.repository.LiveStateRepository;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TelemetryStateServiceTest {

    @Mock
    private LiveStateRepository liveStateRepository;

    @Mock
    private TierRepository tierRepository;

    @Mock
    private LeagueRepository leagueRepository;

    @Mock
    private DriverMappingRepository driverMappingRepository;

    @Mock
    private Broadcaster broadcaster;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LiveDashboardService liveDashboardService;

    @InjectMocks
    private TelemetryStateService telemetryStateService;

    private League league;
    private Tier tier;

    @BeforeEach
    public void setUp() {
        league = new League();
        league.setId(1L);
        league.setHideAi(true);
        league.setShowTyreWear(true);
        league.setShowErs(true);

        tier = new Tier();
        tier.setId(10L);
        tier.setToken("test-token");
        tier.setLeague(league);
    }

    @Test
    public void testCompressAndDecompress() throws IOException {
        String testData = "Hello F1 Telemetry State Compression";
        byte[] compressed = telemetryStateService.compress(testData);
        assertNotNull(compressed);
        assertTrue(compressed.length > 0);

        String decompressed = telemetryStateService.decompress(compressed);
        assertEquals(testData, decompressed);
    }

    @Test
    public void testDecompressEmptyOrNull() throws IOException {
        assertEquals("", telemetryStateService.decompress(null));
        assertEquals("", telemetryStateService.decompress(new byte[0]));
    }

    @Test
    public void testGetOrCreateStateDefaultToken() {
        LeagueSessionState state = telemetryStateService.getOrCreateState("default");
        assertNotNull(state);
        assertEquals(-1L, state.getLeagueId());
    }

    @Test
    public void testGetOrCreateStateNewTokenNoRemoteState() {
        when(tierRepository.findByToken("test-token")).thenReturn(Optional.of(tier));
        when(liveStateRepository.findById(10L)).thenReturn(Optional.empty());
        when(driverMappingRepository.findByLeague(league)).thenReturn(Collections.emptyList());

        LeagueSessionState state = telemetryStateService.getOrCreateState("test-token");

        assertNotNull(state);
        assertEquals(10L, state.getTierId());
        assertEquals(1L, state.getLeagueId());
        assertTrue(state.isHideAi());
        assertTrue(state.isShowTyreWear());
        assertTrue(state.isShowErs());
        
        // Assert cached
        assertSame(state, telemetryStateService.getOrCreateState("test-token"));
    }

    @Test
    public void testGetOrCreateStateNewTokenWithRemoteState() throws Exception {
        when(tierRepository.findByToken("test-token")).thenReturn(Optional.of(tier));

        LeagueSessionState remoteState = new LeagueSessionState(1L);
        remoteState.setTierId(10L);
        remoteState.setCurrentSessionUID(12345L);

        String json = "{\"leagueId\":1,\"tierId\":10}";
        byte[] compressed = telemetryStateService.compress(json);

        LiveState liveState = new LiveState();
        liveState.setTierId(10L);
        liveState.setCompressedState(compressed);
        liveState.setLastUpdated(LocalDateTime.now());

        when(liveStateRepository.findById(10L)).thenReturn(Optional.of(liveState));
        when(objectMapper.readValue(json, LeagueSessionState.class)).thenReturn(remoteState);
        when(driverMappingRepository.findByLeague(league)).thenReturn(Collections.emptyList());

        LeagueSessionState state = telemetryStateService.getOrCreateState("test-token");

        assertNotNull(state);
        assertEquals(10L, state.getTierId());
        assertEquals(12345L, state.getCurrentSessionUID());
    }

    @Test
    public void testClearState() {
        telemetryStateService.clearState(10L);
        verify(liveStateRepository).deleteById(10L);
    }

    @Test
    public void testRefreshDriverMappings() {
        LeagueSessionState state = new LeagueSessionState(1L);
        state.setTierId(10L);

        DriverMapping dm = new DriverMapping();
        dm.setTelemetryName("Max");
        dm.setRaceNumber(1);
        dm.setDriverId(2);
        dm.setCountry("Netherlands");
        dm.setOverriddenName("Super Max");
        dm.setReserve(true);

        when(driverMappingRepository.findByLeague(league)).thenReturn(Collections.singletonList(dm));

        telemetryStateService.refreshDriverMappings(state, league);

        assertEquals("Super Max", state.getDriverNameOverrides().get("Max|1|2|Netherlands"));
        assertTrue(state.getReserveDrivers().contains("Max|1|2|Netherlands"));
    }

    @Test
    public void testPerformAsyncSave() throws Exception {
        LeagueSessionState state = new LeagueSessionState(1L);
        state.setTierId(10L);

        String mockJson = "{}";
        when(objectMapper.writeValueAsString(state)).thenReturn(mockJson);

        telemetryStateService.performAsyncSave(state);

        verify(liveStateRepository).save(any(LiveState.class));
    }

    @Test
    public void testSyncDistributedStateCleanupInactive() {
        // Cache an inactive state (packet received > 2 minutes ago)
        LeagueSessionState state = new LeagueSessionState(1L);
        state.setTierId(10L);
        state.setLastPacketTime(System.currentTimeMillis() - 130000); // 130 seconds ago
        telemetryStateService.getLeagueStates().put("test-token", state);

        telemetryStateService.syncDistributedState();

        // Should be removed from cache
        assertFalse(telemetryStateService.getLeagueStates().containsKey("test-token"));
    }

    @Test
    public void testSyncDistributedStateOutdatedRemoteStateDoesNotOverwriteLocal() throws Exception {
        // Setup local cached state
        LeagueSessionState localState = new LeagueSessionState(1L);
        localState.setTierId(10L);
        localState.setLastPacketTime(System.currentTimeMillis());
        localState.setCurrentSessionUID(5555L);
        telemetryStateService.getLeagueStates().put("test-token", localState);

        // Put a newer local update timestamp
        LocalDateTime now = LocalDateTime.now();
        telemetryStateService.syncDistributedState(); // Warm up sync (populates active set)

        // Remote state with an OLDER timestamp
        LiveState remote = new LiveState();
        remote.setTierId(10L);
        remote.setLastUpdated(now.minusMinutes(5));
        when(liveStateRepository.findAllById(anySet())).thenReturn(Collections.singletonList(remote));

        telemetryStateService.syncDistributedState();

        // Local state should remain untouched
        assertEquals(5555L, localState.getCurrentSessionUID());
        verifyNoInteractions(objectMapper);
    }

    @Test
    public void testSyncDistributedStateMergeMissingSessionAndParticipants() throws Exception {
        // Setup local cached state with missing session/participants
        LeagueSessionState localState = new LeagueSessionState(1L);
        localState.setTierId(10L);
        localState.setLastPacketTime(System.currentTimeMillis()); // active
        assertNull(localState.getCurrentSession());
        assertNull(localState.getCurrentParticipants());
        telemetryStateService.getLeagueStates().put("test-token", localState);

        // Setup remote state containing the missing session/participants
        LeagueSessionState remoteState = new LeagueSessionState(1L);
        remoteState.setTierId(10L);
        PacketSessionData session = new PacketSessionData();
        PacketParticipantsData participants = new PacketParticipantsData();
        remoteState.setCurrentSession(session);
        remoteState.setCurrentParticipants(participants);

        String json = "{}";
        byte[] compressed = telemetryStateService.compress(json);

        LiveState remote = new LiveState();
        remote.setTierId(10L);
        remote.setCompressedState(compressed);
        remote.setLastUpdated(LocalDateTime.now().plusSeconds(10)); // newer

        when(liveStateRepository.findAllById(anySet())).thenReturn(Collections.singletonList(remote));
        when(objectMapper.readValue(any(String.class), eq(LeagueSessionState.class))).thenReturn(remoteState);
        when(tierRepository.findById(10L)).thenReturn(Optional.of(tier));

        telemetryStateService.syncDistributedState();

        // Check merged session & participants
        assertNotNull(localState.getCurrentSession());
        assertNotNull(localState.getCurrentParticipants());
    }

    @Test
    public void testLoadAndBroadcastWhenHasListeners() throws Exception {
        // Active tier is not in local memory, but has listeners
        when(broadcaster.getActiveTierIds()).thenReturn(Collections.singleton(10L));
        when(broadcaster.hasListeners(10L)).thenReturn(true);

        LeagueSessionState remoteState = new LeagueSessionState(1L);
        remoteState.setTierId(10L);

        String json = "{}";
        byte[] compressed = telemetryStateService.compress(json);

        LiveState remote = new LiveState();
        remote.setTierId(10L);
        remote.setCompressedState(compressed);
        remote.setLastUpdated(LocalDateTime.now().plusSeconds(10));

        when(liveStateRepository.findAllById(anySet())).thenReturn(Collections.singletonList(remote));
        when(objectMapper.readValue(any(String.class), eq(LeagueSessionState.class))).thenReturn(remoteState);
        when(tierRepository.findById(10L)).thenReturn(Optional.of(tier));
        when(driverMappingRepository.findByLeague(league)).thenReturn(Collections.emptyList());

        telemetryStateService.syncDistributedState();

        // Should broadcast to listeners
        verify(broadcaster).broadcastLeaderboard(eq(10L), anyList());
        verify(broadcaster).broadcastSessionInfo(eq(10L), any());
    }
}
