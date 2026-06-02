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
}
