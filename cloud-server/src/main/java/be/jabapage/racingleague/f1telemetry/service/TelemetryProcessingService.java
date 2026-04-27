package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.*;
import be.jabapage.racingleague.f1telemetry.entity.TyreStint;
import be.jabapage.racingleague.f1telemetry.model.*;
import be.jabapage.racingleague.f1telemetry.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

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
    private LapResultRepository lapResultRepository;
    @Autowired
    private Broadcaster broadcaster;

    private Long activeLeagueId;
    private PacketSessionData currentSession;
    private PacketParticipantsData currentParticipants;
    private PacketLapData currentLapData;
    private PacketCarStatusData currentCarStatus;

    private final int[] lastLapNum = new int[22];
    private final long[] lastS1 = new long[22];
    private final long[] lastS2 = new long[22];
    private final int[] lastTyre = new int[22];
    private final boolean[] lapInvalid = new boolean[22];

    private final long[] driverBestLap = new long[22];
    private final long[] driverBestS1 = new long[22];
    private final long[] driverBestS2 = new long[22];
    private final long[] driverBestS3 = new long[22];

    private long sessionBestS1 = Long.MAX_VALUE;
    private long sessionBestS2 = Long.MAX_VALUE;
    private long sessionBestS3 = Long.MAX_VALUE;
    private long sessionBestLap = Long.MAX_VALUE;
    
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
    public static final Map<Integer, String> TYRE_COMPOUNDS = Map.of(
            16, "Soft", 17, "Medium", 18, "Hard", 7, "Inter", 8, "Wet"
    );

    public static final Map<Integer, String> SESSION_TYPE_NAMES = Map.ofEntries(
            Map.entry(0, "Unknown"),
            Map.entry(1, "Practice 1"), Map.entry(2, "Practice 2"), Map.entry(3, "Practice 3"), Map.entry(4, "Short Practice"),
            Map.entry(5, "Qualifying 1"), Map.entry(6, "Qualifying 2"), Map.entry(7, "Qualifying 3"), Map.entry(8, "Short Qualifying"), Map.entry(9, "One-Shot Qualifying"),
            Map.entry(10, "Sprint Shootout 1"), Map.entry(11, "Sprint Shootout 2"), Map.entry(12, "Sprint Shootout 3"), Map.entry(13, "Short Sprint Shootout"), Map.entry(14, "One-Shot Sprint Shootout"),
            Map.entry(15, "Race"), Map.entry(16, "Race 2"), Map.entry(17, "Race 3"),
            Map.entry(18, "Time Trial")
    );

    public void setActiveLeague(Long leagueId) {
        this.activeLeagueId = leagueId;
    }

    public Long getActiveLeagueId() {
        return activeLeagueId;
    }

    private long currentSessionUID = -1;
    private long lastPacketTime = 0;

    private void resetSessionState() {
        Arrays.fill(lastLapNum, 0);
        Arrays.fill(lastS1, 0);
        Arrays.fill(lastS2, 0);
        Arrays.fill(lastTyre, 0);
        Arrays.fill(lapInvalid, false);
        Arrays.fill(driverBestLap, 0);
        Arrays.fill(driverBestS1, 0);
        Arrays.fill(driverBestS2, 0);
        Arrays.fill(driverBestS3, 0);
        sessionBestS1 = Long.MAX_VALUE;
        sessionBestS2 = Long.MAX_VALUE;
        sessionBestS3 = Long.MAX_VALUE;
        sessionBestLap = Long.MAX_VALUE;
        currentSession = null;
        currentParticipants = null;
        currentLapData = null;
        currentCarStatus = null;
        
        // Clear the live UI
        broadcaster.broadcastLeaderboard(Collections.emptyList());
    }

    public synchronized void processPacket(PacketHeader header, ByteBuffer buffer) {
        long now = System.currentTimeMillis();
        long packetSessionUID = header.getSessionUID();

        if (header.getPacketId() == 8) {
            log.info("Incoming Packet 8 (Final Classification) for UID: {}", packetSessionUID);
        }

        // Reset session-specific state if session UID changed OR if there was a long gap
        // We only trigger a reset if the NEW UID is non-zero.
        boolean sessionChanged = (packetSessionUID != 0 && packetSessionUID != currentSessionUID);
        boolean timeout = (now - lastPacketTime > 5000 && lastPacketTime > 0);

        if (sessionChanged || timeout) {
            log.info("{} detected, resetting live tracking state. (New UID: {}, Old UID: {}, Gap: {}ms)",
                timeout ? "Timeout" : "Session change",
                packetSessionUID, currentSessionUID, (now - lastPacketTime));
            resetSessionState();
            currentSessionUID = packetSessionUID;
        }
        lastPacketTime = now;

        // Skip packets that don't match the session we are currently tracking.
        // We ignore UID 0 packets if we have a valid session active.
        if (currentSessionUID != -1 && currentSessionUID != 0 && packetSessionUID == 0) {
            return;
        }
        
        // Safety check to ensure we don't intermix packets from different sessions
        if (currentSessionUID != -1 && packetSessionUID != 0 && packetSessionUID != currentSessionUID) {
            return;
        }

        switch (header.getPacketId()) {
            case 1: // Session
                this.currentSession = PacketSessionData.fromByteBuffer(buffer, header);
                broadcastSessionInfo();
                break;
            case 2: // Lap Data
                PacketLapData newLapData = PacketLapData.fromByteBuffer(buffer, header);
                processLapData(newLapData);
                this.currentLapData = newLapData;
                broadcastLeaderboard();
                broadcastSessionInfo(); // Update lap progress
                break;
            case 3: // Event
                PacketEventData eventData = PacketEventData.fromByteBuffer(buffer, header);
                if ("SEND".equals(eventData.getEventStringCode())) {
                    log.info("Session Ended event (SEND) received for UID: {}. Triggering result save.", header.getSessionUID());
                    saveResultsFromLiveState(header.getSessionUID());
                }
                break;
            case 4: // Participants
                this.currentParticipants = PacketParticipantsData.fromByteBuffer(buffer, header);
                break;
            case 7: // Car Status
                this.currentCarStatus = PacketCarStatusData.fromByteBuffer(buffer, header);
                broadcastLeaderboard();
                break;
            case 8: // Final Classification
                PacketFinalClassificationData classification = PacketFinalClassificationData.fromByteBuffer(buffer, header);
                handleFinalClassification(classification);
                break;
            default:
                break;
        }
    }

    private void processLapData(PacketLapData packet) {
        for (int i = 0; i < packet.getLapData().size(); i++) {
            LapData ld = packet.getLapData().get(i);
            int carIndex = i;

            // Update sector times if they are valid in current lap
            if (ld.getSector() == 1 && ld.getSector1TimeInMS() > 0) {
                long s1 = ld.getSector1TimeInMS();
                lastS1[carIndex] = s1;
                if (ld.getCurrentLapInvalid() == 0) {
                    if (s1 < driverBestS1[carIndex] || driverBestS1[carIndex] == 0) driverBestS1[carIndex] = s1;
                    if (s1 < sessionBestS1) sessionBestS1 = s1;
                }
            } else if (ld.getSector() == 2 && ld.getSector2TimeInMS() > 0) {
                long s2 = ld.getSector2TimeInMS();
                lastS2[carIndex] = s2;
                if (ld.getCurrentLapInvalid() == 0) {
                    if (s2 < driverBestS2[carIndex] || driverBestS2[carIndex] == 0) driverBestS2[carIndex] = s2;
                    if (s2 < sessionBestS2) sessionBestS2 = s2;
                }
            }

            // Track if current lap becomes invalid
            if (ld.getCurrentLapInvalid() == 1) {
                lapInvalid[carIndex] = true;
            }

            // Detect lap completion
            if (lastLapNum[carIndex] > 0 && ld.getCurrentLapNum() > lastLapNum[carIndex]) {
                long lastLapTime = ld.getLastLapTimeInMS();
                long s1 = lastS1[carIndex];
                long s2 = lastS2[carIndex];
                long s3 = lastLapTime - s1 - s2;

                // Update best lap and S3
                if (!lapInvalid[carIndex] && lastLapTime > 0) {
                    if (lastLapTime < driverBestLap[carIndex] || driverBestLap[carIndex] == 0) driverBestLap[carIndex] = lastLapTime;
                    if (lastLapTime < sessionBestLap) sessionBestLap = lastLapTime;
                    
                    if (s3 > 0) {
                        if (s3 < driverBestS3[carIndex] || driverBestS3[carIndex] == 0) driverBestS3[carIndex] = s3;
                        if (s3 < sessionBestS3) sessionBestS3 = s3;
                    }
                }

                // Last lap is completed
                LapResult result = new LapResult();
                result.setSessionUID(packet.getHeader().getSessionUID());
                result.setCarIndex(carIndex);
                result.setLapNumber(lastLapNum[carIndex]);
                result.setLapTimeInMS(lastLapTime);
                result.setS1InMS(s1);
                result.setS2InMS(s2);
                result.setS3InMS(s3);
                result.setIsValid(!lapInvalid[carIndex]);
                result.setTyreCompound(lastTyre[carIndex]);
                result.setPitStopCount(ld.getNumPitStops());

                lapResultRepository.save(result);
            }

            // Update state
            lastLapNum[carIndex] = ld.getCurrentLapNum();
            if (currentCarStatus != null && carIndex < currentCarStatus.getCarStatusData().size()) {
                lastTyre[carIndex] = currentCarStatus.getCarStatusData().get(carIndex).getVisualTyreCompound();
            }
            if (ld.getSector() == 0) { // New lap started
                lapInvalid[carIndex] = false;
            }
        }
    }

    private void broadcastSessionInfo() {
        if (currentSession != null) {
            String sessionName = SESSION_TYPE_NAMES.getOrDefault(currentSession.getSessionType(), "Unknown (" + currentSession.getSessionType() + ")");
            int playerCarIndex = currentSession.getHeader().getPlayerCarIndex();
            int currentLap = 0;
            if (currentLapData != null && playerCarIndex < currentLapData.getLapData().size()) {
                currentLap = currentLapData.getLapData().get(playerCarIndex).getCurrentLapNum();
            }

            boolean isRace = currentSession.getSessionType() >= 15 && currentSession.getSessionType() <= 17;

            SessionInfo info = SessionInfo.builder()
                    .sessionType(sessionName)
                    .currentLap(currentLap)
                    .totalLaps(currentSession.getTotalLaps())
                    .timeLeftSeconds(currentSession.getSessionTimeLeft())
                    .isRace(isRace)
                    .build();
            
            broadcaster.broadcastSessionInfo(info);
        }
    }

    private void broadcastLeaderboard() {
        if (currentParticipants == null || currentLapData == null || currentCarStatus == null || currentSession == null) return;
        
        boolean isQualifying = currentSession.getSessionType() >= 5 && currentSession.getSessionType() <= 14;

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
            state.setPenalties(ld.getPenalties());
            state.setResultStatus(ld.getResultStatus());
            state.setQualifying(isQualifying);

            if (isQualifying) {
                state.setBestLapTime(formatLapTimeFull(driverBestLap[i]));
                state.setS1Time(formatLapTimeFull(driverBestS1[i]));
                state.setS2Time(formatLapTimeFull(driverBestS2[i]));
                state.setS3Time(formatLapTimeFull(driverBestS3[i]));
                state.setBestS1(driverBestS1[i] > 0 && driverBestS1[i] == sessionBestS1);
                state.setBestS2(driverBestS2[i] > 0 && driverBestS2[i] == sessionBestS2);
                state.setBestS3(driverBestS3[i] > 0 && driverBestS3[i] == sessionBestS3);
                
                if (driverBestLap[i] > 0 && sessionBestLap > 0) {
                    state.setGapToLeaderBest(formatTime(driverBestLap[i] - sessionBestLap));
                } else {
                    state.setGapToLeaderBest("-");
                }
            } else {
                state.setGapToLeader(formatTime(ld.getDeltaToRaceLeaderInMS()));
                state.setGapToFront(formatTime(ld.getDeltaToCarInFrontInMS()));
            }
            
            board.add(state);
        }
        board.sort(Comparator.comparingInt(DriverBoardState::getPosition));
        broadcaster.broadcastLeaderboard(board);
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "-";
        return String.format("+%.3fs", ms / 1000.0f);
    }

    private String formatLapTimeFull(long ms) {
        if (ms <= 0) return "-";
        int minutes = (int) (ms / 60000);
        float seconds = (ms % 60000) / 1000.0f;
        return String.format("%d:%06.3f", minutes, seconds);
    }

    public List<RacePaceStats> calculatePureRacePace(Long eventId) {
        Event event = eventRepository.findByIdWithResults(eventId).orElse(null);
        if (event == null) return Collections.emptyList();

        // Find the main race session
        SessionResult raceSession = event.getSessionResults().stream()
                .filter(s -> s.getSessionType() >= 15 && s.getSessionType() <= 17)
                .findFirst().orElse(null);
        if (raceSession == null) return Collections.emptyList();

        // Calculate total race distance (max laps driven)
        int maxLaps = raceSession.getDriverResults().stream()
                .flatMap(dr -> dr.getLapResults().stream())
                .mapToInt(LapResult::getLapNumber)
                .max().orElse(0);

        List<RacePaceStats> statsList = new ArrayList<>();
        for (DriverResult dr : raceSession.getDriverResults()) {
            List<LapResult> validLaps = dr.getLapResults().stream()
                    .filter(LapResult::getIsValid)
                    .collect(Collectors.toList());

            // Only drivers who driven at least 50%
            if (dr.getLapResults().size() < maxLaps * 0.5) continue;

            RacePaceStats stats = new RacePaceStats();
            stats.setDriverName(dr.getDriverName());
            stats.setTeamName(dr.getTeamName());

            double s1 = calculateWeightedSector(validLaps.stream().mapToLong(LapResult::getS1InMS).toArray());
            double s2 = calculateWeightedSector(validLaps.stream().mapToLong(LapResult::getS2InMS).toArray());
            double s3 = calculateWeightedSector(validLaps.stream().mapToLong(LapResult::getS3InMS).toArray());

            stats.setS1Pace(s1 / 1000.0);
            stats.setS2Pace(s2 / 1000.0);
            stats.setS3Pace(s3 / 1000.0);
            stats.setPureRacePace((s1 + s2 + s3) / 1000.0);

            // Tyre usage (percentage of tyres in top 60% sectors - simplified to valid laps here)
            Map<String, Long> compoundCounts = validLaps.stream()
                    .collect(Collectors.groupingBy(l -> TYRE_COMPOUNDS.getOrDefault(l.getTyreCompound(), "U"), Collectors.counting()));
            long totalValid = validLaps.size();
            Map<String, Double> tyreUsage = new HashMap<>();
            compoundCounts.forEach((k, v) -> tyreUsage.put(k, (double) v / totalValid * 100.0));
            stats.setTyreUsage(tyreUsage);

            statsList.add(stats);
        }

        // Sector performance calculation
        if (!statsList.isEmpty()) {
            double bestPace = statsList.stream().mapToDouble(RacePaceStats::getPureRacePace).min().orElse(0);
            double avgPace = statsList.stream().mapToDouble(RacePaceStats::getPureRacePace).average().orElse(0);
            
            for (RacePaceStats s : statsList) {
                if (avgPace == bestPace) {
                    s.setSectorPerformance(10.0);
                } else {
                    double perf = 10.0 - 5.0 * (s.getPureRacePace() - bestPace) / (avgPace - bestPace);
                    s.setSectorPerformance(Math.max(0, Math.min(10.0, perf)));
                }
            }
        }

        return statsList.stream()
                .sorted(Comparator.comparingDouble(RacePaceStats::getPureRacePace))
                .collect(Collectors.toList());
    }

    private double calculateWeightedSector(long[] times) {
        if (times.length == 0) return 0;
        Arrays.sort(times);
        
        int n = times.length;
        double n30 = n * 0.3;
        double totalWeight = 0;
        double weightedSum = 0;

        for (int i = 0; i < n; i++) {
            double weight = 0;
            int rank = i + 1; // 1-based index

            if (rank <= n30) {
                weight = 1.0;
            } else if (rank <= 2 * n30) {
                weight = 1.0 - (rank - n30) / n30;
            } else {
                weight = 0;
            }

            if (weight > 0) {
                weightedSum += times[i] * weight;
                totalWeight += weight;
            }
        }

        return totalWeight > 0 ? weightedSum / totalWeight : times[0];
    }

    @Transactional
    public void handleFinalClassification(PacketFinalClassificationData classification) {
        long sessionUID = classification.getHeader().getSessionUID();
        log.info("Received Final Classification packet (packet 8) for session UID: {}", sessionUID);
        
        if (activeLeagueId == null) {
            log.warn("Cannot save results: No league activated.");
            return;
        }
        if (currentSession == null || currentParticipants == null) {
            log.warn("Cannot save results: Session or Participants data missing for UID: {}. (Session: {}, Participants: {})", 
                sessionUID,
                currentSession != null ? "OK" : "MISSING", 
                currentParticipants != null ? "OK" : "MISSING");
            return;
        }

        League league = leagueRepository.findByIdWithEvents(activeLeagueId).orElse(null);
        if (league == null) {
            log.warn("Cannot save results: Activated league ID {} not found in database.", activeLeagueId);
            return;
        }

        // Check if session already recorded
        int sessionType = currentSession.getSessionType();
        Optional<SessionResult> existing = sessionResultRepository.findBySessionUIDAndSessionType(sessionUID, sessionType);
        if (existing.isPresent()) {
            SessionResult sr = existing.get();
            log.info("Session UID: {} with Type: {} already recorded as ID: {}. Skipping.", 
                sessionUID, sessionType, sr.getId());
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
                    log.info("Creating new event: {} for track: {}", newEvent.getEventName(), trackIdStr);
                    return eventRepository.save(newEvent);
                });

        log.info("Processing {} results for session UID: {} (Type: {})", 
            classification.getNumCars(), sessionUID, currentSession.getSessionType());

        SessionResult sessionResult = new SessionResult();
        sessionResult.setLeague(league);
        sessionResult.setEvent(event);
        sessionResult.setSessionUID(classification.getHeader().getSessionUID());
        sessionResult.setSessionType(currentSession.getSessionType());
        sessionResult.setTrackId(trackIdStr);

        boolean isRace = currentSession.getSessionType() >= 15 && currentSession.getSessionType() <= 17;

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
            driverResult.setResultStatus(data.getResultStatus());
            driverResult.setPenalties(data.getPenaltiesTime());

            // Link stored lap results
            List<LapResult> laps = lapResultRepository.findBySessionUIDAndCarIndex(classification.getHeader().getSessionUID(), i);
            for (LapResult lap : laps) {
                lap.setDriverResult(driverResult);
                driverResult.getLapResults().add(lap);
            }

            // Process Tyre Stints
            int lastEndLap = 0;
            for (int j = 0; j < data.getNumTyreStints(); j++) {
                TyreStint stint = new TyreStint();
                stint.setDriverResult(driverResult);
                stint.setStintOrder(j);
                stint.setTyreCompound(data.getTyreStintsVisual()[j]);
                
                int endLap = data.getTyreStintsEndLaps()[j];
                if (endLap == 255) {
                    endLap = data.getNumLaps();
                }
                
                stint.setEndLap(endLap);
                stint.setLaps(endLap - lastEndLap);
                lastEndLap = endLap;
                driverResult.getTyreStints().add(stint);
            }
            
            sessionResult.getDriverResults().add(driverResult);
        }

        // Save everything first to ensure IDs are generated and relations are persisted
        sessionResultRepository.saveAndFlush(sessionResult);

        // Then update standings if it's a race
        if (isRace) {
            for (DriverResult driverResult : sessionResult.getDriverResults()) {
                updateStandings(league, driverResult);
            }
        }

        log.info("Saved {} results for session UID: {} in event: {}", 
                isRace ? "Race" : "Qualifying", sessionResult.getSessionUID(), event.getEventName());
    }

    @Transactional
    public void saveResultsFromLiveState(long sessionUID) {
        if (activeLeagueId == null || currentSession == null || currentParticipants == null || currentLapData == null) {
            log.warn("Cannot save live results: Missing critical context (League, Session, Participants or LapData)");
            return;
        }

        // Check if session already recorded
        int sessionType = currentSession.getSessionType();
        Optional<SessionResult> existing = sessionResultRepository.findBySessionUIDAndSessionType(sessionUID, sessionType);
        if (existing.isPresent()) {
            return; // Already saved (maybe by Packet 8 that arrived just before SEND)
        }

        League league = leagueRepository.findByIdWithEvents(activeLeagueId).orElse(null);
        if (league == null) return;

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
        sessionResult.setSessionUID(sessionUID);
        sessionResult.setSessionType(sessionType);
        sessionResult.setTrackId(trackIdStr);

        boolean isRace = sessionType >= 15 && sessionType <= 17;

        for (int i = 0; i < currentParticipants.getParticipants().size(); i++) {
            ParticipantData participant = currentParticipants.getParticipants().get(i);
            if (participant.getName() == null || participant.getName().isEmpty()) continue;

            if (i >= currentLapData.getLapData().size()) break;
            LapData ld = currentLapData.getLapData().get(i);
            
            // Skip inactive drivers
            if (ld.getResultStatus() == 0 || ld.getResultStatus() == 1) continue;

            DriverResult driverResult = new DriverResult();
            driverResult.setSessionResult(sessionResult);
            driverResult.setDriverName(participant.getName());
            driverResult.setTeamName(TEAM_NAMES.getOrDefault(participant.getTeamId(), "Unknown"));
            driverResult.setPosition(ld.getCarPosition());
            driverResult.setGridPosition(ld.getGridPosition());
            driverResult.setBestLapTime(driverBestLap[i] / 1000.0f);
            driverResult.setResultStatus(ld.getResultStatus());
            driverResult.setPenalties(ld.getPenalties());
            
            // Assign points for Race sessions based on standard F1 system
            if (isRace && ld.getCarPosition() >= 1 && ld.getCarPosition() <= 10) {
                int[] pointsMap = {0, 25, 18, 15, 12, 10, 8, 6, 4, 2, 1};
                driverResult.setPointsAwarded(pointsMap[ld.getCarPosition()]);
            } else {
                driverResult.setPointsAwarded(0);
            }

            // Link stored lap results
            List<LapResult> laps = lapResultRepository.findBySessionUIDAndCarIndex(sessionUID, i);
            for (LapResult lap : laps) {
                lap.setDriverResult(driverResult);
                driverResult.getLapResults().add(lap);
            }

            // Derive Tyre Stints from Lap Results
            if (!laps.isEmpty()) {
                laps.sort(Comparator.comparingInt(LapResult::getLapNumber));
                int stintOrder = 0;
                int currentCompound = -1;
                int currentPitCount = -1;
                int startLap = laps.get(0).getLapNumber();
                int lastLap = -1;
                
                for (int j = 0; j < laps.size(); j++) {
                    LapResult lap = laps.get(j);
                    
                    // Detect stint change: 
                    // 1. Compound changed
                    // 2. Pit stop count increased (even if compound is same)
                    // 3. Large gap in laps (fallback)
                    boolean compoundChanged = (currentCompound != -1 && lap.getTyreCompound() != null && lap.getTyreCompound() != currentCompound);
                    boolean pitStopOccurred = (currentPitCount != -1 && lap.getPitStopCount() != null && lap.getPitStopCount() > currentPitCount);
                    boolean gapDetected = (lastLap != -1 && lap.getLapNumber() > lastLap + 1);

                    if (compoundChanged || pitStopOccurred || gapDetected) {
                        TyreStint stint = new TyreStint();
                        stint.setDriverResult(driverResult);
                        stint.setStintOrder(stintOrder++);
                        stint.setTyreCompound(currentCompound);
                        stint.setEndLap(lastLap);
                        stint.setLaps(stint.getEndLap() - startLap + 1);
                        driverResult.getTyreStints().add(stint);
                        
                        startLap = lap.getLapNumber();
                    }
                    
                    currentCompound = (lap.getTyreCompound() != null) ? lap.getTyreCompound() : currentCompound;
                    currentPitCount = (lap.getPitStopCount() != null) ? lap.getPitStopCount() : currentPitCount;
                    lastLap = lap.getLapNumber();
                    
                    if (j == laps.size() - 1) { // Final stint
                        TyreStint stint = new TyreStint();
                        stint.setDriverResult(driverResult);
                        stint.setStintOrder(stintOrder++);
                        stint.setTyreCompound(currentCompound);
                        stint.setEndLap(lastLap);
                        stint.setLaps(stint.getEndLap() - startLap + 1);
                        driverResult.getTyreStints().add(stint);
                    }
                }
            }
            
            sessionResult.getDriverResults().add(driverResult);
        }

        sessionResultRepository.saveAndFlush(sessionResult);

        if (isRace) {
            for (DriverResult driverResult : sessionResult.getDriverResults()) {
                updateStandings(league, driverResult);
            }
        }

        log.info("Saved Fallback {} results (from live state) for session UID: {} in event: {}", 
                isRace ? "Race" : "Qualifying", sessionUID, event.getEventName());
    }

    @Transactional
    public void recalculateStandings(Long leagueId) {
        League league = leagueRepository.findByIdWithEvents(leagueId).orElse(null);
        if (league == null) return;

        // Clear existing standings
        driverStandingRepository.deleteAll(driverStandingRepository.findByLeague(league));
        teamStandingRepository.deleteAll(teamStandingRepository.findByLeague(league));

        // Get all race sessions from events
        List<SessionResult> raceSessions = league.getEvents().stream()
                .flatMap(e -> e.getSessionResults().stream())
                .filter(s -> s.getSessionType() >= 15 && s.getSessionType() <= 17)
                .collect(Collectors.toList());

        for (SessionResult session : raceSessions) {
            for (DriverResult result : session.getDriverResults()) {
                updateStandings(league, result);
            }
        }
        log.info("Recalculated standings for league: {}", league.getName());
    }

    private void updateStandings(League league, DriverResult result) {
        // Update Driver Standings
        DriverStanding ds = driverStandingRepository.findByLeagueAndDriverName(league, result.getDriverName())
                .orElseGet(() -> {
                    DriverStanding newDs = new DriverStanding();
                    newDs.setLeague(league);
                    newDs.setDriverName(result.getDriverName());
                    newDs.setPoints(0);
                    newDs.setWins(0);
                    newDs.setPodiums(0);
                    return newDs;
                });
        ds.setTeamName(result.getTeamName());
        ds.setPoints((ds.getPoints() != null ? ds.getPoints() : 0) + result.getPointsAwarded());
        if (result.getPosition() != null && result.getPosition() == 1) ds.setWins((ds.getWins() != null ? ds.getWins() : 0) + 1);
        if (result.getPosition() != null && result.getPosition() <= 3) ds.setPodiums((ds.getPodiums() != null ? ds.getPodiums() : 0) + 1);
        driverStandingRepository.save(ds);

        // Update Team Standings
        TeamStanding ts = teamStandingRepository.findByLeagueAndTeamName(league, result.getTeamName())
                .orElseGet(() -> {
                    TeamStanding newTs = new TeamStanding();
                    newTs.setLeague(league);
                    newTs.setTeamName(result.getTeamName());
                    newTs.setPoints(0);
                    return newTs;
                });
        ts.setPoints((ts.getPoints() != null ? ts.getPoints() : 0) + result.getPointsAwarded());
        teamStandingRepository.save(ts);
    }
}
