package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.entity.TeamMapping;
import be.jabapage.racingleague.f1telemetry.model.*;
import be.jabapage.racingleague.f1telemetry.repository.TeamMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private TeamMappingRepository teamMappingRepository;

    private static final java.util.concurrent.ConcurrentHashMap<String, String> teamCache = new java.util.concurrent.ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        refreshTeamCache();
    }

    public void refreshTeamCache() {
        teamCache.clear();
        if (teamMappingRepository != null) {
            List<TeamMapping> mappings = teamMappingRepository.findAll();
            for (TeamMapping m : mappings) {
                teamCache.put(m.getTeamId() + "|" + m.getCarType(), m.getTeamName());
            }
        }
    }

    // Team ID to Name mapping (package-private for access from other services)
    static final Map<Integer, String> TEAM_NAMES = Map.of(
            0, "Mercedes", 1, "Ferrari", 2, "Red Bull Racing", 3, "Williams",
            4, "Aston Martin", 5, "Alpine", 6, "RB", 7, "Haas",
            8, "McLaren", 9, "Sauber"
    );

    static final Map<Integer, String> TEAM_NAMES_F1_26 = Map.ofEntries(
            Map.entry(476, "Mercedes"), Map.entry(220, "Mercedes"),
            Map.entry(477, "Ferrari"), Map.entry(221, "Ferrari"),
            Map.entry(478, "Red Bull Racing"), Map.entry(222, "Red Bull Racing"),
            Map.entry(479, "Williams"), Map.entry(223, "Williams"),
            Map.entry(480, "Aston Martin"), Map.entry(224, "Aston Martin"),
            Map.entry(481, "Alpine"), Map.entry(225, "Alpine"),
            Map.entry(482, "RB"), Map.entry(226, "RB"),
            Map.entry(483, "Haas"), Map.entry(227, "Haas"),
            Map.entry(484, "McLaren"), Map.entry(228, "McLaren"),
            Map.entry(485, "Audi"), Map.entry(229, "Audi"),
            Map.entry(486, "Cadillac"), Map.entry(230, "Cadillac")
    );

    public static String getTeamNameStatic(Integer teamId, String carType) {
        if (teamId == null) return "Unknown";
        String key = teamId + "|" + (carType != null ? carType : "F1 25");
        String name = teamCache.get(key);
        if (name != null) return name;

        // Fallback for tests or when DB mappings are not loaded
        if ("F1 26".equals(carType)) {
            return TEAM_NAMES_F1_26.getOrDefault(teamId, "Unknown (ID: " + teamId + ")");
        }
        return TEAM_NAMES.getOrDefault(teamId, "Unknown (ID: " + teamId + ")");
    }

    public static String getTeamName(int teamId, int gameYear) {
        return getTeamNameStatic(teamId, gameYear == 26 ? "F1 26" : "F1 25");
    }

    public static String detectCarType(PacketParticipantsData participants, int gameYear) {
        if (gameYear == 26) {
            return "F1 26";
        }
        if (participants != null && participants.getParticipants() != null) {
            for (ParticipantData p : participants.getParticipants()) {
                int teamId = p.getTeamId();
                if ((teamId >= 220 && teamId <= 230) || (teamId >= 476 && teamId <= 486)) {
                    return "F1 26";
                }
                if (p.getName() != null && !p.getName().isEmpty()) {
                    String upperName = p.getName().toUpperCase();
                    if (upperName.contains("BORTOLETO") || upperName.contains("LINDBLAD")) {
                        return "F1 26";
                    }
                }
            }
        }
        return "F1 25";
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
    public static final Map<Integer, String> TYRE_COMPOUNDS = new java.util.AbstractMap<Integer, String>() {
        @Override
        public String get(Object key) {
            if (key instanceof Integer compoundId) {
                return getTyreCompoundName(compoundId);
            }
            return null;
        }

        @Override
        public String getOrDefault(Object key, String defaultValue) {
            if (key instanceof Integer compoundId) {
                String name = getTyreCompoundName(compoundId);
                return "Unknown".equals(name) ? defaultValue : name;
            }
            return defaultValue;
        }

        @Override
        public java.util.Set<Entry<Integer, String>> entrySet() {
            return java.util.Collections.emptySet();
        }
    };

    public static String getTyreCompoundName(int compoundId) {
        return switch (compoundId) {
            case 16 -> "Soft";
            case 17 -> "Medium";
            case 18 -> "Hard";
            case 7 -> "Inter";
            case 8, 10 -> "Wet";
            case 9 -> "Dry";
            case 19 -> "C2";
            case 20 -> "C1";
            case 21 -> "C0";
            case 22 -> "C6";
            case 11 -> "Super Soft";
            case 12 -> "Soft";
            case 13 -> "Medium";
            case 14 -> "Hard";
            case 15 -> "Wet";
            default -> "Unknown";
        };
    }

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

    public java.util.Optional<be.jabapage.racingleague.f1telemetry.entity.Event> getEventWithAllResults(Long eventId) {
        return telemetryResultsService.getEventWithAllResults(eventId);
    }
}

