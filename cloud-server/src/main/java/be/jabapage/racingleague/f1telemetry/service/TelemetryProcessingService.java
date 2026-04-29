package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.*;
import be.jabapage.racingleague.f1telemetry.model.*;
import be.jabapage.racingleague.f1telemetry.repository.*;
import be.jabapage.racingleague.f1telemetry.entity.LiveState;
import be.jabapage.racingleague.f1telemetry.repository.LiveStateRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
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
    private DriverMappingRepository driverMappingRepository;
    @Autowired
    private LiveStateRepository liveStateRepository;
    @Autowired
    private Broadcaster broadcaster;
    @Autowired
    private ObjectMapper objectMapper;

    private final Map<String, LeagueSessionState> leagueStates = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> lastLocalUpdate = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Long> lastSavedMap = new java.util.concurrent.ConcurrentHashMap<>();

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 1000)
    public void syncDistributedState() {
        Set<Long> activeLeagueIds = getActiveLeagueIds();
        if (activeLeagueIds.isEmpty()) return;

        // Fetch only states for leagues we care about
        List<LiveState> updates = liveStateRepository.findAllById(activeLeagueIds);
        
        for (LiveState remote : updates) {
            // CRITICAL: If we are the ones receiving UDP for this league, 
            // our memory is newer than the DB. Do NOT overwrite.
            if (isLocallyManaged(remote.getLeagueId())) {
                continue;
            }

            LocalDateTime local = lastLocalUpdate.get(remote.getLeagueId());
            if (local == null || remote.getLastUpdated().isAfter(local)) {
                // Update memory cache for UDP processing
                leagueStates.entrySet().stream()
                        .filter(entry -> Objects.equals(entry.getValue().getLeagueId(), remote.getLeagueId()))
                        .findFirst()
                        .ifPresent(entry -> {
                            try {
                                LeagueSessionState state = objectMapper.readValue(remote.getJsonState(), LeagueSessionState.class);
                                entry.setValue(state);
                                lastLocalUpdate.put(remote.getLeagueId(), remote.getLastUpdated());
                                
                                leagueRepository.findById(remote.getLeagueId()).ifPresent(l -> {
                                    refreshDriverMappings(state, l);
                                    state.setHideAi(l.isHideAi());
                                });

                                broadcastLeaderboard(state);
                                broadcastSessionInfo(state);
                                log.debug("Sync: Updated state for league {} from DB", remote.getLeagueId());
                            } catch (Exception e) {
                                log.error("Sync: Failed to update league {}: {}", remote.getLeagueId(), e.getMessage());
                            }
                        });
                
                // If we don't have it in memory but someone is listening, broadcast to them
                if (!lastLocalUpdate.containsKey(remote.getLeagueId()) || remote.getLastUpdated().isAfter(lastLocalUpdate.getOrDefault(remote.getLeagueId(), LocalDateTime.MIN))) {
                    if (broadcaster.hasListeners(remote.getLeagueId())) {
                         loadAndBroadcast(remote);
                    }
                }
            }
        }
    }

    private boolean isLocallyManaged(Long leagueId) {
        return leagueStates.values().stream()
                .anyMatch(s -> Objects.equals(s.getLeagueId(), leagueId));
    }

    private Set<Long> getActiveLeagueIds() {
        Set<Long> activeIds = new HashSet<>();
        // Add IDs from active UDP sessions
        leagueStates.values().forEach(s -> {
            if (s.getLeagueId() != null && s.getLeagueId() != -1) {
                activeIds.add(s.getLeagueId());
            }
        });
        // Add IDs from active UI listeners (e.g., people watching the dashboard)
        activeIds.addAll(broadcaster.getActiveLeagueIds());
        return activeIds;
    }

    private void loadAndBroadcast(LiveState remote) {
        try {
            LeagueSessionState state = objectMapper.readValue(remote.getJsonState(), LeagueSessionState.class);
            lastLocalUpdate.put(remote.getLeagueId(), remote.getLastUpdated());
            
            leagueRepository.findById(remote.getLeagueId()).ifPresent(l -> {
                refreshDriverMappings(state, l);
                state.setHideAi(l.isHideAi());
                // We need a token to put it in leagueStates. 
                // But we don't know the token here.
                // However, we can just broadcast without putting in leagueStates 
                // if this instance is just a "viewer".
                broadcastLeaderboard(state);
                broadcastSessionInfo(state);
            });
        } catch (Exception e) {
            log.error("Failed to load and broadcast league {}: {}", remote.getLeagueId(), e.getMessage());
        }
    }

    private LeagueSessionState getOrCreateState(String token) {
        return leagueStates.computeIfAbsent(token, t -> {
            Optional<League> league = leagueRepository.findByToken(t);
            if (league.isPresent()) {
                // Try to load from DB first
                League l = league.get();
                Optional<LiveState> liveState = liveStateRepository.findById(l.getId());
                if (liveState.isPresent()) {
                    try {
                        LeagueSessionState state = objectMapper.readValue(liveState.get().getJsonState(), LeagueSessionState.class);
                        // Refresh transient mappings
                        refreshDriverMappings(state, l);
                        state.setHideAi(l.isHideAi());
                        log.info("Loaded live state for league {} from database", l.getId());
                        return state;
                    } catch (Exception e) {
                        log.error("Failed to deserialize live state for league {}: {}", l.getId(), e.getMessage());
                    }
                }

                LeagueSessionState state = new LeagueSessionState(l.getId());
                state.setHideAi(l.isHideAi());
                refreshDriverMappings(state, l);
                return state;
            } else if ("default".equals(t)) {
                // Fallback for default token if no league found
                return new LeagueSessionState(-1L);
            }
            return null;
        });
    }

    private void saveState(LeagueSessionState state) {
        if (state.getLeagueId() == null || state.getLeagueId() == -1) return;

        long now = System.currentTimeMillis();
        long lastSaved = lastSavedMap.getOrDefault(state.getLeagueId(), 0L);

        // Throttle DB writes to once per 1000ms
        if (now - lastSaved > 1000) {
            lastSavedMap.put(state.getLeagueId(), now);
            performAsyncSave(state);
        }
    }

    @org.springframework.scheduling.annotation.Async
    protected void performAsyncSave(LeagueSessionState state) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LiveState liveState = new LiveState();
            liveState.setLeagueId(state.getLeagueId());
            liveState.setLastUpdated(now);
            liveState.setJsonState(objectMapper.writeValueAsString(state));
            liveStateRepository.save(liveState);
            lastLocalUpdate.put(state.getLeagueId(), now);
        } catch (Exception e) {
            log.error("Failed to persist live state for league {}: {}", state.getLeagueId(), e.getMessage());
        }
    }

    private void clearState(Long leagueId) {
        if (leagueId != null && leagueId != -1) {
            liveStateRepository.deleteById(leagueId);
        }
    }

    public void refreshHideAiSetting(Long leagueId) {
        leagueStates.values().stream()
                .filter(s -> Objects.equals(s.getLeagueId(), leagueId))
                .findFirst()
                .ifPresent(state -> {
                    leagueRepository.findById(leagueId).ifPresent(league -> state.setHideAi(league.isHideAi()));
                });
    }

    public void refreshDriverMappings(Long leagueId) {
        leagueStates.values().stream()
                .filter(s -> Objects.equals(s.getLeagueId(), leagueId))
                .findFirst()
                .ifPresent(state -> {
                    leagueRepository.findById(leagueId).ifPresent(league -> refreshDriverMappings(state, league));
                });
    }

    private void refreshDriverMappings(LeagueSessionState state, League league) {
        List<DriverMapping> mappings = driverMappingRepository.findByLeague(league);
        state.getDriverNameOverrides().clear();
        state.getReserveDrivers().clear();
        for (DriverMapping mapping : mappings) {
            String key = mapping.getTelemetryName() + "|" + mapping.getRaceNumber() + "|" + mapping.getDriverId();
            if (mapping.getOverriddenName() != null && !mapping.getOverriddenName().isEmpty()) {
                state.getDriverNameOverrides().put(key, mapping.getOverriddenName());
            }
            if (mapping.isReserve()) {
                state.getReserveDrivers().add(key);
            }
        }
    }

    private String getDriverName(LeagueSessionState state, ParticipantData p) {
        String key = p.getName() + "|" + p.getRaceNumber() + "|" + p.getDriverId();
        String overridden = state.getDriverNameOverrides().get(key);
        if (overridden != null && !overridden.isEmpty()) return overridden;

        return p.getName();
    }

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

    private void autoDiscoverDrivers(LeagueSessionState state, PacketParticipantsData participants) {
        if (state.getLeagueId() == null || state.getLeagueId() == -1) return;

        League league = leagueRepository.findById(state.getLeagueId()).orElse(null);
        if (league == null) return;

        boolean changed = false;
        for (ParticipantData p : participants.getParticipants()) {
            if (p.getName() == null || p.getName().isEmpty()) continue;

            String key = p.getName() + "|" + p.getRaceNumber() + "|" + p.getDriverId();
            if (state.getDriverNameOverrides().containsKey(key)) continue;

            // Check if we already have a mapping (even without override)
            Optional<DriverMapping> mapping = driverMappingRepository.findByLeagueAndTelemetryNameAndRaceNumberAndDriverId(league, p.getName(), p.getRaceNumber(), p.getDriverId());
            if (mapping.isEmpty()) {
                DriverMapping newMapping = new DriverMapping();
                newMapping.setLeague(league);
                newMapping.setTelemetryName(p.getName());
                newMapping.setRaceNumber(p.getRaceNumber());
                newMapping.setDriverId(p.getDriverId());
                driverMappingRepository.save(newMapping);
                // Add to cache with empty override to avoid re-checking
                state.getDriverNameOverrides().put(key, "");
                changed = true;
                log.info("Auto-discovered new driver in league {}: {} (#{}, ID: {})", league.getId(), p.getName(), p.getRaceNumber(), p.getDriverId());
            } else {
                // Already in DB, add to cache to avoid re-checking DB
                state.getDriverNameOverrides().put(key, mapping.get().getOverriddenName() != null ? mapping.get().getOverriddenName() : "");
                if (mapping.get().isReserve()) {
                    state.getReserveDrivers().add(key);
                }
            }
        }
    }

    public synchronized void processPacket(String token, PacketHeader header, ByteBuffer buffer) {
        LeagueSessionState state = getOrCreateState(token);
        if (state == null) {
            log.warn("Received packet for unknown token: {}", token);
            return;
        }

        long now = System.currentTimeMillis();
        long packetSessionUID = header.getSessionUID();

        if (header.getPacketId() == 8) {
            log.info("Incoming Packet 8 (Final Classification) for UID: {} (League: {})", packetSessionUID, state.getLeagueId());
        }

        // Reset session-specific state if session UID changed OR if there was a long gap
        boolean sessionChanged = (packetSessionUID != 0 && packetSessionUID != state.getCurrentSessionUID());
        boolean timeout = (now - state.getLastPacketTime() > 5000 && state.getLastPacketTime() > 0);

        if (sessionChanged || timeout) {
            log.info("{} detected for league {}, resetting live tracking state. (New UID: {}, Old UID: {}, Gap: {}ms)",
                timeout ? "Timeout" : "Session change",
                state.getLeagueId(),
                packetSessionUID, state.getCurrentSessionUID(), (now - state.getLastPacketTime()));
            
            // If it was a real session that just ended/timed out, save what we have before resetting?
            // Usually SEND or Final Classification handles this, but this is a safety net.
            
            state.reset();
            clearState(state.getLeagueId());
            state.setCurrentSessionUID(packetSessionUID);
            // Clear the live UI
            broadcaster.broadcastLeaderboard(state.getLeagueId(), Collections.emptyList());
        }
        state.setLastPacketTime(now);
        lastLocalUpdate.put(state.getLeagueId(), LocalDateTime.now());

        // ... (existing packet matching logic)

        // Skip packets that don't match the session we are currently tracking.
        if (state.getCurrentSessionUID() != -1 && state.getCurrentSessionUID() != 0 && packetSessionUID == 0) {
            return;
        }
        
        if (state.getCurrentSessionUID() != -1 && packetSessionUID != 0 && packetSessionUID != state.getCurrentSessionUID()) {
            return;
        }

        switch (header.getPacketId()) {
            case 1: // Session
                state.setCurrentSession(PacketSessionData.fromByteBuffer(buffer, header));
                broadcastSessionInfo(state);
                break;
            case 2: // Lap Data
                PacketLapData newLapData = PacketLapData.fromByteBuffer(buffer, header);
                processLapData(state, newLapData);
                state.setCurrentLapData(newLapData);
                broadcastLeaderboard(state);
                broadcastSessionInfo(state); // Update lap progress
                break;
            case 3: // Event
                PacketEventData eventData = PacketEventData.fromByteBuffer(buffer, header);
                if ("SEND".equals(eventData.getEventStringCode())) {
                    log.info("Session Ended event (SEND) received for UID: {}. Triggering result save.", header.getSessionUID());
                    saveResultsFromLiveState(state, header.getSessionUID());
                }
                break;
            case 4: // Participants
                PacketParticipantsData participants = PacketParticipantsData.fromByteBuffer(buffer, header);
                state.setCurrentParticipants(participants);
                autoDiscoverDrivers(state, participants);
                break;
            case 7: // Car Status
                state.setCurrentCarStatus(PacketCarStatusData.fromByteBuffer(buffer, header));
                broadcastLeaderboard(state);
                break;
            case 8: // Final Classification
                PacketFinalClassificationData classification = PacketFinalClassificationData.fromByteBuffer(buffer, header);
                handleFinalClassification(state, classification);
                clearState(state.getLeagueId());
                break;
            default:
                break;
        }

        saveState(state);
    }

    private void processLapData(LeagueSessionState state, PacketLapData packet) {
        for (int i = 0; i < packet.getLapData().size(); i++) {
            LapData ld = packet.getLapData().get(i);
            int carIndex = i;

            // Update sector times if they are valid in current lap
            if (ld.getSector() == 1 && ld.getSector1TimeInMS() > 0) {
                long s1 = ld.getSector1TimeInMS();
                state.getLastS1()[carIndex] = s1;
                if (ld.getCurrentLapInvalid() == 0) {
                    if (s1 < state.getDriverBestS1()[carIndex] || state.getDriverBestS1()[carIndex] == 0) state.getDriverBestS1()[carIndex] = s1;
                    if (s1 < state.getSessionBestS1()) state.setSessionBestS1(s1);
                }
            } else if (ld.getSector() == 2 && ld.getSector2TimeInMS() > 0) {
                long s2 = ld.getSector2TimeInMS();
                state.getLastS2()[carIndex] = s2;
                if (ld.getCurrentLapInvalid() == 0) {
                    if (s2 < state.getDriverBestS2()[carIndex] || state.getDriverBestS2()[carIndex] == 0) state.getDriverBestS2()[carIndex] = s2;
                    if (s2 < state.getSessionBestS2()) state.setSessionBestS2(s2);
                }
            }

            // Track if current lap becomes invalid
            if (ld.getCurrentLapInvalid() == 1) {
                state.getLapInvalid()[carIndex] = true;
            }

            // Detect lap completion
            if (state.getLastLapNum()[carIndex] > 0 && ld.getCurrentLapNum() > state.getLastLapNum()[carIndex]) {
                long lastLapTime = ld.getLastLapTimeInMS();
                long s1 = state.getLastS1()[carIndex];
                long s2 = state.getLastS2()[carIndex];
                long s3 = lastLapTime - s1 - s2;

                // Update best lap and S3
                if (!state.getLapInvalid()[carIndex] && lastLapTime > 0) {
                    if (lastLapTime < state.getDriverBestLap()[carIndex] || state.getDriverBestLap()[carIndex] == 0) state.getDriverBestLap()[carIndex] = lastLapTime;
                    if (lastLapTime < state.getSessionBestLap()) state.setSessionBestLap(lastLapTime);
                    
                    if (s3 > 0) {
                        if (s3 < state.getDriverBestS3()[carIndex] || state.getDriverBestS3()[carIndex] == 0) state.getDriverBestS3()[carIndex] = s3;
                        if (s3 < state.getSessionBestS3()) state.setSessionBestS3(s3);
                    }
                }

                // Last lap is completed
                LapResult result = new LapResult();
                result.setSessionUID(packet.getHeader().getSessionUID());
                result.setCarIndex(carIndex);
                result.setLapNumber(state.getLastLapNum()[carIndex]);
                result.setLapTimeInMS(lastLapTime);
                result.setS1InMS(s1);
                result.setS2InMS(s2);
                result.setS3InMS(s3);
                result.setIsValid(!state.getLapInvalid()[carIndex]);
                result.setTyreCompound(state.getLastTyre()[carIndex]);
                result.setPitStopCount(ld.getNumPitStops());

                lapResultRepository.save(result);
            }

            // Update state
            state.getLastLapNum()[carIndex] = ld.getCurrentLapNum();
            if (state.getCurrentCarStatus() != null && carIndex < state.getCurrentCarStatus().getCarStatusData().size()) {
                state.getLastTyre()[carIndex] = state.getCurrentCarStatus().getCarStatusData().get(carIndex).getVisualTyreCompound();
            }
            if (ld.getSector() == 0) { // New lap started
                state.getLapInvalid()[carIndex] = false;
            }
        }
    }

    private void broadcastSessionInfo(LeagueSessionState state) {
        if (state.getCurrentSession() != null) {
            String sessionName = SESSION_TYPE_NAMES.getOrDefault(state.getCurrentSession().getSessionType(), "Unknown (" + state.getCurrentSession().getSessionType() + ")");
            int playerCarIndex = state.getCurrentSession().getHeader().getPlayerCarIndex();
            int currentLap = 0;
            if (state.getCurrentLapData() != null && playerCarIndex < state.getCurrentLapData().getLapData().size()) {
                currentLap = state.getCurrentLapData().getLapData().get(playerCarIndex).getCurrentLapNum();
            }

            boolean isRace = state.getCurrentSession().getSessionType() >= 15 && state.getCurrentSession().getSessionType() <= 17;

            SessionInfo info = SessionInfo.builder()
                    .sessionType(sessionName)
                    .currentLap(currentLap)
                    .totalLaps(state.getCurrentSession().getTotalLaps())
                    .timeLeftSeconds(state.getCurrentSession().getSessionTimeLeft())
                    .isRace(isRace)
                    .safetyCarStatus(state.getCurrentSession().getSafetyCarStatus())
                    .build();
            
            broadcaster.broadcastSessionInfo(state.getLeagueId(), info);
        }
    }

    private void broadcastLeaderboard(LeagueSessionState state) {
        if (state.getCurrentParticipants() == null || state.getCurrentLapData() == null || state.getCurrentCarStatus() == null || state.getCurrentSession() == null) return;
        
        boolean isQualifying = state.getCurrentSession().getSessionType() >= 5 && state.getCurrentSession().getSessionType() <= 14;

        List<DriverBoardState> board = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            if (i >= state.getCurrentParticipants().getParticipants().size() || 
                i >= state.getCurrentLapData().getLapData().size() || 
                i >= state.getCurrentCarStatus().getCarStatusData().size()) break;

            ParticipantData p = state.getCurrentParticipants().getParticipants().get(i);
            if (p.getName() == null || p.getName().isEmpty()) continue;

            LapData ld = state.getCurrentLapData().getLapData().get(i);
            CarStatusData csd = state.getCurrentCarStatus().getCarStatusData().get(i);

            DriverBoardState driverState = new DriverBoardState();
            driverState.setPosition(ld.getCarPosition());
            driverState.setName(getDriverName(state, p));
            driverState.setAi(p.getAiControlled() == 1);
            driverState.setTeam(TEAM_NAMES.getOrDefault(p.getTeamId(), "Unknown"));
            driverState.setTyreCompound(TYRE_COMPOUNDS.getOrDefault(csd.getVisualTyreCompound(), "Unknown"));
            driverState.setTyreAge(csd.getTyresAgeLaps());
            driverState.setPitStops(ld.getNumPitStops());
            driverState.setPenalties(ld.getPenalties());
            driverState.setResultStatus(ld.getResultStatus());
            driverState.setQualifying(isQualifying);

            if (isQualifying) {
                driverState.setBestLapTime(formatLapTimeFull(state.getDriverBestLap()[i]));
                driverState.setS1Time(formatLapTimeFull(state.getDriverBestS1()[i]));
                driverState.setS2Time(formatLapTimeFull(state.getDriverBestS2()[i]));
                driverState.setS3Time(formatLapTimeFull(state.getDriverBestS3()[i]));
                driverState.setBestS1(state.getDriverBestS1()[i] > 0 && state.getDriverBestS1()[i] == state.getSessionBestS1());
                driverState.setBestS2(state.getDriverBestS2()[i] > 0 && state.getDriverBestS2()[i] == state.getSessionBestS2());
                driverState.setBestS3(state.getDriverBestS3()[i] > 0 && state.getDriverBestS3()[i] == state.getSessionBestS3());
                
                if (state.getDriverBestLap()[i] > 0 && state.getSessionBestLap() > 0) {
                    driverState.setGapToLeaderBest(formatTime(state.getDriverBestLap()[i] - state.getSessionBestLap()));
                } else {
                    driverState.setGapToLeaderBest("-");
                }
            } else {
                driverState.setGapToLeader(formatTime(ld.getDeltaToRaceLeaderInMS()));
                driverState.setGapToFront(formatTime(ld.getDeltaToCarInFrontInMS()));
            }
            
            board.add(driverState);
        }
        
        if (state.isHideAi()) {
            board = board.stream().filter(s -> !s.isAi()).collect(Collectors.toList());
        }

        board.sort(Comparator.comparingInt(DriverBoardState::getPosition));
        broadcaster.broadcastLeaderboard(state.getLeagueId(), board);
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
            stats.setAi(dr.isAi());
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
    public void handleFinalClassification(LeagueSessionState state, PacketFinalClassificationData classification) {
        long sessionUID = classification.getHeader().getSessionUID();
        log.info("Received Final Classification packet (packet 8) for session UID: {}", sessionUID);
        
        if (state.getLeagueId() == null || state.getLeagueId() == -1) {
            log.warn("Cannot save results: No valid league associated with state.");
            return;
        }
        if (state.getCurrentSession() == null || state.getCurrentParticipants() == null) {
            log.warn("Cannot save results: Session or Participants data missing for UID: {}. (Session: {}, Participants: {})", 
                sessionUID,
                state.getCurrentSession() != null ? "OK" : "MISSING", 
                state.getCurrentParticipants() != null ? "OK" : "MISSING");
            return;
        }

        League league = leagueRepository.findByIdWithEvents(state.getLeagueId()).orElse(null);
        if (league == null) {
            log.warn("Cannot save results: Activated league ID {} not found in database.", state.getLeagueId());
            return;
        }

        // Check if session already recorded
        int sessionType = state.getCurrentSession().getSessionType();
        Optional<SessionResult> existing = sessionResultRepository.findBySessionUIDAndSessionType(sessionUID, sessionType);
        if (existing.isPresent()) {
            SessionResult sr = existing.get();
            log.info("Session UID: {} with Type: {} already recorded as ID: {}. Skipping.", 
                sessionUID, sessionType, sr.getId());
            return;
        }

        // Find or create event
        String trackIdStr = String.valueOf(state.getCurrentSession().getTrackId());
        Event event = eventRepository.findByLeagueAndTrackId(league, trackIdStr)
                .orElseGet(() -> {
                    Event newEvent = new Event();
                    newEvent.setLeague(league);
                    newEvent.setTrackId(trackIdStr);
                    newEvent.setEventName(TRACK_NAMES.getOrDefault(state.getCurrentSession().getTrackId(), "Track " + trackIdStr));
                    log.info("Creating new event: {} for track: {}", newEvent.getEventName(), trackIdStr);
                    return eventRepository.save(newEvent);
                });

        log.info("Processing {} results for session UID: {} (Type: {})", 
            classification.getNumCars(), sessionUID, state.getCurrentSession().getSessionType());

        SessionResult sessionResult = new SessionResult();
        sessionResult.setLeague(league);
        sessionResult.setEvent(event);
        sessionResult.setSessionUID(classification.getHeader().getSessionUID());
        sessionResult.setSessionType(state.getCurrentSession().getSessionType());
        sessionResult.setTrackId(trackIdStr);

        boolean isRace = state.getCurrentSession().getSessionType() >= 15 && state.getCurrentSession().getSessionType() <= 17;

        for (int i = 0; i < classification.getNumCars(); i++) {
            FinalClassificationData data = classification.getClassificationData().get(i);
            if (data.getResultStatus() == 0) continue; // Inactive/Invalid

            ParticipantData participant = state.getCurrentParticipants().getParticipants().get(i);
            
            DriverResult driverResult = new DriverResult();
            driverResult.setSessionResult(sessionResult);
            driverResult.setDriverName(getDriverName(state, participant));
            driverResult.setTelemetryName(participant.getName());
            driverResult.setRaceNumber(participant.getRaceNumber());
            driverResult.setDriverId(participant.getDriverId());
            driverResult.setAi(participant.getAiControlled() == 1);
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
                String key = driverResult.getTelemetryName() + "|" + driverResult.getRaceNumber() + "|" + driverResult.getDriverId();
                boolean isReserve = state.getReserveDrivers().contains(key);
                updateStandings(league, driverResult, isReserve);
            }
        }

        log.info("Saved {} results for session UID: {} in event: {}", 
                isRace ? "Race" : "Qualifying", sessionResult.getSessionUID(), event.getEventName());
    }

    @Transactional
    public void saveResultsFromLiveState(LeagueSessionState state, long sessionUID) {
        if (state.getLeagueId() == null || state.getLeagueId() == -1 || state.getCurrentSession() == null || state.getCurrentParticipants() == null || state.getCurrentLapData() == null) {
            log.warn("Cannot save live results: Missing critical context (League, Session, Participants or LapData)");
            return;
        }

        // Check if session already recorded
        int sessionType = state.getCurrentSession().getSessionType();
        Optional<SessionResult> existing = sessionResultRepository.findBySessionUIDAndSessionType(sessionUID, sessionType);
        if (existing.isPresent()) {
            return; // Already saved (maybe by Packet 8 that arrived just before SEND)
        }

        League league = leagueRepository.findByIdWithEvents(state.getLeagueId()).orElse(null);
        if (league == null) return;

        String trackIdStr = String.valueOf(state.getCurrentSession().getTrackId());
        Event event = eventRepository.findByLeagueAndTrackId(league, trackIdStr)
                .orElseGet(() -> {
                    Event newEvent = new Event();
                    newEvent.setLeague(league);
                    newEvent.setTrackId(trackIdStr);
                    newEvent.setEventName(TRACK_NAMES.getOrDefault(state.getCurrentSession().getTrackId(), "Track " + trackIdStr));
                    return eventRepository.save(newEvent);
                });

        SessionResult sessionResult = new SessionResult();
        sessionResult.setLeague(league);
        sessionResult.setEvent(event);
        sessionResult.setSessionUID(sessionUID);
        sessionResult.setSessionType(sessionType);
        sessionResult.setTrackId(trackIdStr);

        boolean isRace = sessionType >= 15 && sessionType <= 17;

        for (int i = 0; i < state.getCurrentParticipants().getParticipants().size(); i++) {
            ParticipantData participant = state.getCurrentParticipants().getParticipants().get(i);
            if (participant.getName() == null || participant.getName().isEmpty()) continue;

            if (i >= state.getCurrentLapData().getLapData().size()) break;
            LapData ld = state.getCurrentLapData().getLapData().get(i);
            
            // Skip inactive drivers
            if (ld.getResultStatus() == 0 || ld.getResultStatus() == 1) continue;

            DriverResult driverResult = new DriverResult();
            driverResult.setSessionResult(sessionResult);
            driverResult.setDriverName(getDriverName(state, participant));
            driverResult.setTelemetryName(participant.getName());
            driverResult.setRaceNumber(participant.getRaceNumber());
            driverResult.setDriverId(participant.getDriverId());
            driverResult.setAi(participant.getAiControlled() == 1);
            driverResult.setTeamName(TEAM_NAMES.getOrDefault(participant.getTeamId(), "Unknown"));
            driverResult.setPosition(ld.getCarPosition());
            driverResult.setGridPosition(ld.getGridPosition());
            driverResult.setBestLapTime(state.getDriverBestLap()[i] / 1000.0f);
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
                String key = driverResult.getTelemetryName() + "|" + driverResult.getRaceNumber() + "|" + driverResult.getDriverId();
                boolean isReserve = state.getReserveDrivers().contains(key);
                updateStandings(league, driverResult, isReserve);
            }
        }

        clearState(state.getLeagueId());

        log.info("Saved Fallback {} results (from live state) for session UID: {} in event: {}", 
                isRace ? "Race" : "Qualifying", sessionUID, event.getEventName());
    }

    @Transactional
    public void updateDriverNamesFromMappings(Long leagueId) {
        League league = leagueRepository.findByIdWithEvents(leagueId).orElse(null);
        if (league == null) return;

        List<DriverMapping> mappings = driverMappingRepository.findByLeague(league);
        Map<String, String> nameMap = mappings.stream()
                .filter(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty())
                .collect(Collectors.toMap(
                        m -> m.getTelemetryName() + "|" + m.getRaceNumber() + "|" + m.getDriverId(),
                        DriverMapping::getOverriddenName,
                        (existing, replacement) -> existing
                ));

        List<SessionResult> allSessions = league.getEvents().stream()
                .flatMap(e -> e.getSessionResults().stream())
                .collect(Collectors.toList());

        for (SessionResult session : allSessions) {
            for (DriverResult result : session.getDriverResults()) {
                if (result.getTelemetryName() != null && result.getRaceNumber() != null && result.getDriverId() != null) {
                    String key = result.getTelemetryName() + "|" + result.getRaceNumber() + "|" + result.getDriverId();
                    String nameToUse = nameMap.getOrDefault(key, result.getTelemetryName());
                    if (!nameToUse.equals(result.getDriverName())) {
                        result.setDriverName(nameToUse);
                    }
                }
            }
        }
        log.info("Updated driver names in all results for league: {}", league.getId());
    }

    @Transactional
    public void recalculateStandings(Long leagueId) {
        League league = leagueRepository.findByIdWithEvents(leagueId).orElse(null);
        if (league == null) return;

        // Load all mappings for this league
        List<DriverMapping> mappings = driverMappingRepository.findByLeague(league);
        Map<String, String> nameMap = mappings.stream()
                .filter(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty())
                .collect(Collectors.toMap(
                        m -> m.getTelemetryName() + "|" + m.getRaceNumber() + "|" + m.getDriverId(),
                        DriverMapping::getOverriddenName,
                        (existing, replacement) -> existing
                ));

        java.util.Set<String> reserveSet = mappings.stream()
                .filter(DriverMapping::isReserve)
                .map(m -> m.getTelemetryName() + "|" + m.getRaceNumber() + "|" + m.getDriverId())
                .collect(Collectors.toSet());

        // Clear existing standings
        driverStandingRepository.deleteAll(driverStandingRepository.findByLeague(league));
        teamStandingRepository.deleteAll(teamStandingRepository.findByLeague(league));

        // Get all sessions from events
        List<SessionResult> allSessions = league.getEvents().stream()
                .flatMap(e -> e.getSessionResults().stream())
                .collect(Collectors.toList());

        for (SessionResult session : allSessions) {
            boolean isRace = session.getSessionType() >= 15 && session.getSessionType() <= 17;
            for (DriverResult result : session.getDriverResults()) {
                // Determine the correct name to use
                String nameToUse = result.getDriverName();
                if (result.getTelemetryName() != null && result.getRaceNumber() != null && result.getDriverId() != null) {
                    String key = result.getTelemetryName() + "|" + result.getRaceNumber() + "|" + result.getDriverId();
                    nameToUse = nameMap.getOrDefault(key, result.getTelemetryName());
                }

                // Update the DriverResult itself so it stays consistent in the UI
                if (!nameToUse.equals(result.getDriverName())) {
                    result.setDriverName(nameToUse);
                }

                // Only update standings if it's a race
                if (isRace) {
                    boolean isReserve = false;
                    if (result.getTelemetryName() != null && result.getRaceNumber() != null && result.getDriverId() != null) {
                        isReserve = reserveSet.contains(result.getTelemetryName() + "|" + result.getRaceNumber() + "|" + result.getDriverId());
                    }
                    updateStandings(league, result, isReserve);
                }
            }
        }
        log.info("Recalculated standings for league: {}", league.getName());
    }

    private void updateStandings(League league, DriverResult result, boolean isReserve) {
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
        ds.setAi(result.isAi());
        ds.setReserve(isReserve);
        
        // Handle multiple teams for a driver
        String currentTeams = ds.getTeamName();
        String newTeam = result.getTeamName();
        if (isReserve) {
            ds.setTeamName("Reserve Driver");
        } else if (currentTeams == null || currentTeams.isEmpty() || "Reserve Driver".equals(currentTeams)) {
            ds.setTeamName(newTeam);
        } else if (!currentTeams.contains(newTeam)) {
            ds.setTeamName(currentTeams + ", " + newTeam);
        }

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
