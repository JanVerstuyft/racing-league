package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.*;
import be.jabapage.racingleague.f1telemetry.model.ConsistencyStats;
import be.jabapage.racingleague.f1telemetry.model.LongestStintStats;
import be.jabapage.racingleague.f1telemetry.model.RacePaceStats;
import be.jabapage.racingleague.f1telemetry.model.PacketParticipantsData;
import be.jabapage.racingleague.f1telemetry.model.ParticipantData;
import be.jabapage.racingleague.f1telemetry.repository.SessionResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.DriverResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.LapResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TelemetryProcessingServiceTest {

    @Mock
    private SessionResultRepository sessionResultRepository;

    @Mock
    private DriverResultRepository driverResultRepository;

    @Mock
    private LapResultRepository lapResultRepository;

    @InjectMocks
    private RaceAnalyticsService raceAnalyticsService;

    @InjectMocks
    private TelemetryResultsService telemetryResultsService;

    private SessionResult sessionResult;
    private League league;
    private Tier tier;

    @BeforeEach
    public void setUp() {
        league = new League();
        league.setId(1L);
        league.setMinLapsPct(50);

        tier = new Tier();
        tier.setId(1L);
        tier.setLeague(league);

        sessionResult = new SessionResult();
        sessionResult.setId(1L);
        sessionResult.setTier(tier);
    }

    @Test
    public void testCalculateGapsRaceSameLap() {
        sessionResult.setSessionType(15); // Race

        DriverResult winner = new DriverResult();
        winner.setPosition(1);
        winner.setNumLaps(10);
        winner.setTotalTime(600.0);

        DriverResult p2 = new DriverResult();
        p2.setPosition(2);
        p2.setNumLaps(10);
        p2.setTotalTime(601.523);

        sessionResult.setDriverResults(new LinkedHashSet<>(Arrays.asList(winner, p2)));

        telemetryResultsService.calculateGaps(sessionResult);

        assertEquals("Winner", winner.getGapToLeader());
        assertEquals("+1.523s", p2.getGapToLeader());
    }

    @Test
    public void testCalculateGapsRaceLapped() {
        sessionResult.setSessionType(15); // Race

        DriverResult winner = new DriverResult();
        winner.setPosition(1);
        winner.setNumLaps(10);
        winner.setTotalTime(600.0);

        DriverResult p2 = new DriverResult();
        p2.setPosition(2);
        p2.setNumLaps(9);
        p2.setTotalTime(605.0);

        DriverResult p3 = new DriverResult();
        p3.setPosition(3);
        p3.setNumLaps(8);
        p3.setTotalTime(610.0);

        sessionResult.setDriverResults(new LinkedHashSet<>(Arrays.asList(winner, p2, p3)));

        telemetryResultsService.calculateGaps(sessionResult);

        assertEquals("Winner", winner.getGapToLeader());
        assertEquals("+1 Lap", p2.getGapToLeader());
        assertEquals("+2 Laps", p3.getGapToLeader());
    }

    @Test
    public void testCalculateGapsQualifying() {
        sessionResult.setSessionType(5); // Qualifying 1

        DriverResult winner = new DriverResult();
        winner.setPosition(1);
        winner.setBestLapTime(90.0f);

        DriverResult p2 = new DriverResult();
        p2.setPosition(2);
        p2.setBestLapTime(90.543f);

        DriverResult p3 = new DriverResult();
        p3.setPosition(3);
        p3.setBestLapTime(0.0f); // Did not set a time

        sessionResult.setDriverResults(new LinkedHashSet<>(Arrays.asList(winner, p2, p3)));

        telemetryResultsService.calculateGaps(sessionResult);

        assertEquals("Pole", winner.getGapToLeader());
        assertEquals("+0.543s", p2.getGapToLeader());
        assertEquals("-", p3.getGapToLeader());
    }

    @Test
    public void testCalculatePureRacePace() {
        sessionResult.setSessionType(15);

        DriverResult dr = new DriverResult();
        dr.setDriverName("Driver 1");
        dr.setTeamId(0);
        dr.setCountry("Belgium");
        dr.setAi(false);

        // Add 30 laps (10 for each segment) to ensure non-zero weights in calculateWeightedSector
        for (int i = 1; i <= 30; i++) {
            LapResult lap = new LapResult();
            lap.setLapNumber(i);
            lap.setIsValid(true);
            lap.setS1InMS(30000L);
            lap.setS2InMS(40000L);
            lap.setS3InMS(20000L);
            lap.setLapTimeInMS(90000L);
            lap.setTyreCompound(16); // Soft
            dr.getLapResults().add(lap);
        }

        sessionResult.setDriverResults(new LinkedHashSet<>(Collections.singletonList(dr)));

        when(sessionResultRepository.findById(1L)).thenReturn(Optional.of(sessionResult));

        List<RacePaceStats> stats = raceAnalyticsService.calculatePureRacePace(1L);

        assertNotNull(stats);
        assertFalse(stats.isEmpty());
        assertEquals("Driver 1", stats.get(0).getDriverName());
        assertEquals(30.0, stats.get(0).getS1Pace());
        assertEquals(40.0, stats.get(0).getS2Pace());
        assertEquals(20.0, stats.get(0).getS3Pace());
        assertEquals(90.0, stats.get(0).getPureRacePace());
    }

    @Test
    public void testCalculateConsistency() {
        sessionResult.setSessionType(15);

        DriverResult dr = new DriverResult();
        dr.setDriverName("Driver 1");
        dr.setTeamId(0);
        dr.setCountry("Belgium");
        dr.setAi(false);

        // Needs at least 3 valid laps
        for (int i = 1; i <= 3; i++) {
            LapResult lap = new LapResult();
            lap.setLapNumber(i);
            lap.setIsValid(true);
            lap.setS1InMS(30000L);
            lap.setS2InMS(40000L);
            lap.setS3InMS(20000L);
            lap.setLapTimeInMS(90000L);
            dr.getLapResults().add(lap);
        }

        sessionResult.setDriverResults(new LinkedHashSet<>(Collections.singletonList(dr)));

        when(sessionResultRepository.findById(1L)).thenReturn(Optional.of(sessionResult));

        List<ConsistencyStats> stats = raceAnalyticsService.calculateConsistency(1L);

        assertNotNull(stats);
        assertFalse(stats.isEmpty());
        assertEquals("Driver 1", stats.get(0).getDriverName());
        assertEquals(100.0, stats.get(0).getRating()); // Only 1 driver so default max 100 rating
    }

    @Test
    public void testCalculateLongestStints() {
        sessionResult.setSessionType(15);

        DriverResult dr = new DriverResult();
        dr.setDriverName("Driver 1");
        dr.setTeamId(0);
        dr.setCountry("Belgium");
        dr.setAi(false);

        TyreStint stint = new TyreStint();
        stint.setStintOrder(0);
        stint.setTyreCompound(16); // Soft
        stint.setEndLap(10);
        stint.setLaps(10);
        dr.getTyreStints().add(stint);

        for (int i = 1; i <= 10; i++) {
            LapResult lap = new LapResult();
            lap.setLapNumber(i);
            lap.setIsValid(true);
            lap.setS1InMS(30000L);
            lap.setS2InMS(40000L);
            lap.setS3InMS(20000L);
            lap.setLapTimeInMS(90000L);
            dr.getLapResults().add(lap);
        }

        sessionResult.setDriverResults(new LinkedHashSet<>(Collections.singletonList(dr)));

        when(sessionResultRepository.findById(1L)).thenReturn(Optional.of(sessionResult));

        List<LongestStintStats> stats = raceAnalyticsService.calculateLongestStints(1L);

        assertNotNull(stats);
        assertFalse(stats.isEmpty());
        assertEquals("Driver 1", stats.get(0).getDriverName());
        assertEquals(10, stats.get(0).getLaps());
        assertEquals("Soft", stats.get(0).getTyreCompound());
    }

    @Test
    public void testGetTeamName() {
        // Test standard 2025 season mappings
        assertEquals("Mercedes", TelemetryProcessingService.getTeamName(0, 25));
        assertEquals("Sauber", TelemetryProcessingService.getTeamName(9, 25));
        assertEquals("Unknown (ID: 10)", TelemetryProcessingService.getTeamName(10, 25));

        // Test 2026 season mappings
        assertEquals("Mercedes", TelemetryProcessingService.getTeamName(476, 26));
        assertEquals("Mercedes", TelemetryProcessingService.getTeamName(220, 26));
        assertEquals("Audi", TelemetryProcessingService.getTeamName(485, 26));
        assertEquals("Audi", TelemetryProcessingService.getTeamName(229, 26));
        assertEquals("Cadillac", TelemetryProcessingService.getTeamName(486, 26));
        assertEquals("Cadillac", TelemetryProcessingService.getTeamName(230, 26));
        assertEquals("Unknown (ID: 0)", TelemetryProcessingService.getTeamName(0, 26));
    }

    @Test
    public void testDetectCarType() {
        // gameYear 26 is always F1 26
        assertEquals("F1 26", TelemetryProcessingService.detectCarType(null, 26));

        // gameYear 25 with no participants is F1 25
        assertEquals("F1 25", TelemetryProcessingService.detectCarType(null, 25));

        // gameYear 25 with participants containing teamId 486 (Cadillac) is F1 26
        PacketParticipantsData participants = new PacketParticipantsData();
        ParticipantData p = new ParticipantData();
        p.setTeamId(486);
        participants.getParticipants().add(p);
        assertEquals("F1 26", TelemetryProcessingService.detectCarType(participants, 25));

        // gameYear 25 with participants containing teamId 230 (Cadillac in uint8) is F1 26
        PacketParticipantsData participants3 = new PacketParticipantsData();
        ParticipantData p3 = new ParticipantData();
        p3.setTeamId(230);
        participants3.getParticipants().add(p3);
        assertEquals("F1 26", TelemetryProcessingService.detectCarType(participants3, 25));

        // gameYear 25 with name containing BORTOLETO is F1 26
        PacketParticipantsData participants2 = new PacketParticipantsData();
        ParticipantData p2 = new ParticipantData();
        p2.setName("Gabriel Bortoleto");
        participants2.getParticipants().add(p2);
        assertEquals("F1 26", TelemetryProcessingService.detectCarType(participants2, 25));
    }

    @Test
    public void testTrackNamesIncludesMadrid() {
        assertEquals("Madrid", TelemetryProcessingService.TRACK_NAMES.get(42));
    }
}
