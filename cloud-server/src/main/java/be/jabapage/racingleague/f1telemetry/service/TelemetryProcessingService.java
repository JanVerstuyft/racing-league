package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TelemetryProcessingService {

    @Autowired
    private TelemetryStateService telemetryStateService;

    @Autowired
    private TelemetryPacketProcessor telemetryPacketProcessor;

    @Autowired
    private LiveDashboardService liveDashboardService;

    @Autowired
    private RaceAnalyticsService raceAnalyticsService;

    @Autowired
    private TelemetryResultsService telemetryResultsService;

    // Team ID to Name mapping (package-private for access from other services)
    static final Map<Integer, String> TEAM_NAMES = Map.of(
            0, "Mercedes", 1, "Ferrari", 2, "Red Bull Racing", 3, "Williams",
            4, "Aston Martin", 5, "Alpine", 6, "RB", 7, "Haas",
            8, "McLaren", 9, "Sauber"
    );

    public static String getTeamName(int teamId, int gameYear) {
        if (teamId == 9) {
            return gameYear == 26 ? "Audi" : "Sauber";
        }
        if (teamId == 10) {
            return gameYear == 26 ? "Cadillac" : "Unknown";
        }
        return TEAM_NAMES.getOrDefault(teamId, "Unknown");
    }

    // Track ID to Name mapping
    public static final Map<Integer, String> TRACK_NAMES = Map.ofEntries(
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
            Map.entry(40, "Austria (Reverse)"), Map.entry(41, "Brazil (Reverse)"), Map.entry(42, "Madrid")
    );

    // Tyre Compound ID to Name mapping
    public static final Map<Integer, String> TYRE_COMPOUNDS = Map.of(
            16, "Soft", 17, "Medium", 18, "Hard", 7, "Inter", 8, "Wet"
    );

    // Session Type mapping
    public static final Map<Integer, String> SESSION_TYPE_NAMES = Map.ofEntries(
            Map.entry(0, "Unknown"),
            Map.entry(1, "Practice 1"), Map.entry(2, "Practice 2"), Map.entry(3, "Practice 3"), Map.entry(4, "Short Practice"),
            Map.entry(5, "Qualifying 1"), Map.entry(6, "Qualifying 2"), Map.entry(7, "Qualifying 3"), Map.entry(8, "Short Qualifying"), Map.entry(9, "One-Shot Qualifying"),
            Map.entry(10, "Sprint Shootout 1"), Map.entry(11, "Sprint Shootout 2"), Map.entry(12, "Sprint Shootout 3"), Map.entry(13, "Short Sprint Shootout"), Map.entry(14, "One-Shot Sprint Shootout"),
            Map.entry(15, "Race"), Map.entry(16, "Race 2"), Map.entry(17, "Race 3"),
            Map.entry(18, "Time Trial"),
            Map.entry(19, "Sprint Race")
    );

    @Scheduled(fixedDelay = 1000)
    public void syncDistributedState() {
        telemetryStateService.syncDistributedState();
    }

    public void refreshHideAiSetting(Long leagueId) {
        telemetryStateService.refreshHideAiSetting(leagueId);
    }

    public void refreshDriverMappings(Long leagueId) {
        telemetryStateService.refreshDriverMappings(leagueId);
    }

    public List<RacePaceStats> calculatePureRacePace(Long sessionResultId) {
        return raceAnalyticsService.calculatePureRacePace(sessionResultId);
    }

    public List<ConsistencyStats> calculateConsistency(Long sessionResultId) {
        return raceAnalyticsService.calculateConsistency(sessionResultId);
    }

    public List<LongestStintStats> calculateLongestStints(Long sessionResultId) {
        return raceAnalyticsService.calculateLongestStints(sessionResultId);
    }

    public synchronized void processPacket(String token, PacketHeader header, ByteBuffer buffer) {
        telemetryPacketProcessor.processPacket(token, header, buffer);
    }

    public SessionInfo getSessionInfo(Long tierId) {
        return liveDashboardService.getSessionInfo(tierId);
    }

    public List<DriverBoardState> getLeaderboard(Long tierId) {
        return liveDashboardService.getLeaderboard(tierId);
    }

    public void handleFinalClassification(LeagueSessionState state, PacketFinalClassificationData classification) {
        telemetryResultsService.handleFinalClassification(state, classification);
    }

    public void saveResultsFromLiveState(LeagueSessionState state, long sessionUID) {
        telemetryResultsService.saveResultsFromLiveState(state, sessionUID);
    }

    public void updateDriverNamesFromMappings(Long leagueId) {
        telemetryResultsService.updateDriverNamesFromMappings(leagueId);
    }

    public void recalculateStandings(Long tierId) {
        telemetryResultsService.recalculateStandings(tierId);
    }

    public void calculateGaps(SessionResult session) {
        telemetryResultsService.calculateGaps(session);
    }

    public void deleteEvent(Long eventId) {
        telemetryResultsService.deleteEvent(eventId);
    }
}
