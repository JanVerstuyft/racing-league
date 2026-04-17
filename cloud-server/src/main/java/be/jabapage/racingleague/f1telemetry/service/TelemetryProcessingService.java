package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.*;
import be.jabapage.racingleague.f1telemetry.model.*;
import be.jabapage.racingleague.f1telemetry.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.util.*;

@Slf4j
@Service
public class TelemetryProcessingService {

    @Autowired
    private LeagueRepository leagueRepository;
    @Autowired
    private SessionResultRepository sessionResultRepository;
    @Autowired
    private DriverStandingRepository driverStandingRepository;
    @Autowired
    private TeamStandingRepository teamStandingRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private Broadcaster broadcaster;

    private Long activeLeagueId;
    private PacketSessionData currentSession;
    private PacketParticipantsData currentParticipants;
    private PacketLapData currentLapData;
    private PacketCarStatusData currentCarStatus;
    
    // Team ID to Name mapping (simplified)
    private static final Map<Integer, String> TEAM_NAMES = Map.of(
            0, "Mercedes", 1, "Ferrari", 2, "Red Bull Racing", 3, "Williams",
            4, "Aston Martin", 5, "Alpine", 6, "RB", 7, "Haas",
            8, "McLaren", 9, "Sauber"
    );

    // Track ID to Name mapping (simplified)
    private static final Map<Integer, String> TRACK_NAMES = Map.ofEntries(
            Map.entry(0, "Melbourne"), Map.entry(1, "Paul Ricard"), Map.entry(2, "Shanghai"), Map.entry(3, "Sakhir"),
            Map.entry(4, "Catalunya"), Map.entry(5, "Monaco"), Map.entry(6, "Montreal"), Map.entry(7, "Silverstone"),
            Map.entry(8, "Hockenheim"), Map.entry(9, "Hungaroring"), Map.entry(10, "Spa"), Map.entry(11, "Monza"),
            Map.entry(12, "Singapore"), Map.entry(13, "Suzuka"), Map.entry(14, "Abu Dhabi"), Map.entry(15, "Texas"),
            Map.entry(16, "Brazil"), Map.entry(17, "Austria"), Map.entry(18, "Sochi"), Map.entry(19, "Mexico"),
            Map.entry(20, "Baku"), Map.entry(21, "Sakhir Short"), Map.entry(22, "Silverstone Short"), Map.entry(23, "Texas Short"),
            Map.entry(24, "Suzuka Short"), Map.entry(25, "Hanoi"), Map.entry(26, "Zandvoort"), Map.entry(27, "Imola"),
            Map.entry(28, "Portimao"), Map.entry(29, "Jeddah"), Map.entry(30, "Miami"), Map.entry(31, "Las Vegas"),
            Map.entry(32, "Losail"), Map.entry(33, "Imola (Classic)"), Map.entry(34, "Estoril (Classic)"), Map.entry(35, "Jerez (Classic)"),
            Map.entry(36, "Adelaide (Classic)"), Map.entry(37, "Kyalami (Classic)"), Map.entry(38, "Brands Hatch (Classic)"), Map.entry(39, "Silverstone (Reverse)"),
            Map.entry(40, "Austria (Reverse)"), Map.entry(41, "Brazil (Reverse)")
    );

    // Tyre Compound ID to Name mapping
    private static final Map<Integer, String> TYRE_COMPOUNDS = Map.of(
            16, "Soft", 17, "Medium", 18, "Hard", 7, "Inter", 8, "Wet"
    );

    public void setActiveLeague(Long leagueId) {
        this.activeLeagueId = leagueId;
    }

    public void processPacket(PacketHeader header, ByteBuffer buffer) {
        switch (header.getPacketId()) {
            case 1: // Session
                this.currentSession = PacketSessionData.fromByteBuffer(buffer, header);
                break;
            case 2: // Lap Data
                this.currentLapData = PacketLapData.fromByteBuffer(buffer, header);
                broadcastLeaderboard();
                break;
            case 4: // Participants
                this.currentParticipants = PacketParticipantsData.fromByteBuffer(buffer, header);
                break;
            case 6: // Car Telemetry
                PacketCarTelemetryData telemetry = PacketCarTelemetryData.fromByteBuffer(buffer, header);
                if (header.getPlayerCarIndex() < telemetry.getCarTelemetryData().size()) {
                    broadcaster.broadcastTelemetry(telemetry.getCarTelemetryData().get(header.getPlayerCarIndex()));
                }
                break;
            case 7: // Car Status
                this.currentCarStatus = PacketCarStatusData.fromByteBuffer(buffer, header);
                broadcastLeaderboard();
                break;
            case 8: // Final Classification
                PacketFinalClassificationData classification = PacketFinalClassificationData.fromByteBuffer(buffer, header);
                handleFinalClassification(classification);
                break;
            case 10: // Car Damage
                PacketCarDamageData damage = PacketCarDamageData.fromByteBuffer(buffer, header);
                if (header.getPlayerCarIndex() < damage.getCarDamageData().size()) {
                    broadcaster.broadcastDamage(damage.getCarDamageData().get(header.getPlayerCarIndex()));
                }
                break;
            default:
                break;
        }
    }

