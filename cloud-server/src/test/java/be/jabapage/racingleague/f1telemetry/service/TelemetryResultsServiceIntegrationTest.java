package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.*;
import be.jabapage.racingleague.f1telemetry.model.*;
import be.jabapage.racingleague.f1telemetry.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
public class TelemetryResultsServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.liquibase.enabled", () -> "false");
    }

    @Autowired
    private TelemetryResultsService telemetryResultsService;

    @Autowired
    private LeagueRepository leagueRepository;

    @Autowired
    private TierRepository tierRepository;

    @Autowired
    private SessionResultRepository sessionResultRepository;

    @Autowired
    private LapResultRepository lapResultRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private DriverStandingRepository driverStandingRepository;

    @Autowired
    private TeamStandingRepository teamStandingRepository;

    @Autowired
    private SessionPointConfigRepository sessionPointConfigRepository;

    @Autowired
    private ExtraPointRuleRepository extraPointRuleRepository;

    @Autowired
    private DriverResultRepository driverResultRepository;

    @Test
    public void testHandleFinalClassificationSavesSuccessfullyWithoutTransientPropertyValueException() {
        // 1. Setup League & Tier in the real database
        League league = new League();
        league.setName("F1 League");
        league.setMinLapsPct(50);
        league = leagueRepository.saveAndFlush(league);

        Tier tier = new Tier();
        tier.setName("Tier 1");
        tier.setToken("tier1-token");
        tier.setLeague(league);
        tier = tierRepository.saveAndFlush(tier);

        // 2. Setup existing LapResults in the database that will be linked to the DriverResults
        long sessionUID = 987654321L;
        
        LapResult lap1 = new LapResult();
        lap1.setSessionUID(sessionUID);
        lap1.setCarIndex(0);
        lap1.setLapNumber(1);
        lap1.setLapTimeInMS(90000L);
        lap1.setS1InMS(30000L);
        lap1.setS2InMS(40000L);
        lap1.setS3InMS(20000L);
        lap1.setIsValid(true);
        lap1.setTyreCompound(16);
        lap1 = lapResultRepository.saveAndFlush(lap1);

        // 3. Construct LeagueSessionState with current participants and session info
        LeagueSessionState state = new LeagueSessionState();
        state.setTierId(tier.getId());
        state.setLeagueId(league.getId());
        state.setCurrentSessionUID(sessionUID);

        PacketSessionData sessionData = new PacketSessionData();
        sessionData.setTrackId((byte) 17); // Austria
        sessionData.setSessionType((byte) 15); // Race
        sessionData.setHeader(new PacketHeader());
        sessionData.getHeader().setPlayerCarIndex((byte) 0);
        state.setCurrentSession(sessionData);

        PacketParticipantsData participantsData = new PacketParticipantsData();
        ParticipantData participant = new ParticipantData();
        participant.setName("Test Driver");
        participant.setRaceNumber((short) 44);
        participant.setDriverId((byte) 1);
        participant.setNationality((byte) 1); 
        participant.setTeamId((byte) 0); // Mercedes
        participant.setAiControlled((byte) 0);
        participantsData.getParticipants().add(participant);
        state.setCurrentParticipants(participantsData);

        // 4. Construct PacketFinalClassificationData (representing Packet 8)
        PacketFinalClassificationData classification = new PacketFinalClassificationData();
        classification.setHeader(new PacketHeader());
        classification.getHeader().setSessionUID(sessionUID);
        classification.setNumCars((byte) 1);

        FinalClassificationData classData = new FinalClassificationData();
        classData.setPosition((byte) 1);
        classData.setNumLaps((byte) 1);
        classData.setGridPosition((byte) 1);
        classData.setBestLapTimeInMS(90000L);
        classData.setTotalRaceTime(90.0);
        classData.setResultStatus((byte) 3); // Finished
        classData.setPenaltiesTime((byte) 0);
        classData.setNumTyreStints((byte) 1);
        classData.getTyreStintsVisual()[0] = 16;
        classData.getTyreStintsEndLaps()[0] = 1;
        classification.getClassificationData().add(classData);

        // 5. Trigger handleFinalClassification (this will run the real Hibernate code)
        assertDoesNotThrow(() -> {
            telemetryResultsService.handleFinalClassification(state, classification);
        });

        // 6. Verify that it saved the results and successfully linked the LapResult to DriverResult!
        List<SessionResult> results = sessionResultRepository.findAll();
        assertEquals(1, results.size());
        SessionResult savedSession = results.get(0);
        assertEquals(sessionUID, savedSession.getSessionUID());
        assertEquals(1, savedSession.getDriverResults().size());

        DriverResult dr = savedSession.getDriverResults().iterator().next();
        assertEquals("Test Driver", dr.getDriverName());
        assertEquals(1, dr.getLapResults().size());
        
        LapResult savedLap = dr.getLapResults().iterator().next();
        assertEquals(lap1.getId(), savedLap.getId());
        assertEquals(dr.getId(), savedLap.getDriverResult().getId());
    }

    @Test
    public void testRecalculateStandingsWithFastestLapPointsAwardedCorrectly() {
        // 1. Setup League & Tier
        League league = new League();
        league.setName("League 1");
        league.setMinLapsPct(50);
        league = leagueRepository.saveAndFlush(league);

        Tier tier = new Tier();
        tier.setName("Tier 1");
        tier.setToken("tier1-token-recalc");
        tier.setLeague(league);
        tier = tierRepository.saveAndFlush(tier);

        // 2. Setup custom Point Configs: position 1 gets 25, position 2 gets 18
        SessionPointConfig c1 = new SessionPointConfig();
        c1.setLeague(league);
        c1.setSessionType(15); // Race
        c1.setPosition(1);
        c1.setPoints(25);
        sessionPointConfigRepository.saveAndFlush(c1);

        SessionPointConfig c2 = new SessionPointConfig();
        c2.setLeague(league);
        c2.setSessionType(15); // Race
        c2.setPosition(2);
        c2.setPoints(18);
        sessionPointConfigRepository.saveAndFlush(c2);

        // Setup generic fastest lap rule
        ExtraPointRule rule = new ExtraPointRule();
        rule.setLeague(league);
        rule.setSessionType(15);
        rule.setRuleName("Fastest Lap");
        rule.setMetric(ExtraPointRule.Metric.FASTEST_LAP);
        rule.setMetricExpression(ExtraPointRule.Metric.FASTEST_LAP.getDefaultExpression());
        rule.setRuleType(ExtraPointRule.RuleType.LOWEST_VALUE);
        rule.setPoints(1);
        rule.setMustFinish(true);
        rule.setOnlyForPointScorers(true);
        rule.setExcludeAi(false); // test setup has isAi=false by default but let's be sure
        extraPointRuleRepository.saveAndFlush(rule);

        // 3. Create Event
        Event event = new Event();
        event.setEventName("Grand Prix");
        event.setTrackId("17");
        event.setTier(tier);
        event = eventRepository.saveAndFlush(event);

        // 4. Create SessionResult
        SessionResult session = new SessionResult();
        session.setSessionType(15);
        session.setSessionUID(11112222L);
        session.setEvent(event);
        session = sessionResultRepository.saveAndFlush(session);

        // Driver 1: Finishes 1st (Position 1), best lap time 90.0s (Scores 25 finish pts + 0 fastest lap)
        DriverResult d1 = new DriverResult();
        d1.setDriverName("Driver 1");
        d1.setPosition(1);
        d1.setBestLapTime(90.0f);
        d1.setSessionResult(session);
        d1.setResultStatus(3);
        d1.setPenalties(0);
        d1.setWarnings(0);
        d1 = driverResultRepository.saveAndFlush(d1);

        // Driver 2: Finishes 2nd (Position 2), best lap time 89.0s (Outright Fastest Lap! Scores 18 finish pts + 1 fastest lap = 19)
        DriverResult d2 = new DriverResult();
        d2.setDriverName("Driver 2");
        d2.setPosition(2);
        d2.setBestLapTime(89.0f);
        d2.setSessionResult(session);
        d2.setResultStatus(3);
        d2.setPenalties(0);
        d2.setWarnings(0);
        d2 = driverResultRepository.saveAndFlush(d2);

        // Driver 3: Finishes 11th (Position 11 - no finish points), best lap time 95.0s (Slower but finished 11th - ineligible for fastest lap pt!)
        DriverResult d3 = new DriverResult();
        d3.setDriverName("Driver 3");
        d3.setPosition(11);
        d3.setBestLapTime(95.0f);
        d3.setSessionResult(session);
        d3.setResultStatus(3);
        d3.setPenalties(0);
        d3.setWarnings(0);
        d3 = driverResultRepository.saveAndFlush(d3);

        session.getDriverResults().add(d1);
        session.getDriverResults().add(d2);
        session.getDriverResults().add(d3);
        session = sessionResultRepository.saveAndFlush(session);

        // Link event
        event.getSessionResults().add(session);
        eventRepository.saveAndFlush(event);

        // 5. Trigger recalculateStandings
        telemetryResultsService.recalculateStandings(tier.getId());

        // 6. Verify driver standings in database
        List<DriverStanding> standings = driverStandingRepository.findByTier(tier);
        assertEquals(3, standings.size()); // All 3 drivers get standings (some with 0 points)
        
        DriverStanding sd1 = driverStandingRepository.findByTierAndDriverNameAndRaceNumberAndCountry(tier, "Driver 1", d1.getRaceNumber(), d1.getCountry()).orElseThrow();
        assertEquals(25, sd1.getPoints()); // 25 points awarded

        DriverStanding sd2 = driverStandingRepository.findByTierAndDriverNameAndRaceNumberAndCountry(tier, "Driver 2", d2.getRaceNumber(), d2.getCountry()).orElseThrow();
        assertEquals(19, sd2.getPoints()); // 18 + 1 point awarded!
    }

    @Test
    public void testDeleteEventCascadesCleanly() {
        // 1. Setup League & Tier
        League league = new League();
        league.setName("League Del");
        league = leagueRepository.saveAndFlush(league);

        Tier tier = new Tier();
        tier.setName("Tier Del");
        tier.setToken("tier-del-token");
        tier.setLeague(league);
        tier = tierRepository.saveAndFlush(tier);

        // 2. Setup Event & Session
        Event event = new Event();
        event.setEventName("GP to Delete");
        event.setTier(tier);
        event = eventRepository.saveAndFlush(event);

        SessionResult session = new SessionResult();
        session.setSessionUID(88889999L);
        session.setSessionType(15);
        session.setEvent(event);
        session = sessionResultRepository.saveAndFlush(session);

        DriverResult dr = new DriverResult();
        dr.setDriverName("Driver Del");
        dr.setPosition(1);
        dr.setSessionResult(session);
        dr.setResultStatus(3);
        dr.setPenalties(0);
        dr.setWarnings(0);
        dr = driverResultRepository.saveAndFlush(dr);

        LapResult lap = new LapResult();
        lap.setSessionUID(88889999L);
        lap.setCarIndex(0);
        lap.setLapNumber(1);
        lap.setDriverResult(dr);
        lap = lapResultRepository.saveAndFlush(lap);

        dr.getLapResults().add(lap);
        dr = driverResultRepository.saveAndFlush(dr);

        session.getDriverResults().add(dr);
        session = sessionResultRepository.saveAndFlush(session);

        event.getSessionResults().add(session);
        event = eventRepository.saveAndFlush(event);

        // Recalculate standings to save standing rows
        telemetryResultsService.recalculateStandings(tier.getId());
        assertFalse(driverStandingRepository.findByTier(tier).isEmpty());

        // 3. Delete Event
        telemetryResultsService.deleteEvent(event.getId());

        // 4. Assert all session results, driver results, and lap results are cascade-deleted!
        assertFalse(eventRepository.findById(event.getId()).isPresent());
        assertTrue(sessionResultRepository.findBySessionUID(88889999L).isEmpty());
        assertTrue(driverResultRepository.findAll().stream().filter(d -> d.getDriverName().equals("Driver Del")).findFirst().isEmpty());
        assertTrue(lapResultRepository.findBySessionUID(88889999L).isEmpty());
    }
}
