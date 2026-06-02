package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class LiveDashboardServiceTest {

    @InjectMocks
    private LiveDashboardService liveDashboardService;

    private LeagueSessionState state;

    @BeforeEach
    public void setUp() {
        state = new LeagueSessionState(1L);
        state.setTierId(10L);
    }

    @Test
    public void testFormatTime() {
        assertEquals("-", liveDashboardService.formatTime(0));
        assertEquals("-", liveDashboardService.formatTime(-100));
        assertEquals("+1.523s", liveDashboardService.formatTime(1523));
        assertEquals("+60.000s", liveDashboardService.formatTime(60000));
    }

    @Test
    public void testFormatLapTimeFull() {
        assertEquals("-", liveDashboardService.formatLapTimeFull(0));
        assertEquals("-", liveDashboardService.formatLapTimeFull(-50));
        assertEquals("1:30.123", liveDashboardService.formatLapTimeFull(90123));
        assertEquals("0:05.004", liveDashboardService.formatLapTimeFull(5004));
    }

    @Test
    public void testBuildSessionInfoNullSession() {
        assertNull(liveDashboardService.buildSessionInfo(state));
    }

    @Test
    public void testBuildSessionInfoSuccess() {
        PacketSessionData session = new PacketSessionData();
        session.setSessionType((byte) 15); // Race
        session.setTotalLaps((byte) 50);
        session.setSessionTimeLeft(3600);
        session.setSafetyCarStatus((byte) 1); // Full safety car
        session.setWeather((byte) 0); // Clear
        session.setAirTemperature((byte) 25);
        session.setTrackTemperature((byte) 38);

        PacketHeader header = new PacketHeader();
        header.setPlayerCarIndex((byte) 0);
        session.setHeader(header);

        state.setCurrentSession(session);
        state.setDrsEnabled(true);

        PacketLapData lapData = new PacketLapData();
        LapData playerLap = new LapData();
        playerLap.setCurrentLapNum((byte) 12);
        lapData.getLapData().add(playerLap);
        state.setCurrentLapData(lapData);

        SessionInfo info = liveDashboardService.buildSessionInfo(state);

        assertNotNull(info);
        assertEquals("Race", info.getSessionType());
        assertEquals(12, info.getCurrentLap());
        assertEquals(50, info.getTotalLaps());
        assertEquals(3600, info.getTimeLeftSeconds());
        assertTrue(info.isRace());
        assertEquals(1, info.getSafetyCarStatus());
        assertTrue(info.isDrsEnabled());
        assertEquals(0, info.getWeather());
        assertEquals(25, info.getAirTemperature());
        assertEquals(38, info.getTrackTemperature());
    }

    @Test
    public void testBuildLeaderboardNullComponents() {
        assertNull(liveDashboardService.buildLeaderboard(state));
    }

    @Test
    public void testBuildLeaderboardRaceSuccess() {
        state.setShowErs(true);
        state.setShowTyreWear(true);
        state.setHideAi(false);

        // 1. Session Setup
        PacketSessionData session = new PacketSessionData();
        session.setSessionType((byte) 15); // Race
        state.setCurrentSession(session);

        // 2. Participants
        PacketParticipantsData participants = new PacketParticipantsData();
        ParticipantData p1 = new ParticipantData();
        p1.setName("Driver 1");
        p1.setRaceNumber((short) 44);
        p1.setNationality((byte) 1); // UK
        p1.setTeamId((byte) 0); // Mercedes
        p1.setAiControlled((byte) 0);
        participants.getParticipants().add(p1);

        ParticipantData p2 = new ParticipantData();
        p2.setName("Driver 2 (AI)");
        p2.setRaceNumber((short) 1);
        p2.setNationality((byte) 3); // France
        p2.setTeamId((byte) 1); // Red Bull
        p2.setAiControlled((byte) 1);
        participants.getParticipants().add(p2);
        state.setCurrentParticipants(participants);

        // 3. Lap Data
        PacketLapData lapData = new PacketLapData();
        LapData ld1 = new LapData();
        ld1.setCarPosition((byte) 2);
        ld1.setNumPitStops((byte) 1);
        ld1.setPenalties((byte) 3);
        ld1.setTotalWarnings((byte) 2);
        ld1.setDeltaToRaceLeaderMinutesPart(0);
        ld1.setDeltaToRaceLeaderMSPart(1500);
        ld1.setDeltaToCarInFrontMinutesPart(0);
        ld1.setDeltaToCarInFrontMSPart(1500);
        lapData.getLapData().add(ld1);

        LapData ld2 = new LapData();
        ld2.setCarPosition((byte) 1);
        ld2.setNumPitStops((byte) 0);
        ld2.setPenalties((byte) 0);
        ld2.setTotalWarnings((byte) 0);
        ld2.setDeltaToRaceLeaderMinutesPart(0);
        ld2.setDeltaToRaceLeaderMSPart(0);
        ld2.setDeltaToCarInFrontMinutesPart(0);
        ld2.setDeltaToCarInFrontMSPart(0);
        lapData.getLapData().add(ld2);
        state.setCurrentLapData(lapData);

        // 4. Car Status
        PacketCarStatusData carStatus = new PacketCarStatusData();
        CarStatusData csd1 = new CarStatusData();
        csd1.setVisualTyreCompound((byte) 16); // Soft
        csd1.setTyresAgeLaps((byte) 5);
        csd1.setErsStoreEnergy(2000000.0f); // 50%
        csd1.setErsDeployMode((byte) 3); // Overtake active
        carStatus.getCarStatusData().add(csd1);

        CarStatusData csd2 = new CarStatusData();
        csd2.setVisualTyreCompound((byte) 17); // Medium
        csd2.setTyresAgeLaps((byte) 10);
        csd2.setErsStoreEnergy(4000000.0f); // 100%
        csd2.setErsDeployMode((byte) 1);
        carStatus.getCarStatusData().add(csd2);
        state.setCurrentCarStatus(carStatus);

        // 5. Car Damage (for Tyre Wear)
        PacketCarDamageData carDamage = new PacketCarDamageData();
        CarDamageData cdd1 = new CarDamageData();
        cdd1.getTyresWear()[0] = 12.0f;
        cdd1.getTyresWear()[1] = 15.0f;
        cdd1.getTyresWear()[2] = 8.0f;
        cdd1.getTyresWear()[3] = 9.0f;
        carDamage.getCarDamageData().add(cdd1);

        CarDamageData cdd2 = new CarDamageData();
        cdd2.getTyresWear()[0] = 5.0f;
        carDamage.getCarDamageData().add(cdd2);
        state.setCurrentCarDamageData(carDamage);

        // Run
        List<DriverBoardState> board = liveDashboardService.buildLeaderboard(state);

        assertNotNull(board);
        assertEquals(2, board.size());

        // First place driver (ld2 / p2)
        DriverBoardState first = board.get(0);
        assertEquals("Driver 2 (AI)", first.getName());
        assertEquals(1, first.getPosition());
        assertTrue(first.isAi());
        assertEquals("-", first.getGapToLeader());

        // Second place driver (ld1 / p1)
        DriverBoardState second = board.get(1);
        assertEquals("Driver 1", second.getName());
        assertEquals(2, second.getPosition());
        assertFalse(second.isAi());
        assertEquals("+1.500s", second.getGapToLeader());
        assertEquals("Soft", second.getTyreCompound());
        assertEquals(5, second.getTyreAge());
        assertEquals(1, second.getPitStops());
        assertEquals(3, second.getPenalties());
        assertEquals(2, second.getWarnings());
        assertEquals(50, second.getErsPercentage());
        assertTrue(second.isErsActive());
        assertEquals(15, second.getTyreWear()); // Max wear of 15%
    }

    @Test
    public void testBuildLeaderboardHideAi() {
        state.setHideAi(true);

        PacketSessionData session = new PacketSessionData();
        session.setSessionType((byte) 15);
        state.setCurrentSession(session);

        PacketParticipantsData participants = new PacketParticipantsData();
        ParticipantData p1 = new ParticipantData();
        p1.setName("Human");
        p1.setAiControlled((byte) 0);
        participants.getParticipants().add(p1);

        ParticipantData p2 = new ParticipantData();
        p2.setName("AI");
        p2.setAiControlled((byte) 1);
        participants.getParticipants().add(p2);
        state.setCurrentParticipants(participants);

        PacketLapData lapData = new PacketLapData();
        LapData ld1 = new LapData(); ld1.setCarPosition((byte) 1);
        lapData.getLapData().add(ld1);
        LapData ld2 = new LapData(); ld2.setCarPosition((byte) 2);
        lapData.getLapData().add(ld2);
        state.setCurrentLapData(lapData);

        PacketCarStatusData carStatus = new PacketCarStatusData();
        carStatus.getCarStatusData().add(new CarStatusData());
        carStatus.getCarStatusData().add(new CarStatusData());
        state.setCurrentCarStatus(carStatus);

        List<DriverBoardState> board = liveDashboardService.buildLeaderboard(state);

        assertNotNull(board);
        assertEquals(1, board.size());
        assertEquals("Human", board.get(0).getName());
    }
}