    private void broadcastLeaderboard() {
        if (currentParticipants == null || currentLapData == null || currentCarStatus == null) return;

        List<DriverBoardState> board = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            if (i >= currentParticipants.getParticipants().size() || 
                i >= currentLapData.getLapData().size() || 
                i >= currentCarStatus.getCarStatusData().size()) break;

            ParticipantData p = currentParticipants.getParticipants().get(i);
            if (p.getName() == null || p.getName().isEmpty()) continue;

            LapData ld = currentLapData.getLapData().get(i);
            CarStatusData csd = currentCarStatus.getCarStatusData().get(i);

            DriverBoardState state = new DriverBoardState();
            state.setPosition(ld.getCarPosition());
            state.setName(p.getName());
            state.setTeam(TEAM_NAMES.getOrDefault(p.getTeamId(), "Unknown"));
            state.setTyreCompound(TYRE_COMPOUNDS.getOrDefault(csd.getVisualTyreCompound(), "Unknown"));
            state.setTyreAge(csd.getTyresAgeLaps());
            state.setPitStops(ld.getNumPitStops());
            state.setGapToLeader(formatTime(ld.getDeltaToRaceLeaderInMS()));
            state.setGapToFront(formatTime(ld.getDeltaToCarInFrontInMS()));
            
            board.add(state);
        }
        board.sort(Comparator.comparingInt(DriverBoardState::getPosition));
        broadcaster.broadcastLeaderboard(board);
    }

    private String formatTime(long ms) {
        if (ms == 0) return "-";
        return String.format("+%.3fs", ms / 1000.0f);
    }

    @Transactional
    public void handleFinalClassification(PacketFinalClassificationData classification) {
        if (activeLeagueId == null || currentSession == null || currentParticipants == null) {
            log.warn("Cannot save results: League, Session or Participants data missing.");
            return;
        }

        League league = leagueRepository.findById(activeLeagueId).orElse(null);
        if (league == null) return;

        // Check if session already recorded
        if (sessionResultRepository.findBySessionUID(classification.getHeader().getSessionUID()).isPresent()) {
            return;
        }

        // Find or create event
        String trackIdStr = String.valueOf(currentSession.getTrackId());
        Event event = eventRepository.findByLeagueAndTrackId(league, trackIdStr)
                .orElseGet(() -> {
                    Event newEvent = new Event();
                    newEvent.setLeague(league);
                    newEvent.setTrackId(trackIdStr);
                    newEvent.setEventName(TRACK_NAMES.getOrDefault(currentSession.getTrackId(), "Track " + trackIdStr));
                    return eventRepository.save(newEvent);
                });

        SessionResult sessionResult = new SessionResult();
        sessionResult.setLeague(league);
        sessionResult.setEvent(event);
        sessionResult.setSessionUID(classification.getHeader().getSessionUID());
        sessionResult.setSessionType(currentSession.getSessionType());
        sessionResult.setTrackId(trackIdStr);

        boolean isRace = currentSession.getSessionType() >= 10 && currentSession.getSessionType() <= 12;

        for (int i = 0; i < classification.getNumCars(); i++) {
            FinalClassificationData data = classification.getClassificationData().get(i);
            if (data.getResultStatus() == 0) continue; // Inactive/Invalid

            ParticipantData participant = currentParticipants.getParticipants().get(i);
            
            DriverResult driverResult = new DriverResult();
            driverResult.setSessionResult(sessionResult);
            driverResult.setDriverName(participant.getName());
            driverResult.setTeamName(TEAM_NAMES.getOrDefault(participant.getTeamId(), "Unknown"));
            driverResult.setPosition(data.getPosition());
            driverResult.setPointsAwarded(data.getPoints());
            driverResult.setGridPosition(data.getGridPosition());
            driverResult.setBestLapTime(data.getBestLapTimeInMS() / 1000.0f);
            
            sessionResult.getDriverResults().add(driverResult);
            
            if (isRace) {
                updateStandings(league, driverResult);
            }
        }

        sessionResultRepository.save(sessionResult);
        log.info("Saved {} results for session UID: {} in event: {}", 
                isRace ? "Race" : "Qualifying", sessionResult.getSessionUID(), event.getEventName());
    }

    private void updateStandings(League league, DriverResult result) {
        // Update Driver Standings
        DriverStanding ds = driverStandingRepository.findByLeagueAndDriverName(league, result.getDriverName())
                .orElseGet(() -> {
                    DriverStanding newDs = new DriverStanding();
                    newDs.setLeague(league);
                    newDs.setDriverName(result.getDriverName());
                    return newDs;
                });
        ds.setPoints(ds.getPoints() + result.getPointsAwarded());
        if (result.getPosition() == 1) ds.setWins(ds.getWins() + 1);
        if (result.getPosition() <= 3) ds.setPodiums(ds.getPodiums() + 1);
        driverStandingRepository.save(ds);

        // Update Team Standings
        TeamStanding ts = teamStandingRepository.findByLeagueAndTeamName(league, result.getTeamName())
                .orElseGet(() -> {
                    TeamStanding newTs = new TeamStanding();
                    newTs.setLeague(league);
                    newTs.setTeamName(result.getTeamName());
                    return newTs;
                });
        ts.setPoints(ts.getPoints() + result.getPointsAwarded());
        teamStandingRepository.save(ts);
    }
}
