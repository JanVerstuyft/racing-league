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
import java.util.stream.Collectors;

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

    @Autowired
    private DriverMappingRepository driverMappingRepository;

    @Autowired
    private ManualPenaltyRepository manualPenaltyRepository;

    @Autowired
    private EventLineupEntryRepository eventLineupEntryRepository;

    @org.junit.jupiter.api.BeforeEach
    public void cleanDatabase() {
        manualPenaltyRepository.deleteAll();
        eventLineupEntryRepository.deleteAll();
        driverStandingRepository.deleteAll();
        teamStandingRepository.deleteAll();
        lapResultRepository.deleteAll();
        driverResultRepository.deleteAll();
        sessionResultRepository.deleteAll();
        eventRepository.deleteAll();
        sessionPointConfigRepository.deleteAll();
        extraPointRuleRepository.deleteAll();
        driverMappingRepository.deleteAll();
        tierRepository.deleteAll();
        leagueRepository.deleteAll();
    }

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
        event.setFinalized(true);
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
        event.setFinalized(true);
        event = eventRepository.saveAndFlush(event);

        DriverMapping driver = new DriverMapping();
        driver.setLeague(league);
        driver.setTelemetryName("Del Driver");
        driver.setCountry("Belgium");
        driver = driverMappingRepository.saveAndFlush(driver);

        EventLineupEntry entry = new EventLineupEntry();
        entry.setEvent(event);
        entry.setDriver(driver);
        entry.setTeamId(1);
        entry.setCarType("F1 25");
        entry = eventLineupEntryRepository.saveAndFlush(entry);

        event.getLineupEntries().add(entry);
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
        assertTrue(eventLineupEntryRepository.findById(entry.getId()).isEmpty());
    }

    @Test
    public void testRecalculateStandingsWithManualPenalties() {
        // 1. Setup League & Tier
        League league = new League();
        league.setName("League Penalties Test");
        league.setMinLapsPct(50);
        league = leagueRepository.saveAndFlush(league);

        Tier tier = new Tier();
        tier.setName("Tier 1");
        tier.setToken("tier1-token-penalties");
        tier.setLeague(league);
        tier = tierRepository.saveAndFlush(tier);

        // 2. Setup Driver Mappings (needed for matching manual penalties)
        DriverMapping m1 = new DriverMapping();
        m1.setLeague(league);
        m1.setTelemetryName("Telemetry Driver 1");
        m1.setOverriddenName("Driver 1");
        m1.setRaceNumber(10);
        m1.setDriverId(1);
        m1.setCountry("United Kingdom");
        m1 = driverMappingRepository.saveAndFlush(m1);

        DriverMapping m2 = new DriverMapping();
        m2.setLeague(league);
        m2.setTelemetryName("Telemetry Driver 2");
        m2.setOverriddenName("Driver 2");
        m2.setRaceNumber(20);
        m2.setDriverId(2);
        m2.setCountry("Germany");
        m2 = driverMappingRepository.saveAndFlush(m2);

        // 3. Create Event & Session
        Event event = new Event();
        event.setEventName("Austrian GP");
        event.setTrackId("17");
        event.setTier(tier);
        event.setFinalized(true);
        event = eventRepository.saveAndFlush(event);

        SessionResult session = new SessionResult();
        session.setSessionType(15); // Race
        session.setSessionUID(12345678L);
        session.setEvent(event);
        session = sessionResultRepository.saveAndFlush(session);

        // Driver 1: Finishes 1st (Position 1), raw race time 1000.0s
        DriverResult d1 = new DriverResult();
        d1.setDriverName("Driver 1");
        d1.setTelemetryName("Telemetry Driver 1");
        d1.setRaceNumber(10);
        d1.setDriverId(1);
        d1.setCountry("United Kingdom");
        d1.setPosition(1);
        d1.setRawPosition(1);
        d1.setTotalTime(1000.0);
        d1.setRawTotalTime(1000.0);
        d1.setSessionResult(session);
        d1.setResultStatus(3);
        d1.setPenalties(0);
        d1.setWarnings(0);
        d1 = driverResultRepository.saveAndFlush(d1);

        // Driver 2: Finishes 2nd (Position 2), raw race time 1002.0s
        DriverResult d2 = new DriverResult();
        d2.setDriverName("Driver 2");
        d2.setTelemetryName("Telemetry Driver 2");
        d2.setRaceNumber(20);
        d2.setDriverId(2);
        d2.setCountry("Germany");
        d2.setPosition(2);
        d2.setRawPosition(2);
        d2.setTotalTime(1002.0);
        d2.setRawTotalTime(1002.0);
        d2.setSessionResult(session);
        d2.setResultStatus(3);
        d2.setPenalties(0);
        d2.setWarnings(0);
        d2 = driverResultRepository.saveAndFlush(d2);

        session.getDriverResults().add(d1);
        session.getDriverResults().add(d2);
        session = sessionResultRepository.saveAndFlush(session);
        event.getSessionResults().add(session);
        event = eventRepository.saveAndFlush(event);

        // 4. Save Point Configs: position 1 gets 25, position 2 gets 18
        SessionPointConfig c1 = new SessionPointConfig();
        c1.setLeague(league);
        c1.setSessionType(15);
        c1.setPosition(1);
        c1.setPoints(25);
        sessionPointConfigRepository.saveAndFlush(c1);

        SessionPointConfig c2 = new SessionPointConfig();
        c2.setLeague(league);
        c2.setSessionType(15);
        c2.setPosition(2);
        c2.setPoints(18);
        sessionPointConfigRepository.saveAndFlush(c2);

        // 5. Setup manual penalties:
        // - Driver 1 gets a +5s penalty (their total time becomes 1005.0s, dropping behind Driver 2's 1002.0s)
        // - Driver 2 gets a 3-point deduction
        ManualPenalty p1 = new ManualPenalty();
        p1.setSessionResult(session);
        p1.setDriverMapping(m1);
        p1.setSeconds(5);
        p1.setComment("Track limits");
        manualPenaltyRepository.saveAndFlush(p1);

        ManualPenalty p2 = new ManualPenalty();
        p2.setSessionResult(session);
        p2.setDriverMapping(m2);
        p2.setPointDeduction(3);
        p2.setComment("Dangerous driving");
        manualPenaltyRepository.saveAndFlush(p2);

        // 6. Recalculate standings
        telemetryResultsService.recalculateStandings(tier.getId());

        // 7. Verify positions, total times, points awarded, and standings
        final Long sessionId = session.getId();
        List<DriverResult> updatedResults = driverResultRepository.findAll().stream()
                .filter(dr -> dr.getSessionResult().getId().equals(sessionId))
                .collect(Collectors.toList());
        
        DriverResult updatedD1 = updatedResults.stream().filter(dr -> dr.getDriverName().equals("Driver 1")).findFirst().orElseThrow();
        DriverResult updatedD2 = updatedResults.stream().filter(dr -> dr.getDriverName().equals("Driver 2")).findFirst().orElseThrow();

        // Driver 1 should now be 2nd with 1005.0s and 18 points, with 5s stewards penalties and 0 point deductions
        assertEquals(2, updatedD1.getPosition());
        assertEquals(1005.0, updatedD1.getTotalTime());
        assertEquals(18, updatedD1.getPointsAwarded());
        assertEquals(5, updatedD1.getStewardsPenalties());
        assertEquals(0, updatedD1.getPointDeductions());

        // Driver 2 should now be 1st with 1002.0s and 25 - 3 = 22 points, with 0s stewards penalties and 3 point deductions
        assertEquals(1, updatedD2.getPosition());
        assertEquals(1002.0, updatedD2.getTotalTime());
        assertEquals(22, updatedD2.getPointsAwarded());
        assertEquals(0, updatedD2.getStewardsPenalties());
        assertEquals(3, updatedD2.getPointDeductions());

        // Standings verification
        DriverStanding sd1 = driverStandingRepository.findByTierAndDriverNameAndRaceNumberAndCountry(tier, "Driver 1", d1.getRaceNumber(), d1.getCountry()).orElseThrow();
        assertEquals(18, sd1.getPoints());

        DriverStanding sd2 = driverStandingRepository.findByTierAndDriverNameAndRaceNumberAndCountry(tier, "Driver 2", d2.getRaceNumber(), d2.getCountry()).orElseThrow();
        assertEquals(22, sd2.getPoints());
    }

    @Test
    public void testRecalculateStandingsWithDuplicateDriverMappingsInDifferentTiers() {
        // 1. Setup League
        League league = new League();
        league.setName("Duplicate Mappings League");
        league.setMinLapsPct(50);
        league = leagueRepository.saveAndFlush(league);

        // 2. Setup Tier 1 and Tier 2
        Tier tier1 = new Tier();
        tier1.setName("Tier 1");
        tier1.setToken("t1-tok");
        tier1.setLeague(league);
        tier1 = tierRepository.saveAndFlush(tier1);

        Tier tier2 = new Tier();
        tier2.setName("Tier 2");
        tier2.setToken("t2-tok");
        tier2.setLeague(league);
        tier2 = tierRepository.saveAndFlush(tier2);

        // 3. Create Duplicate Driver Mappings for telemetry name "Earbender1979", same race number, driver_id, and country.
        // mapping 1 is in Tier 1.
        DriverMapping m1 = new DriverMapping();
        m1.setLeague(league);
        m1.setTier(tier1);
        m1.setTelemetryName("Earbender1979");
        m1.setRaceNumber(79);
        m1.setDriverId(255);
        m1.setCountry("Belgian");
        m1 = driverMappingRepository.saveAndFlush(m1);

        // mapping 2 is in Tier 2.
        DriverMapping m2 = new DriverMapping();
        m2.setLeague(league);
        m2.setTier(tier2);
        m2.setTelemetryName("Earbender1979");
        m2.setRaceNumber(79);
        m2.setDriverId(255);
        m2.setCountry("Belgian");
        m2 = driverMappingRepository.saveAndFlush(m2);

        // 4. Create Event & Session in Tier 1
        Event event = new Event();
        event.setEventName("GP 1");
        event.setTrackId("17");
        event.setTier(tier1);
        event.setFinalized(true);
        event = eventRepository.saveAndFlush(event);

        SessionResult session = new SessionResult();
        session.setSessionType(15); // Race
        session.setSessionUID(111222L);
        session.setEvent(event);
        session.setTier(tier1);
        session = sessionResultRepository.saveAndFlush(session);

        DriverResult dr = new DriverResult();
        dr.setDriverName("Earbender1979");
        dr.setTelemetryName("Earbender1979");
        dr.setRaceNumber(79);
        dr.setDriverId(255);
        dr.setCountry("Belgian");
        dr.setPosition(1);
        dr.setRawPosition(1);
        dr.setTotalTime(1000.0);
        dr.setRawTotalTime(1000.0);
        dr.setSessionResult(session);
        dr.setResultStatus(3);
        dr.setPenalties(0);
        dr.setWarnings(0);
        final DriverResult savedDr = driverResultRepository.saveAndFlush(dr);

        session.getDriverResults().add(savedDr);
        session = sessionResultRepository.saveAndFlush(session);
        event.getSessionResults().add(session);
        event = eventRepository.saveAndFlush(event);

        // 5. Setup manual penalty linked to m1 (Tier 1 mapping)
        ManualPenalty penalty = new ManualPenalty();
        penalty.setSessionResult(session);
        penalty.setDriverMapping(m1);
        penalty.setSeconds(-10);
        penalty.setComment("Stewards correction");
        manualPenaltyRepository.saveAndFlush(penalty);

        // 6. Recalculate standings for Tier 1
        telemetryResultsService.recalculateStandings(tier1.getId());

        // 7. Verify penalty is applied: total time should be 990.0 and stewards penalty -10
        List<DriverResult> updatedResults = driverResultRepository.findAll();
        DriverResult updatedDr = updatedResults.stream()
                .filter(r -> r.getId().equals(savedDr.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(-10, updatedDr.getStewardsPenalties());
        assertEquals(990.0, updatedDr.getTotalTime());
    }

    @Test
    public void testHandleFinalClassificationOverwritesQualifyingSessionAndPreservesLapResults() {
        // 1. Setup League & Tier
        League league = new League();
        league.setName("Quali Overwrite League");
        league.setMinLapsPct(50);
        league = leagueRepository.saveAndFlush(league);

        Tier tier = new Tier();
        tier.setName("Tier 1");
        tier.setToken("tier1-quali-token");
        tier.setLeague(league);
        tier = tierRepository.saveAndFlush(tier);

        long sessionUID = 555666777L;

        // 2. Setup Participant & LapData state
        LeagueSessionState state = new LeagueSessionState();
        state.setTierId(tier.getId());
        state.setLeagueId(league.getId());
        state.setCurrentSessionUID(sessionUID);

        PacketSessionData sessionData = new PacketSessionData();
        sessionData.setTrackId((byte) 17); // Austria
        sessionData.setSessionType((byte) 5); // Q1
        sessionData.setHeader(new PacketHeader());
        sessionData.getHeader().setSessionUID(sessionUID);
        state.setCurrentSession(sessionData);

        PacketParticipantsData participantsData = new PacketParticipantsData();
        ParticipantData participant = new ParticipantData();
        participant.setName("Quali Driver");
        participant.setRaceNumber((short) 10);
        participant.setDriverId((byte) 1);
        participant.setNationality((byte) 1); 
        participant.setTeamId((byte) 0); 
        participant.setAiControlled((byte) 0);
        participantsData.getParticipants().add(participant);
        state.setCurrentParticipants(participantsData);

        PacketLapData lapData = new PacketLapData();
        LapData ld = new LapData();
        ld.setCarPosition((byte) 1);
        ld.setCurrentLapNum((byte) 2);
        ld.setResultStatus((byte) 2); // Active
        ld.setGridPosition((byte) 1);
        ld.setPenalties((byte) 0);
        ld.setTotalWarnings((byte) 0);
        lapData.getLapData().add(ld);
        state.setCurrentLapData(lapData);

        // Populate driver best lap in state
        state.getDriverBestLap()[0] = 75000L; // 75.0s

        // 3. Setup LapResult in database (saved during the live session)
        LapResult lap1 = new LapResult();
        lap1.setSessionUID(sessionUID);
        lap1.setCarIndex(0);
        lap1.setLapNumber(1);
        lap1.setLapTimeInMS(75000L);
        lap1.setS1InMS(25000L);
        lap1.setS2InMS(30000L);
        lap1.setS3InMS(20000L);
        lap1.setIsValid(true);
        lap1 = lapResultRepository.saveAndFlush(lap1);

        // 4. Save session results from live state (Q1)
        telemetryResultsService.saveResultsFromLiveState(state, sessionUID);

        // Verify it saved the session and associated the lap
        List<SessionResult> results = sessionResultRepository.findAll().stream()
                .filter(sr -> sr.getSessionUID() == sessionUID)
                .collect(Collectors.toList());
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).getDriverResults().size());
        DriverResult initialDr = results.get(0).getDriverResults().iterator().next();
        assertEquals(1, initialDr.getLapResults().size());

        // 5. Construct PacketFinalClassificationData for overwrite
        PacketFinalClassificationData classification = new PacketFinalClassificationData();
        classification.setHeader(new PacketHeader());
        classification.getHeader().setSessionUID(sessionUID);
        classification.setNumCars((byte) 1);

        FinalClassificationData classData = new FinalClassificationData();
        classData.setPosition((byte) 1);
        classData.setNumLaps((byte) 2);
        classData.setGridPosition((byte) 1);
        classData.setBestLapTimeInMS(74500L); // New faster lap!
        classData.setResultStatus((byte) 3); // Finished
        classData.setPenaltiesTime((byte) 0);
        classData.setNumTyreStints((byte) 1);
        classification.getClassificationData().add(classData);

        // 6. Overwrite the session results using handleFinalClassification
        assertDoesNotThrow(() -> {
            telemetryResultsService.handleFinalClassification(state, classification);
        });

        // 7. Verify session result was updated, driver result best lap is updated, and LapResult is STILL intact and linked!
        List<SessionResult> overwrittenResults = sessionResultRepository.findAll().stream()
                .filter(sr -> sr.getSessionUID() == sessionUID)
                .collect(Collectors.toList());
        assertEquals(1, overwrittenResults.size());
        
        SessionResult savedSession = overwrittenResults.get(0);
        assertEquals(1, savedSession.getDriverResults().size());
        
        DriverResult dr = savedSession.getDriverResults().iterator().next();
        assertEquals("Quali Driver", dr.getDriverName());
        assertEquals(74.5f, dr.getBestLapTime()); // Best lap updated!
        
        // Assert that the lap result was NOT deleted and remains linked to the updated DriverResult
        assertEquals(1, dr.getLapResults().size());
        LapResult savedLap = dr.getLapResults().iterator().next();
        assertEquals(lap1.getId(), savedLap.getId());
        assertEquals(25000L, savedLap.getS1InMS());
        assertEquals(30000L, savedLap.getS2InMS());
        assertEquals(20000L, savedLap.getS3InMS());
    }

    @Test
    public void testRecalculateStandingsWithMultipleRaceSessionsAndIndependentManualPenalties() {
        // 1. Setup League & Tier
        League league = new League();
        league.setName("Multi Race penalties");
        league.setMinLapsPct(50);
        league = leagueRepository.saveAndFlush(league);

        Tier tier = new Tier();
        tier.setName("Tier 1");
        tier.setToken("tier1-multi-penalties");
        tier.setLeague(league);
        tier = tierRepository.saveAndFlush(tier);

        // 2. Setup Driver Mappings
        DriverMapping m1 = new DriverMapping();
        m1.setLeague(league);
        m1.setTelemetryName("Telemetry Driver 1");
        m1.setOverriddenName("Driver 1");
        m1.setRaceNumber(10);
        m1.setDriverId(1);
        m1.setCountry("United Kingdom");
        m1 = driverMappingRepository.saveAndFlush(m1);

        DriverMapping m2 = new DriverMapping();
        m2.setLeague(league);
        m2.setTelemetryName("Telemetry Driver 2");
        m2.setOverriddenName("Driver 2");
        m2.setRaceNumber(20);
        m2.setDriverId(2);
        m2.setCountry("Germany");
        m2 = driverMappingRepository.saveAndFlush(m2);

        // 3. Create Event
        Event event = new Event();
        event.setEventName("Austrian GP Weekend");
        event.setTrackId("17");
        event.setTier(tier);
        event.setFinalized(true);
        event = eventRepository.saveAndFlush(event);

        // 4. Create Main Race Session (Type 16) and Sprint Session (Type 15)
        SessionResult sessionMain = new SessionResult();
        sessionMain.setSessionType(16);
        sessionMain.setSessionUID(1234L);
        sessionMain.setEvent(event);
        sessionMain = sessionResultRepository.saveAndFlush(sessionMain);

        SessionResult sessionSprint = new SessionResult();
        sessionSprint.setSessionType(15);
        sessionSprint.setSessionUID(5678L);
        sessionSprint.setEvent(event);
        sessionSprint = sessionResultRepository.saveAndFlush(sessionSprint);

        // 5. Populate Driver Results for Main Session
        DriverResult drMain1 = new DriverResult();
        drMain1.setDriverName("Driver 1");
        drMain1.setTelemetryName("Telemetry Driver 1");
        drMain1.setRaceNumber(10);
        drMain1.setDriverId(1);
        drMain1.setCountry("United Kingdom");
        drMain1.setPosition(1);
        drMain1.setRawPosition(1);
        drMain1.setTotalTime(1000.0);
        drMain1.setRawTotalTime(1000.0);
        drMain1.setSessionResult(sessionMain);
        drMain1.setResultStatus(3);
        drMain1.setPenalties(0);
        drMain1.setWarnings(0);
        drMain1 = driverResultRepository.saveAndFlush(drMain1);

        DriverResult drMain2 = new DriverResult();
        drMain2.setDriverName("Driver 2");
        drMain2.setTelemetryName("Telemetry Driver 2");
        drMain2.setRaceNumber(20);
        drMain2.setDriverId(2);
        drMain2.setCountry("Germany");
        drMain2.setPosition(2);
        drMain2.setRawPosition(2);
        drMain2.setTotalTime(1002.0);
        drMain2.setRawTotalTime(1002.0);
        drMain2.setSessionResult(sessionMain);
        drMain2.setResultStatus(3);
        drMain2.setPenalties(0);
        drMain2.setWarnings(0);
        drMain2 = driverResultRepository.saveAndFlush(drMain2);

        sessionMain.getDriverResults().add(drMain1);
        sessionMain.getDriverResults().add(drMain2);
        sessionMain = sessionResultRepository.saveAndFlush(sessionMain);

        // 6. Populate Driver Results for Sprint Session
        DriverResult drSprint1 = new DriverResult();
        drSprint1.setDriverName("Driver 1");
        drSprint1.setTelemetryName("Telemetry Driver 1");
        drSprint1.setRaceNumber(10);
        drSprint1.setDriverId(1);
        drSprint1.setCountry("United Kingdom");
        drSprint1.setPosition(1);
        drSprint1.setRawPosition(1);
        drSprint1.setTotalTime(500.0);
        drSprint1.setRawTotalTime(500.0);
        drSprint1.setSessionResult(sessionSprint);
        drSprint1.setResultStatus(3);
        drSprint1.setPenalties(0);
        drSprint1.setWarnings(0);
        drSprint1 = driverResultRepository.saveAndFlush(drSprint1);

        DriverResult drSprint2 = new DriverResult();
        drSprint2.setDriverName("Driver 2");
        drSprint2.setTelemetryName("Telemetry Driver 2");
        drSprint2.setRaceNumber(20);
        drSprint2.setDriverId(2);
        drSprint2.setCountry("Germany");
        drSprint2.setPosition(2);
        drSprint2.setRawPosition(2);
        drSprint2.setTotalTime(501.0);
        drSprint2.setRawTotalTime(501.0);
        drSprint2.setSessionResult(sessionSprint);
        drSprint2.setResultStatus(3);
        drSprint2.setPenalties(0);
        drSprint2.setWarnings(0);
        drSprint2 = driverResultRepository.saveAndFlush(drSprint2);

        sessionSprint.getDriverResults().add(drSprint1);
        sessionSprint.getDriverResults().add(drSprint2);
        sessionSprint = sessionResultRepository.saveAndFlush(sessionSprint);

        event.getSessionResults().add(sessionMain);
        event.getSessionResults().add(sessionSprint);
        event = eventRepository.saveAndFlush(event);

        // 7. Setup Point Configs
        // Main Race points: pos 1 gets 25, pos 2 gets 18
        SessionPointConfig cMain1 = new SessionPointConfig();
        cMain1.setLeague(league);
        cMain1.setSessionType(15);
        cMain1.setPosition(1);
        cMain1.setPoints(25);
        sessionPointConfigRepository.saveAndFlush(cMain1);

        SessionPointConfig cMain2 = new SessionPointConfig();
        cMain2.setLeague(league);
        cMain2.setSessionType(15);
        cMain2.setPosition(2);
        cMain2.setPoints(18);
        sessionPointConfigRepository.saveAndFlush(cMain2);

        // Sprint Race points: pos 1 gets 8, pos 2 gets 7
        SessionPointConfig cSprint1 = new SessionPointConfig();
        cSprint1.setLeague(league);
        cSprint1.setSessionType(19);
        cSprint1.setPosition(1);
        cSprint1.setPoints(8);
        sessionPointConfigRepository.saveAndFlush(cSprint1);

        SessionPointConfig cSprint2 = new SessionPointConfig();
        cSprint2.setLeague(league);
        cSprint2.setSessionType(19);
        cSprint2.setPosition(2);
        cSprint2.setPoints(7);
        sessionPointConfigRepository.saveAndFlush(cSprint2);

        // 8. Setup manual penalties:
        // - Driver 1 gets a +5s penalty in Main Race ONLY (drops to pos 2)
        // - Driver 2 gets a 2-point deduction in Sprint Race ONLY
        ManualPenalty pMain = new ManualPenalty();
        pMain.setSessionResult(sessionMain);
        pMain.setDriverMapping(m1);
        pMain.setSeconds(5);
        pMain.setComment("Main race track limits");
        manualPenaltyRepository.saveAndFlush(pMain);

        ManualPenalty pSprint = new ManualPenalty();
        pSprint.setSessionResult(sessionSprint);
        pSprint.setDriverMapping(m2);
        pSprint.setPointDeduction(2);
        pSprint.setComment("Sprint race shortcut");
        manualPenaltyRepository.saveAndFlush(pSprint);

        // 9. Recalculate standings
        telemetryResultsService.recalculateStandings(tier.getId());

        // 10. Verify results
        final Long sMainId = sessionMain.getId();
        final Long sSprintId = sessionSprint.getId();
        
        List<DriverResult> mainResults = driverResultRepository.findAll().stream()
                .filter(dr -> dr.getSessionResult().getId().equals(sMainId))
                .collect(Collectors.toList());
        List<DriverResult> sprintResults = driverResultRepository.findAll().stream()
                .filter(dr -> dr.getSessionResult().getId().equals(sSprintId))
                .collect(Collectors.toList());

        DriverResult updatedMainD1 = mainResults.stream().filter(dr -> dr.getDriverName().equals("Driver 1")).findFirst().orElseThrow();
        DriverResult updatedMainD2 = mainResults.stream().filter(dr -> dr.getDriverName().equals("Driver 2")).findFirst().orElseThrow();
        DriverResult updatedSprintD1 = sprintResults.stream().filter(dr -> dr.getDriverName().equals("Driver 1")).findFirst().orElseThrow();
        DriverResult updatedSprintD2 = sprintResults.stream().filter(dr -> dr.getDriverName().equals("Driver 2")).findFirst().orElseThrow();

        // Main Race Verification:
        // Driver 1 (penalized +5s -> total time 1005.0, position 2, points 18, stewardsPenalties 5)
        assertEquals(2, updatedMainD1.getPosition());
        assertEquals(1005.0, updatedMainD1.getTotalTime());
        assertEquals(18, updatedMainD1.getPointsAwarded());
        assertEquals(5, updatedMainD1.getStewardsPenalties());
        
        // Driver 2 (unpenalized -> total time 1002.0, position 1, points 25, stewardsPenalties 0)
        assertEquals(1, updatedMainD2.getPosition());
        assertEquals(1002.0, updatedMainD2.getTotalTime());
        assertEquals(25, updatedMainD2.getPointsAwarded());
        assertEquals(0, updatedMainD2.getStewardsPenalties());

        // Sprint Race Verification:
        // Driver 1 (unpenalized -> position 1, points 8, stewardsPenalties 0, pointDeductions 0)
        assertEquals(1, updatedSprintD1.getPosition());
        assertEquals(500.0, updatedSprintD1.getTotalTime());
        assertEquals(8, updatedSprintD1.getPointsAwarded());
        assertEquals(0, updatedSprintD1.getStewardsPenalties());
        assertEquals(0, updatedSprintD1.getPointDeductions());

        // Driver 2 (penalized 2 PD -> position 2, points 7 - 2 = 5, stewardsPenalties 0, pointDeductions 2)
        assertEquals(2, updatedSprintD2.getPosition());
        assertEquals(501.0, updatedSprintD2.getTotalTime());
        assertEquals(5, updatedSprintD2.getPointsAwarded());
        assertEquals(0, updatedSprintD2.getStewardsPenalties());
        assertEquals(2, updatedSprintD2.getPointDeductions());

        // Standings Verification:
        // Driver 1: 18 (Main) + 8 (Sprint) = 26 points
        DriverStanding sd1 = driverStandingRepository.findByTierAndDriverNameAndRaceNumberAndCountry(tier, "Driver 1", m1.getRaceNumber(), m1.getCountry()).orElseThrow();
        assertEquals(26, sd1.getPoints());

        // Driver 2: 25 (Main) + 5 (Sprint) = 30 points
        DriverStanding sd2 = driverStandingRepository.findByTierAndDriverNameAndRaceNumberAndCountry(tier, "Driver 2", m2.getRaceNumber(), m2.getCountry()).orElseThrow();
        assertEquals(30, sd2.getPoints());
    }

    @Test
    public void testRecalculateStandingsWithProvisionalAndFinalizedEvents() {
        // 1. Setup League & Tier
        League league = new League();
        league.setName("Provisional League");
        league.setMinLapsPct(50);
        league = leagueRepository.saveAndFlush(league);

        Tier tier = new Tier();
        tier.setName("Tier 1");
        tier.setToken("tier1-prov");
        tier.setLeague(league);
        tier = tierRepository.saveAndFlush(tier);

        // 2. Setup Point Config: position 1 gets 25
        SessionPointConfig c1 = new SessionPointConfig();
        c1.setLeague(league);
        c1.setSessionType(15);
        c1.setPosition(1);
        c1.setPoints(25);
        sessionPointConfigRepository.saveAndFlush(c1);

        // 3. Create Event (Provisional by default)
        Event event = new Event();
        event.setEventName("Provisional GP");
        event.setTrackId("17");
        event.setTier(tier);
        event.setFinalized(false);
        event = eventRepository.saveAndFlush(event);

        // 4. Create SessionResult
        SessionResult session = new SessionResult();
        session.setSessionType(15);
        session.setSessionUID(99998888L);
        session.setEvent(event);
        session = sessionResultRepository.saveAndFlush(session);

        // Driver 1: Finishes 1st (Position 1), raw race time 1000.0s
        DriverResult d1 = new DriverResult();
        d1.setDriverName("Driver 1");
        d1.setPosition(1);
        d1.setSessionResult(session);
        d1.setResultStatus(3);
        d1.setPointsAwarded(25);
        d1.setPenalties(0);
        d1.setWarnings(0);
        d1 = driverResultRepository.saveAndFlush(d1);

        session.getDriverResults().add(d1);
        session = sessionResultRepository.saveAndFlush(session);
        event.getSessionResults().add(session);
        event = eventRepository.saveAndFlush(event);

        // 5. Recalculate standings -> Since the event is provisional, standings should be empty!
        telemetryResultsService.recalculateStandings(tier.getId());
        List<DriverStanding> standings = driverStandingRepository.findByTier(tier);
        assertTrue(standings.isEmpty(), "Standings should be empty for a provisional event");

        // 6. Mark the event as finalized and recalculate -> Standings should now have 1 driver with 25 points!
        event.setFinalized(true);
        eventRepository.saveAndFlush(event);
        telemetryResultsService.recalculateStandings(tier.getId());

        standings = driverStandingRepository.findByTier(tier);
        assertEquals(1, standings.size(), "Standings should have 1 entry when event is finalized");
        assertEquals(25, standings.get(0).getPoints(), "Driver 1 should have 25 points");

        // 7. Mark the event as provisional again and recalculate -> Standings should be empty again!
        event.setFinalized(false);
        eventRepository.saveAndFlush(event);
        telemetryResultsService.recalculateStandings(tier.getId());

        standings = driverStandingRepository.findByTier(tier);
        assertTrue(standings.isEmpty(), "Standings should be empty again after event is marked provisional");
    }
}
