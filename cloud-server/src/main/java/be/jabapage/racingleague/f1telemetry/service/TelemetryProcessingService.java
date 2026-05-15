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
    private DriverResultRepository driverResultRepository;
    @Autowired
    private LapResultRepository lapResultRepository;
    @Autowired
    private DriverMappingRepository driverMappingRepository;
    @Autowired
    private LiveStateRepository liveStateRepository;
    @Autowired
    private SessionPointConfigRepository sessionPointConfigRepository;
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
            LocalDateTime local = lastLocalUpdate.get(remote.getLeagueId());
            if (local == null || remote.getLastUpdated().isAfter(local)) {
                // Update memory cache for UDP processing
                leagueStates.entrySet().stream()
                        .filter(entry -> Objects.equals(entry.getValue().getLeagueId(), remote.getLeagueId()))
                        .findFirst()
                        .ifPresentOrElse(entry -> {
                            try {
                                String json = decompress(remote.getCompressedState());
                                if (json.isEmpty()) return;
                                LeagueSessionState remoteState = objectMapper.readValue(json, LeagueSessionState.class);
                                LeagueSessionState localState = entry.getValue();

                                // Merge logic: If we are actively receiving packets, only fill in what we are missing
                                // or update if the remote is a newer session.
                                boolean remoteIsNewerSession = remoteState.getCurrentSessionUID() != localState.getCurrentSessionUID() && remoteState.getCurrentSessionUID() != -1;
                                boolean localIsActivelyReceiving = System.currentTimeMillis() - localState.getLastPacketTime() < 2000;

                                if (remoteIsNewerSession || !localIsActivelyReceiving) {
                                    entry.setValue(remoteState);
                                    log.debug("Sync: Updated state for league {} (Remote is newer or local is idle)", remote.getLeagueId());
                                } else {
                                    // Merge critical fields if missing locally
                                    boolean merged = false;
                                    if (localState.getCurrentSession() == null && remoteState.getCurrentSession() != null) {
                                        localState.setCurrentSession(remoteState.getCurrentSession());
                                        merged = true;
                                    }
                                    if (localState.getCurrentParticipants() == null && remoteState.getCurrentParticipants() != null) {
                                        localState.setCurrentParticipants(remoteState.getCurrentParticipants());
                                        merged = true;
                                    }
                                    if (merged) {
                                        log.info("Sync: Merged missing Session/Participants for league {} from DB", remote.getLeagueId());
                                    }
                                }

                                lastLocalUpdate.put(remote.getLeagueId(), remote.getLastUpdated());
                                
                                leagueRepository.findById(remote.getLeagueId()).ifPresent(l -> {
                                    refreshDriverMappings(entry.getValue(), l);
                                    entry.getValue().setHideAi(l.isHideAi());
                                    entry.getValue().setShowTyreWear(l.isShowTyreWear());
                                    entry.getValue().setShowErs(l.isShowErs());
                                });

                                broadcastLeaderboard(entry.getValue());
                                broadcastSessionInfo(entry.getValue());
                            } catch (Exception e) {
                                log.error("Sync: Failed to update league {}: {}", remote.getLeagueId(), e.getMessage());
                            }
                        }, () -> {
                            // If we don't have it in memory but someone is listening, broadcast to them
                            if (broadcaster.hasListeners(remote.getLeagueId())) {
                                loadAndBroadcast(remote);
                            }
                        });
            }
        }
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
            String json = decompress(remote.getCompressedState());
            if (json.isEmpty()) return;
            LeagueSessionState state = objectMapper.readValue(json, LeagueSessionState.class);
            lastLocalUpdate.put(remote.getLeagueId(), remote.getLastUpdated());
            
            leagueRepository.findById(remote.getLeagueId()).ifPresent(l -> {
                refreshDriverMappings(state, l);
                state.setHideAi(l.isHideAi());
                state.setShowTyreWear(l.isShowTyreWear());
                state.setShowErs(l.isShowErs());
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
                        String json = decompress(liveState.get().getCompressedState());
                        if (!json.isEmpty()) {
                            LeagueSessionState state = objectMapper.readValue(json, LeagueSessionState.class);
                            // Refresh transient mappings
                            refreshDriverMappings(state, l);
                            state.setHideAi(l.isHideAi());
                            state.setShowTyreWear(l.isShowTyreWear());
                            state.setShowErs(l.isShowErs());
                            log.info("Loaded live state for league {} from database", l.getId());
                            return state;
                        }
                    } catch (Exception e) {
                        log.error("Failed to deserialize live state for league {}: {}", l.getId(), e.getMessage());
                    }
                }

                LeagueSessionState state = new LeagueSessionState(l.getId());
                state.setHideAi(l.isHideAi());
                state.setShowTyreWear(l.isShowTyreWear());
                state.setShowErs(l.isShowErs());
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
            
            String json = objectMapper.writeValueAsString(state);
            liveState.setCompressedState(compress(json));
            
            liveStateRepository.save(liveState);
            lastLocalUpdate.put(state.getLeagueId(), now);
        } catch (Exception e) {
            log.error("Failed to persist live state for league {}: {}", state.getLeagueId(), e.getMessage());
        }
    }

    private byte[] compress(String data) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(data.length());
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(bos)) {
            gzip.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }

    private String decompress(byte[] compressed) throws java.io.IOException {
        if (compressed == null || compressed.length == 0) {
            return "";
        }
        try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(compressed);
             java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(bis)) {
            return new String(gzip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
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

    private int getPointsForPosition(League league, int sessionType, int position) {
        // Check for custom point configuration for this session type and position
        List<SessionPointConfig> configs = sessionPointConfigRepository.findByLeague(league);
        Optional<SessionPointConfig> config = configs.stream()
                .filter(c -> c.getSessionType() == sessionType && c.getPosition() == position)
                .findFirst();

        if (config.isPresent()) {
            return config.get().getPoints();
        }

        // Fallback to standard F1 points for Race sessions (15, 16, 17)
        boolean isRace = sessionType >= 15 && sessionType <= 17;
        if (isRace && position >= 1 && position <= 10) {
            int[] pointsMap = {0, 25, 18, 15, 12, 10, 8, 6, 4, 2, 1};
            return pointsMap[position];
        }

        return 0;
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
        for (int i = 0; i < participants.getParticipants().size(); i++) {
            ParticipantData p = participants.getParticipants().get(i);
            if (p.getName() == null || p.getName().isEmpty()) continue;

            // Track if this car was EVER controlled by a human during this session.
            // In online races, if a human DNFs/Retires, aiControlled might switch to 1.
            if (p.getAiControlled() == 0 && i < state.getIsHuman().length) {
                state.getIsHuman()[i] = true;
            }

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

    private boolean isAi(LeagueSessionState state, ParticipantData p, int carIndex) {
        // If we EVER saw a human in this car slot, it's NOT an AI for league purposes.
        if (carIndex >= 0 && carIndex < state.getIsHuman().length && state.getIsHuman()[carIndex]) {
            return false;
        }
        return p.getAiControlled() == 1;
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
                } else if ("DRSE".equals(eventData.getEventStringCode())) {
                    log.info("DRS Enabled event received for league {}", state.getLeagueId());
                    state.setDrsEnabled(true);
                    broadcastSessionInfo(state);
                } else if ("DRSD".equals(eventData.getEventStringCode())) {
                    log.info("DRS Disabled event received for league {}", state.getLeagueId());
                    state.setDrsEnabled(false);
                    broadcastSessionInfo(state);
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
            case 10: // Car Damage
                state.setCurrentCarDamageData(PacketCarDamageData.fromByteBuffer(buffer, header));
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
        SessionInfo info = buildSessionInfo(state);
        if (info != null) {
            broadcaster.broadcastSessionInfo(state.getLeagueId(), info);
        }
    }

    public SessionInfo getSessionInfo(Long leagueId) {
        return leagueStates.values().stream()
                .filter(s -> Objects.equals(s.getLeagueId(), leagueId))
                .findFirst()
                .map(this::buildSessionInfo)
                .orElse(null);
    }

    private SessionInfo buildSessionInfo(LeagueSessionState state) {
        if (state.getCurrentSession() == null) return null;

        String sessionName = SESSION_TYPE_NAMES.getOrDefault(state.getCurrentSession().getSessionType(), "Unknown (" + state.getCurrentSession().getSessionType() + ")");
        int playerCarIndex = state.getCurrentSession().getHeader().getPlayerCarIndex();
        int currentLap = 0;
        if (state.getCurrentLapData() != null && playerCarIndex < state.getCurrentLapData().getLapData().size()) {
            currentLap = state.getCurrentLapData().getLapData().get(playerCarIndex).getCurrentLapNum();
        }

        boolean isRace = state.getCurrentSession().getSessionType() >= 15 && state.getCurrentSession().getSessionType() <= 17;

        return SessionInfo.builder()
                .sessionType(sessionName)
                .currentLap(currentLap)
                .totalLaps(state.getCurrentSession().getTotalLaps())
                .timeLeftSeconds(state.getCurrentSession().getSessionTimeLeft())
                .isRace(isRace)
                .safetyCarStatus(state.getCurrentSession().getSafetyCarStatus())
                .drsEnabled(state.isDrsEnabled())
                .weather(state.getCurrentSession().getWeather())
                .airTemperature(state.getCurrentSession().getAirTemperature())
                .trackTemperature(state.getCurrentSession().getTrackTemperature())
                .weatherForecast(state.getCurrentSession().getWeatherForecastSamples())
                .build();
    }

    private void broadcastLeaderboard(LeagueSessionState state) {
        List<DriverBoardState> board = buildLeaderboard(state);
        if (board != null) {
            broadcaster.broadcastLeaderboard(state.getLeagueId(), board);
        }
    }

    public List<DriverBoardState> getLeaderboard(Long leagueId) {
        return leagueStates.values().stream()
                .filter(s -> Objects.equals(s.getLeagueId(), leagueId))
                .findFirst()
                .map(this::buildLeaderboard)
                .orElse(Collections.emptyList());
    }

    private List<DriverBoardState> buildLeaderboard(LeagueSessionState state) {
        if (state.getCurrentParticipants() == null || state.getCurrentLapData() == null || state.getCurrentCarStatus() == null || state.getCurrentSession() == null) return null;

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
            driverState.setRaceNumber(p.getRaceNumber());
            driverState.setAi(isAi(state, p, i));
            driverState.setTeam(TEAM_NAMES.getOrDefault(p.getTeamId(), "Unknown"));
            driverState.setTyreCompound(TYRE_COMPOUNDS.getOrDefault(csd.getVisualTyreCompound(), "Unknown"));
            driverState.setTyreAge(csd.getTyresAgeLaps());
            driverState.setPitStops(ld.getNumPitStops());
            driverState.setPenalties(ld.getPenalties());
            driverState.setWarnings(ld.getTotalWarnings());

            if (state.isShowErs() && csd != null) {
                driverState.setErsPercentage((int) (csd.getErsStoreEnergy() / 4000000.0 * 100.0));
                driverState.setErsActive(csd.getErsDeployMode() == 3); // Overtake mode
            }

            if (state.isShowTyreWear() && state.getCurrentCarDamageData() != null && i < state.getCurrentCarDamageData().getCarDamageData().size()) {
                CarDamageData cdd = state.getCurrentCarDamageData().getCarDamageData().get(i);
                float maxWear = 0;
                for (float wear : cdd.getTyresWear()) {
                    if (wear > maxWear) maxWear = wear;
                }
                driverState.setTyreWear((int) maxWear);
            }

            driverState.setResultStatus(ld.getResultStatus());
            driverState.setQualifying(isQualifying);
            driverState.setShowTyreWear(state.isShowTyreWear());
            driverState.setShowErs(state.isShowErs());

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
        return board;
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

    private record SectorData(long time, String tyre) {}
    private record WeightedResult(double pace, Map<String, Double> tyreWeights) {}

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

        // Find absolute best sectors in the session for performance scoring (Ultimate Lap)
        double absoluteBestS1 = raceSession.getDriverResults().stream()
                .flatMap(dr -> dr.getLapResults().stream())
                .filter(LapResult::getIsValid)
                .mapToLong(LapResult::getS1InMS)
                .filter(t -> t > 0)
                .min().orElse(0) / 1000.0;
        double absoluteBestS2 = raceSession.getDriverResults().stream()
                .flatMap(dr -> dr.getLapResults().stream())
                .filter(LapResult::getIsValid)
                .mapToLong(LapResult::getS2InMS)
                .filter(t -> t > 0)
                .min().orElse(0) / 1000.0;
        double absoluteBestS3 = raceSession.getDriverResults().stream()
                .flatMap(dr -> dr.getLapResults().stream())
                .filter(LapResult::getIsValid)
                .mapToLong(LapResult::getS3InMS)
                .filter(t -> t > 0)
                .min().orElse(0) / 1000.0;
        double absoluteBestLap = absoluteBestS1 + absoluteBestS2 + absoluteBestS3;

        List<RacePaceStats> statsList = new ArrayList<>();
        int seg1End = maxLaps / 3;
        int seg2End = 2 * maxLaps / 3;

        for (DriverResult dr : raceSession.getDriverResults()) {
            List<LapResult> validLaps = dr.getLapResults().stream()
                    .filter(LapResult::getIsValid)
                    .collect(Collectors.toList());

            // Only drivers who driven at least 60%
            if (dr.getLapResults().size() < maxLaps * 0.6) continue;

            RacePaceStats stats = new RacePaceStats();
            stats.setDriverName(dr.getDriverName());
            stats.setAi(dr.isAi());
            stats.setTeamName(dr.getTeamName());

            // Process segments independently
            List<LapResult> seg1Laps = validLaps.stream().filter(l -> l.getLapNumber() <= seg1End).toList();
            List<LapResult> seg2Laps = validLaps.stream().filter(l -> l.getLapNumber() > seg1End && l.getLapNumber() <= seg2End).toList();
            List<LapResult> seg3Laps = validLaps.stream().filter(l -> l.getLapNumber() > seg2End).toList();

            Map<String, Double> tyreWeightAggregator = new HashMap<>();
            
            double s1 = processSectorWithSegments(seg1Laps, seg2Laps, seg3Laps, LapResult::getS1InMS, tyreWeightAggregator);
            double s2 = processSectorWithSegments(seg1Laps, seg2Laps, seg3Laps, LapResult::getS2InMS, tyreWeightAggregator);
            double s3 = processSectorWithSegments(seg1Laps, seg2Laps, seg3Laps, LapResult::getS3InMS, tyreWeightAggregator);

            stats.setS1Pace(s1 / 1000.0);
            stats.setS2Pace(s2 / 1000.0);
            stats.setS3Pace(s3 / 1000.0);
            stats.setPureRacePace((s1 + s2 + s3) / 1000.0);

            // Tyre usage (percentage based on weight contribution)
            double totalWeightSum = tyreWeightAggregator.values().stream().mapToDouble(Double::doubleValue).sum();
            Map<String, Double> tyreUsage = new HashMap<>();
            if (totalWeightSum > 0) {
                for (Map.Entry<String, Double> entry : tyreWeightAggregator.entrySet()) {
                    tyreUsage.put(entry.getKey(), (entry.getValue() / totalWeightSum) * 100.0);
                }
            }
            stats.setTyreUsage(tyreUsage);

            statsList.add(stats);
        }

        // Performance calculations relative to Ultimate Lap (10.0) and Session Average (5.0)
        calculatePerformances(statsList, absoluteBestLap, absoluteBestS1, absoluteBestS2, absoluteBestS3);

        return statsList.stream()
                .sorted(Comparator.comparingDouble(RacePaceStats::getPureRacePace))
                .collect(Collectors.toList());
    }

    private void calculatePerformances(List<RacePaceStats> statsList, double bestLap, double bestS1, double bestS2, double bestS3) {
        if (statsList.isEmpty()) return;

        // Overall
        calculateSinglePerformance(statsList, RacePaceStats::getPureRacePace, RacePaceStats::setSectorPerformance, bestLap);
        // S1
        calculateSinglePerformance(statsList, RacePaceStats::getS1Pace, RacePaceStats::setS1Performance, bestS1);
        // S2
        calculateSinglePerformance(statsList, RacePaceStats::getS2Pace, RacePaceStats::setS2Performance, bestS2);
        // S3
        calculateSinglePerformance(statsList, RacePaceStats::getS3Pace, RacePaceStats::setS3Performance, bestS3);
    }

    private void calculateSinglePerformance(List<RacePaceStats> statsList, java.util.function.ToDoubleFunction<RacePaceStats> getter, java.util.function.BiConsumer<RacePaceStats, Double> setter, double best) {
        double avg = statsList.stream().mapToDouble(getter).filter(v -> v > 0).average().orElse(0);
        
        for (RacePaceStats s : statsList) {
            double val = getter.applyAsDouble(s);
            if (val <= 0 || best <= 0) {
                setter.accept(s, 0.0);
                continue;
            }
            // If avg <= best (unlikely), everyone gets 10.0
            if (avg <= best) {
                setter.accept(s, 10.0);
            } else {
                // Formula: 10.0 is Ultimate Best, 5.0 is Average Pure Pace
                double perf = 10.0 - 5.0 * (val - best) / (avg - best);
                setter.accept(s, Math.max(0, Math.min(10.0, perf)));
            }
        }
    }

    private double processSectorWithSegments(List<LapResult> s1, List<LapResult> s2, List<LapResult> s3, java.util.function.ToLongFunction<LapResult> sectorGetter, Map<String, Double> tyreWeightAggregator) {
        List<WeightedResult> results = new ArrayList<>();
        
        results.add(calculateWeightedSector(s1.stream().map(l -> new SectorData(sectorGetter.applyAsLong(l), String.valueOf(l.getTyreCompound()))).toList()));
        results.add(calculateWeightedSector(s2.stream().map(l -> new SectorData(sectorGetter.applyAsLong(l), String.valueOf(l.getTyreCompound()))).toList()));
        results.add(calculateWeightedSector(s3.stream().map(l -> new SectorData(sectorGetter.applyAsLong(l), String.valueOf(l.getTyreCompound()))).toList()));

        double totalPace = 0;
        int count = 0;
        for (WeightedResult wr : results) {
            if (wr.pace() > 0) {
                totalPace += wr.pace();
                count++;
                wr.tyreWeights().forEach((k, v) -> tyreWeightAggregator.put(k, tyreWeightAggregator.getOrDefault(k, 0.0) + v));
            }
        }
        
        return count > 0 ? totalPace / count : 0;
    }

    private WeightedResult calculateWeightedSector(List<SectorData> data) {
        // Filter out zero times
        List<SectorData> filtered = data.stream().filter(d -> d.time() > 0).collect(Collectors.toList());
        if (filtered.isEmpty()) return new WeightedResult(0, Collections.emptyMap());
        
        filtered.sort(Comparator.comparingLong(SectorData::time));

        int n = filtered.size();
        // Legend: 30% best fully taken, next 30% decreases linearly to 0.
        double n30 = n * 0.3;
        double n60 = n * 0.6;
        
        double totalWeight = 0;
        double weightedSum = 0;
        Map<String, Double> tyreWeights = new HashMap<>();

        for (int i = 0; i < n; i++) {
            double weight = 0;
            int rank = i + 1; // 1-based index

            if (rank <= n30) {
                weight = 1.0;
            } else if (rank <= n60) {
                // Linear decay from 1.0 to 0.0 across the second 30%
                if (n60 > n30) {
                    weight = (n60 - rank) / (n60 - n30);
                    if (weight < 0) weight = 0;
                } else {
                    weight = 0;
                }
            } else {
                weight = 0;
            }

            if (weight > 0) {
                weightedSum += filtered.get(i).time() * weight;
                totalWeight += weight;
                
                try {
                    String compound = TYRE_COMPOUNDS.getOrDefault(Integer.valueOf(filtered.get(i).tyre()), "U");
                    tyreWeights.put(compound, tyreWeights.getOrDefault(compound, 0.0) + weight);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }

        return new WeightedResult(totalWeight > 0 ? weightedSum / totalWeight : 0, tyreWeights);
    }

    public List<ConsistencyStats> calculateConsistency(Long eventId) {
        Event event = eventRepository.findByIdWithResults(eventId).orElse(null);
        if (event == null) return Collections.emptyList();

        SessionResult raceSession = event.getSessionResults().stream()
                .filter(s -> s.getSessionType() >= 15 && s.getSessionType() <= 17)
                .findFirst().orElse(null);
        if (raceSession == null) return Collections.emptyList();

        List<ConsistencyStats> statsList = new ArrayList<>();

        for (DriverResult dr : raceSession.getDriverResults()) {
            List<LapResult> laps = dr.getLapResults().stream()
                    .filter(LapResult::getIsValid)
                    .sorted(Comparator.comparingInt(LapResult::getLapNumber))
                    .collect(Collectors.toList());

            if (laps.size() < 3) continue;

            ConsistencyStats stats = new ConsistencyStats();
            stats.setDriverName(dr.getDriverName());
            stats.setAi(dr.isAi());
            stats.setTeamName(dr.getTeamName());

            stats.setS1AvgDiff(calculateProcessedSectorDiff(laps.stream().mapToLong(LapResult::getS1InMS).toArray()));
            stats.setS2AvgDiff(calculateProcessedSectorDiff(laps.stream().mapToLong(LapResult::getS2InMS).toArray()));
            stats.setS3AvgDiff(calculateProcessedSectorDiff(laps.stream().mapToLong(LapResult::getS3InMS).toArray()));
            stats.setAvgDiff((stats.getS1AvgDiff() + stats.getS2AvgDiff() + stats.getS3AvgDiff()) / 1000.0);

            statsList.add(stats);
        }

        if (!statsList.isEmpty()) {
            // Overall rating calculation (normalized 0-100 based on total avg diff)
            double bestTotal = statsList.stream().mapToDouble(ConsistencyStats::getAvgDiff).min().orElse(0);
            double worstTotal = statsList.stream().mapToDouble(ConsistencyStats::getAvgDiff).max().orElse(1);
            
            // Sector rating calculation
            double bestS1 = statsList.stream().mapToDouble(ConsistencyStats::getS1AvgDiff).min().orElse(0);
            double worstS1 = statsList.stream().mapToDouble(ConsistencyStats::getS1AvgDiff).max().orElse(1);
            double bestS2 = statsList.stream().mapToDouble(ConsistencyStats::getS2AvgDiff).min().orElse(0);
            double worstS2 = statsList.stream().mapToDouble(ConsistencyStats::getS2AvgDiff).max().orElse(1);
            double bestS3 = statsList.stream().mapToDouble(ConsistencyStats::getS3AvgDiff).min().orElse(0);
            double worstS3 = statsList.stream().mapToDouble(ConsistencyStats::getS3AvgDiff).max().orElse(1);

            for (ConsistencyStats s : statsList) {
                s.setRating(calculateNormalizedRating(s.getAvgDiff(), bestTotal, worstTotal));
                s.setS1Rating(calculateNormalizedRating(s.getS1AvgDiff(), bestS1, worstS1));
                s.setS2Rating(calculateNormalizedRating(s.getS2AvgDiff(), bestS2, worstS2));
                s.setS3Rating(calculateNormalizedRating(s.getS3AvgDiff(), bestS3, worstS3));
            }
        }

        return statsList.stream()
                .sorted(Comparator.comparingDouble(ConsistencyStats::getRating).reversed())
                .collect(Collectors.toList());
    }

    private double calculateNormalizedRating(double val, double best, double worst) {
        if (worst == best) return 100.0;
        double r = 100.0 * (1.0 - (val - best) / (worst - best));
        return Math.max(0, Math.min(100.0, r));
    }

    private double calculateProcessedSectorDiff(long[] times) {
        if (times.length < 2) return 0;

        List<Double> diffs2 = new ArrayList<>();
        for (int i = 1; i < times.length; i++) {
            if (times[i] <= 0 || times[i-1] <= 0) continue;
            double diff = Math.abs(times[i] - times[i-1]);
            if (times[i] < times[i-1]) diff *= 0.5; // Reward improvement
            diffs2.add(diff);
        }

        List<Double> diffs3 = new ArrayList<>();
        for (int i = 2; i < times.length; i++) {
            if (times[i] <= 0 || times[i-1] <= 0 || times[i-2] <= 0) continue;
            // Difference between three consecutive laps: sum of adjacent diffs / 2
            double d1 = Math.abs(times[i] - times[i-1]) * (times[i] < times[i-1] ? 0.5 : 1.0);
            double d2 = Math.abs(times[i-1] - times[i-2]) * (times[i-1] < times[i-2] ? 0.5 : 1.0);
            diffs3.add((d1 + d2) / 2.0);
        }

        double score2 = processWeightedDiff(diffs2, 0.25, 0.25, 1.0);
        double score3 = processWeightedDiff(diffs3, 0.15, 0.15, 0.75);

        return (score2 + score3);
    }

    private double processWeightedDiff(List<Double> diffs, double p1, double p2, double baseWeight) {
        if (diffs.isEmpty()) return 0;
        Collections.sort(diffs);

        int n = diffs.size();
        double totalWeight = 0;
        double weightedSum = 0;

        for (int i = 0; i < n; i++) {
            double rank = i + 1;
            double weight = 0;

            if (rank <= n * p1) {
                weight = baseWeight;
            } else if (rank <= n * (p1 + p2)) {
                weight = baseWeight * (1.0 - (rank - n * p1) / (n * p2));
            }

            if (weight > 0) {
                weightedSum += diffs.get(i) * weight;
                totalWeight += weight;
            }
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0;
    }

    public List<LongestStintStats> calculateLongestStints(Long eventId) {
        Event event = eventRepository.findByIdWithResults(eventId).orElse(null);
        if (event == null) return Collections.emptyList();

        // Find the main race session
        SessionResult raceSession = event.getSessionResults().stream()
                .filter(s -> s.getSessionType() >= 15 && s.getSessionType() <= 17)
                .findFirst().orElse(null);
        if (raceSession == null) return Collections.emptyList();

        // Find best sectors for 107% rule
        long bestS1 = Long.MAX_VALUE;
        long bestS2 = Long.MAX_VALUE;
        long bestS3 = Long.MAX_VALUE;

        for (DriverResult dr : raceSession.getDriverResults()) {
            for (LapResult lap : dr.getLapResults()) {
                if (lap.getIsValid()) {
                    if (lap.getS1InMS() > 0) bestS1 = Math.min(bestS1, lap.getS1InMS());
                    if (lap.getS2InMS() > 0) bestS2 = Math.min(bestS2, lap.getS2InMS());
                    if (lap.getS3InMS() > 0) bestS3 = Math.min(bestS3, lap.getS3InMS());
                }
            }
        }

        if (bestS1 == Long.MAX_VALUE || bestS2 == Long.MAX_VALUE || bestS3 == Long.MAX_VALUE) {
            return Collections.emptyList();
        }

        double limitS1 = bestS1 * 1.07;
        double limitS2 = bestS2 * 1.07;
        double limitS3 = bestS3 * 1.07;

        List<LongestStintStats> allStints = new ArrayList<>();

        for (DriverResult dr : raceSession.getDriverResults()) {
            List<LapResult> laps = new ArrayList<>(dr.getLapResults());
            laps.sort(Comparator.comparingInt(LapResult::getLapNumber));

            LongestStintStats bestDriverStint = null;

            for (TyreStint stint : dr.getTyreStints()) {
                int startLap = stint.getEndLap() - stint.getLaps() + 1;
                int endLap = stint.getEndLap();

                List<LapResult> stintLaps = laps.stream()
                        .filter(l -> l.getLapNumber() >= startLap && l.getLapNumber() <= endLap)
                        .collect(Collectors.toList());

                if (stintLaps.isEmpty()) continue;

                LongestStintStats stats = new LongestStintStats();
                stats.setDriverName(dr.getDriverName());
                stats.setAi(dr.isAi());
                stats.setTeamName(dr.getTeamName());
                stats.setLaps(stint.getLaps());
                stats.setTyreCompound(TYRE_COMPOUNDS.getOrDefault(stint.getTyreCompound(), "Unknown"));

                List<Long> s1Times = stintLaps.stream()
                        .filter(LapResult::getIsValid)
                        .map(LapResult::getS1InMS)
                        .filter(t -> t > 0 && t <= limitS1)
                        .collect(Collectors.toList());
                List<Long> s2Times = stintLaps.stream()
                        .filter(LapResult::getIsValid)
                        .map(LapResult::getS2InMS)
                        .filter(t -> t > 0 && t <= limitS2)
                        .collect(Collectors.toList());
                List<Long> s3Times = stintLaps.stream()
                        .filter(LapResult::getIsValid)
                        .map(LapResult::getS3InMS)
                        .filter(t -> t > 0 && t <= limitS3)
                        .collect(Collectors.toList());

                double avgS1 = s1Times.stream().mapToLong(Long::longValue).average().orElse(0);
                double avgS2 = s2Times.stream().mapToLong(Long::longValue).average().orElse(0);
                double avgS3 = s3Times.stream().mapToLong(Long::longValue).average().orElse(0);

                stats.setAvgS1(avgS1 / 1000.0);
                stats.setAvgS2(avgS2 / 1000.0);
                stats.setAvgS3(avgS3 / 1000.0);
                stats.setAvgLapTime((avgS1 + avgS2 + avgS3) / 1000.0);

                if (bestDriverStint == null || stats.getLaps() > bestDriverStint.getLaps()) {
                    bestDriverStint = stats;
                }
            }
            if (bestDriverStint != null) {
                allStints.add(bestDriverStint);
            }
        }

        return allStints.stream()
                .sorted(Comparator.comparingInt(LongestStintStats::getLaps).reversed())
                .collect(Collectors.toList());
    }

    @Transactional
    public void handleFinalClassification(LeagueSessionState state, PacketFinalClassificationData classification) {
        long sessionUID = classification.getHeader().getSessionUID();
        log.info("Received Final Classification packet (packet 8) for session UID: {}", sessionUID);
        
        if (state.getLeagueId() == null || state.getLeagueId() == -1) {
            log.warn("Cannot save results: No valid league associated with state.");
            return;
        }

        // Fallback fetch from DB if critical data is missing
        if (state.getCurrentSession() == null || state.getCurrentParticipants() == null) {
            log.info("Session or Participants data missing for UID: {}, trying fallback fetch from DB.", sessionUID);
            liveStateRepository.findById(state.getLeagueId()).ifPresent(remote -> {
                try {
                    String json = decompress(remote.getCompressedState());
                    if (!json.isEmpty()) {
                        LeagueSessionState remoteState = objectMapper.readValue(json, LeagueSessionState.class);
                        if (remoteState.getCurrentSessionUID() == sessionUID) {
                            if (state.getCurrentSession() == null) state.setCurrentSession(remoteState.getCurrentSession());
                            if (state.getCurrentParticipants() == null) state.setCurrentParticipants(remoteState.getCurrentParticipants());
                            log.info("Successfully recovered missing data from database for league {}", state.getLeagueId());
                        }
                    }
                } catch (Exception e) {
                    log.error("Fallback fetch failed: {}", e.getMessage());
                }
            });
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

        // Check if session already recorded (Search by UID only first, handles multi-pod finishing)
        boolean wasOverwritten = false;
        Optional<SessionResult> existing = sessionResultRepository.findBySessionUID(sessionUID);
        if (existing.isPresent()) {
            log.info("Session UID: {} already recorded as ID: {}. Overwriting with Final Classification data.", 
                sessionUID, existing.get().getId());
            sessionResultRepository.delete(existing.get());
            sessionResultRepository.flush();
            wasOverwritten = true;
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
            driverResult.setAi(isAi(state, participant, i));
            driverResult.setTeamName(TEAM_NAMES.getOrDefault(participant.getTeamId(), "Unknown"));
            driverResult.setPosition(data.getPosition());
            driverResult.setNumLaps(data.getNumLaps());
            driverResult.setPointsAwarded(getPointsForPosition(league, state.getCurrentSession().getSessionType(), data.getPosition()));
            driverResult.setGridPosition(data.getGridPosition());
            driverResult.setBestLapTime(data.getBestLapTimeInMS() / 1000.0f);
            driverResult.setTotalTime(data.getTotalRaceTime());
            driverResult.setResultStatus(data.getResultStatus());
            driverResult.setPenalties(data.getPenaltiesTime());
            if (state.getCurrentLapData() != null && i < state.getCurrentLapData().getLapData().size()) {
                driverResult.setWarnings(state.getCurrentLapData().getLapData().get(i).getTotalWarnings());
            }

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

        // Calculate gaps
        calculateGaps(sessionResult);

        // Then update standings if points were awarded or it's a race
        boolean hasPoints = sessionResult.getDriverResults().stream().anyMatch(dr -> dr.getPointsAwarded() != null && dr.getPointsAwarded() > 0);
        if (isRace || hasPoints) {
            if (wasOverwritten) {
                // If we overwritten an existing (live-saved) result, we must recalculate everything
                // to avoid double-counting points in standings.
                recalculateStandings(league.getId());
            } else {
                for (DriverResult driverResult : sessionResult.getDriverResults()) {
                    String key = driverResult.getTelemetryName() + "|" + driverResult.getRaceNumber() + "|" + driverResult.getDriverId();
                    boolean isReserve = state.getReserveDrivers().contains(key);
                    updateStandings(league, driverResult, isReserve, driverResult.getRaceNumber(), isRace);
                }
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
            driverResult.setAi(isAi(state, participant, i));
            driverResult.setTeamName(TEAM_NAMES.getOrDefault(participant.getTeamId(), "Unknown"));
            driverResult.setPosition(ld.getCarPosition());
            driverResult.setNumLaps(ld.getCurrentLapNum() - 1); // Live state current lap means they completed current-1
            driverResult.setGridPosition(ld.getGridPosition());
            driverResult.setBestLapTime(state.getDriverBestLap()[i] / 1000.0f);
            driverResult.setResultStatus(ld.getResultStatus());
            driverResult.setPenalties(ld.getPenalties());
            driverResult.setWarnings(ld.getTotalWarnings());
            
            // Assign points
            driverResult.setPointsAwarded(getPointsForPosition(league, sessionType, ld.getCarPosition()));

            // Link stored lap results
            List<LapResult> laps = lapResultRepository.findBySessionUIDAndCarIndex(sessionUID, i);
            long totalTimeMs = 0;
            for (LapResult lap : laps) {
                lap.setDriverResult(driverResult);
                driverResult.getLapResults().add(lap);
                if (lap.getLapTimeInMS() != null) totalTimeMs += lap.getLapTimeInMS();
            }
            if (totalTimeMs > 0) {
                driverResult.setTotalTime(totalTimeMs / 1000.0);
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

        // Calculate gaps
        calculateGaps(sessionResult);

        boolean hasPoints = sessionResult.getDriverResults().stream().anyMatch(dr -> dr.getPointsAwarded() != null && dr.getPointsAwarded() > 0);
        if (isRace || hasPoints) {
            for (DriverResult driverResult : sessionResult.getDriverResults()) {
                String key = driverResult.getTelemetryName() + "|" + driverResult.getRaceNumber() + "|" + driverResult.getDriverId();
                boolean isReserve = state.getReserveDrivers().contains(key);
                updateStandings(league, driverResult, isReserve, driverResult.getRaceNumber(), isRace);
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

        java.util.Set<SessionResult> allSessions = league.getEvents().stream()
                .flatMap(e -> e.getSessionResults().stream())
                .collect(java.util.stream.Collectors.toSet());

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

        List<SessionPointConfig> pointConfigs = sessionPointConfigRepository.findByLeague(league);

        // Get all sessions from events - Use a Set to avoid duplicates if hibernate join fetch returned duplicates
        java.util.Set<SessionResult> allSessions = league.getEvents().stream()
                .flatMap(e -> e.getSessionResults().stream())
                .collect(java.util.stream.Collectors.toSet());

        for (SessionResult session : allSessions) {
            boolean isRace = session.getSessionType() >= 15 && session.getSessionType() <= 17;
            
            // Recalculate gaps for each session
            calculateGaps(session);

            // Find fastest lap for this session
            final Float fastestLapTime = session.getDriverResults().stream()
                    .map(DriverResult::getBestLapTime)
                    .filter(t -> t != null && t > 0)
                    .min(Float::compare)
                    .orElse(null);
            
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

                // Re-evaluate points based on current configuration
                int finishPoints = getPointsForPosition(league, session.getSessionType(), result.getPosition());
                
                // Bonus points
                int bonusPoints = 0;
                Optional<SessionPointConfig> sessionConfig = pointConfigs.stream()
                        .filter(c -> c.getSessionType().equals(session.getSessionType()))
                        .findFirst();
                
                if (sessionConfig.isPresent()) {
                    SessionPointConfig conf = sessionConfig.get();
                    // Bonuses are only awarded if the driver scored points from their finishing position
                    if (finishPoints > 0) {
                        // Fastest lap bonus
                        if (fastestLapTime != null && fastestLapTime.equals(result.getBestLapTime())) {
                            bonusPoints += (conf.getFastestLapPoints() != null ? conf.getFastestLapPoints() : 0);
                        }
                        // No penalty bonus
                        if ((result.getPenalties() == null || result.getPenalties() == 0) && (result.getWarnings() == null || result.getWarnings() == 0)) {
                            bonusPoints += (conf.getNoPenaltyPoints() != null ? conf.getNoPenaltyPoints() : 0);
                        }
                    }
                }

                result.setPointsAwarded(finishPoints + bonusPoints);

                // Only update standings if points were awarded
                if (result.getPointsAwarded() != null && result.getPointsAwarded() > 0) {
                    boolean isReserve = false;
                    Integer raceNumber = result.getRaceNumber();
                    if (result.getTelemetryName() != null && result.getRaceNumber() != null && result.getDriverId() != null) {
                        isReserve = reserveSet.contains(result.getTelemetryName() + "|" + result.getRaceNumber() + "|" + result.getDriverId());
                    }
                    updateStandings(league, result, isReserve, raceNumber, isRace);
                } else if (isRace) {
                    // Still need to update standings for races for Wins/Podiums even if 0 points
                    boolean isReserve = false;
                    Integer raceNumber = result.getRaceNumber();
                    if (result.getTelemetryName() != null && result.getRaceNumber() != null && result.getDriverId() != null) {
                        isReserve = reserveSet.contains(result.getTelemetryName() + "|" + result.getRaceNumber() + "|" + result.getDriverId());
                    }
                    updateStandings(league, result, isReserve, raceNumber, true);
                }
            }
        }
        log.info("Recalculated standings for league: {}", league.getName());
    }

    private void updateStandings(League league, DriverResult result, boolean isReserve, Integer raceNumber, boolean isRaceSession) {
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
        ds.setRaceNumber(raceNumber);
        
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
        if (isRaceSession) {
            if (result.getPosition() != null && result.getPosition() == 1) ds.setWins((ds.getWins() != null ? ds.getWins() : 0) + 1);
            if (result.getPosition() != null && result.getPosition() <= 3) ds.setPodiums((ds.getPodiums() != null ? ds.getPodiums() : 0) + 1);
        }
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

    public void calculateGaps(SessionResult session) {
        if (session.getDriverResults() == null || session.getDriverResults().isEmpty()) return;

        boolean isRace = session.getSessionType() >= 15 && session.getSessionType() <= 17;

        // Find the winner/pole setter (Position 1)
        Optional<DriverResult> leader = session.getDriverResults().stream()
                .filter(dr -> dr.getPosition() != null && dr.getPosition() == 1)
                .findFirst();

        if (leader.isPresent()) {
            DriverResult l = leader.get();
            l.setGapToLeader(isRace ? "Winner" : "Pole");
            
            if (isRace) {
                Integer winnerLaps = l.getNumLaps();
                double winnerTime = l.getTotalTime() != null ? l.getTotalTime() : 0;

                for (DriverResult dr : session.getDriverResults()) {
                    if (dr.getPosition() != null && dr.getPosition() == 1) continue;

                    // 1. Check for Lap Gaps first
                    if (winnerLaps != null && dr.getNumLaps() != null && dr.getNumLaps() < winnerLaps) {
                        int lapGap = winnerLaps - dr.getNumLaps();
                        dr.setGapToLeader("+" + lapGap + (lapGap == 1 ? " Lap" : " Laps"));
                    } 
                    // 2. Check for Time Gaps if on the same lap
                    else if (dr.getTotalTime() != null && dr.getTotalTime() > 0 && winnerTime > 0) {
                        double gap = dr.getTotalTime() - winnerTime;
                        dr.setGapToLeader(String.format("+%.3fs", gap));
                    } 
                    else {
                        dr.setGapToLeader("-");
                    }
                }
            } else {
                // Qualifying/Practice: Gap to best lap
                float bestTime = l.getBestLapTime() != null ? l.getBestLapTime() : 0;
                for (DriverResult dr : session.getDriverResults()) {
                    if (dr.getPosition() != null && dr.getPosition() == 1) continue;
                    
                    if (dr.getBestLapTime() != null && dr.getBestLapTime() > 0 && bestTime > 0) {
                        dr.setGapToLeader(String.format("+%.3fs", dr.getBestLapTime() - bestTime));
                    } else {
                        dr.setGapToLeader("-");
                    }
                }
            }
        } else {
            // No leader found, clear gaps
            for (DriverResult dr : session.getDriverResults()) {
                dr.setGapToLeader("-");
            }
        }
        driverResultRepository.saveAll(session.getDriverResults());
    }
}
