package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.DriverResult;
import be.jabapage.racingleague.f1telemetry.entity.DriverStanding;
import be.jabapage.racingleague.f1telemetry.entity.Event;
import be.jabapage.racingleague.f1telemetry.entity.LapResult;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.entity.LiveState;
import be.jabapage.racingleague.f1telemetry.entity.SessionPointConfig;
import be.jabapage.racingleague.f1telemetry.entity.SessionResult;
import be.jabapage.racingleague.f1telemetry.entity.TeamStanding;
import be.jabapage.racingleague.f1telemetry.entity.TyreStint;
import be.jabapage.racingleague.f1telemetry.model.CarDamageData;
import be.jabapage.racingleague.f1telemetry.model.CarStatusData;
import be.jabapage.racingleague.f1telemetry.model.ConsistencyStats;
import be.jabapage.racingleague.f1telemetry.model.DriverBoardState;
import be.jabapage.racingleague.f1telemetry.model.FinalClassificationData;
import be.jabapage.racingleague.f1telemetry.model.LapData;
import be.jabapage.racingleague.f1telemetry.model.LongestStintStats;
import be.jabapage.racingleague.f1telemetry.model.PacketCarDamageData;
import be.jabapage.racingleague.f1telemetry.model.PacketCarStatusData;
import be.jabapage.racingleague.f1telemetry.model.PacketEventData;
import be.jabapage.racingleague.f1telemetry.model.PacketFinalClassificationData;
import be.jabapage.racingleague.f1telemetry.model.PacketHeader;
import be.jabapage.racingleague.f1telemetry.model.PacketLapData;
import be.jabapage.racingleague.f1telemetry.model.PacketParticipantsData;
import be.jabapage.racingleague.f1telemetry.model.PacketSessionData;
import be.jabapage.racingleague.f1telemetry.model.ParticipantData;
import be.jabapage.racingleague.f1telemetry.model.RacePaceStats;
import be.jabapage.racingleague.f1telemetry.model.SessionInfo;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.DriverResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.DriverStandingRepository;
import be.jabapage.racingleague.f1telemetry.repository.EventRepository;
import be.jabapage.racingleague.f1telemetry.repository.LapResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import be.jabapage.racingleague.f1telemetry.repository.LiveStateRepository;
import be.jabapage.racingleague.f1telemetry.repository.SessionPointConfigRepository;
import be.jabapage.racingleague.f1telemetry.repository.SessionResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.TeamStandingRepository;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class TelemetryProcessingService {

    private final LeagueRepository leagueRepository;
    private final TierRepository tierRepository;
    private final SessionResultRepository sessionResultRepository;
    private final DriverStandingRepository driverStandingRepository;
    private final TeamStandingRepository teamStandingRepository;
    private final EventRepository eventRepository;
    private final DriverResultRepository driverResultRepository;
    private final LapResultRepository lapResultRepository;
    private final DriverMappingRepository driverMappingRepository;
    private final LiveStateRepository liveStateRepository;
    private final SessionPointConfigRepository sessionPointConfigRepository;
    private final Broadcaster broadcaster;
    private final ObjectMapper objectMapper;

    private final Map<String, LeagueSessionState> leagueStates = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> lastLocalUpdate = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Long> lastSavedMap = new java.util.concurrent.ConcurrentHashMap<>();

    public TelemetryProcessingService(LeagueRepository leagueRepository,
                                      TierRepository tierRepository,
                                      SessionResultRepository sessionResultRepository,
                                      DriverStandingRepository driverStandingRepository,
                                      TeamStandingRepository teamStandingRepository,
                                      EventRepository eventRepository,
                                      DriverResultRepository driverResultRepository,
                                      LapResultRepository lapResultRepository,
                                      DriverMappingRepository driverMappingRepository,
                                      LiveStateRepository liveStateRepository,
                                      SessionPointConfigRepository sessionPointConfigRepository,
                                      Broadcaster broadcaster,
                                      ObjectMapper objectMapper) {
        this.leagueRepository = leagueRepository;
        this.tierRepository = tierRepository;
        this.sessionResultRepository = sessionResultRepository;
        this.driverStandingRepository = driverStandingRepository;
        this.teamStandingRepository = teamStandingRepository;
        this.eventRepository = eventRepository;
        this.driverResultRepository = driverResultRepository;
        this.lapResultRepository = lapResultRepository;
        this.driverMappingRepository = driverMappingRepository;
        this.liveStateRepository = liveStateRepository;
        this.sessionPointConfigRepository = sessionPointConfigRepository;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void syncDistributedState() {
        Set<Long> activeLeagueIds = getActiveLeagueIds();
        if (activeLeagueIds.isEmpty()) return;

        // Fetch only states for leagues we care about
        List<LiveState> updates = liveStateRepository.findAllById(activeLeagueIds);
        
        for (LiveState remote : updates) {
            LocalDateTime local = lastLocalUpdate.get(remote.getTierId());
            if (local == null || remote.getLastUpdated().isAfter(local)) {
                // Update memory cache for UDP processing
                leagueStates.entrySet().stream()
                        .filter(entry -> Objects.equals(entry.getValue().getTierId(), remote.getTierId()))
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
                                    log.debug("Sync: Updated state for league {} (Remote is newer or local is idle)", remote.getTierId());
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
                                        log.info("Sync: Merged missing Session/Participants for league {} from DB", remote.getTierId());
                                    }
                                }

                                lastLocalUpdate.put(remote.getTierId(), remote.getLastUpdated());
                                
                                tierRepository.findById(remote.getTierId()).ifPresent(t -> {
                                    refreshDriverMappings(entry.getValue(), t);
                                    League l = t.getLeague();
                                    entry.getValue().setHideAi(l.isHideAi());
                                    entry.getValue().setShowTyreWear(l.isShowTyreWear());
                                    entry.getValue().setShowErs(l.isShowErs());
                                });

                                broadcastLeaderboard(entry.getValue());
                                broadcastSessionInfo(entry.getValue());
                            } catch (Exception e) {
                                log.error("Sync: Failed to update league {}: {}", remote.getTierId(), e.getMessage());
                            }
                        }, () -> {
                            // If we don't have it in memory but someone is listening, broadcast to them
                            if (broadcaster.hasListeners(remote.getTierId())) {
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
            if (s.getTierId() != null && s.getTierId() != -1) {
                activeIds.add(s.getTierId());
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
            lastLocalUpdate.put(remote.getTierId(), remote.getLastUpdated());
            
            tierRepository.findById(remote.getTierId()).ifPresent(t -> {
                League l = t.getLeague();
                refreshDriverMappings(state, t);
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
            log.error("Failed to load and broadcast league {}: {}", remote.getTierId(), e.getMessage());
        }
    }

    private LeagueSessionState getOrCreateState(String token) {
        return leagueStates.computeIfAbsent(token, t -> {
            Optional<Tier> tierOptional = tierRepository.findByToken(t);
            if (tierOptional.isPresent()) {
                // Try to load from DB first
                Tier tier = tierOptional.get();
                League l = tier.getLeague();
                Optional<LiveState> liveState = liveStateRepository.findById(tier.getId());
                if (liveState.isPresent()) {
                    try {
                        String json = decompress(liveState.get().getCompressedState());
                        if (!json.isEmpty()) {
                            LeagueSessionState state = objectMapper.readValue(json, LeagueSessionState.class);
                            // Refresh transient mappings
                            refreshDriverMappings(state, tier);
                            state.setHideAi(l.isHideAi());
                            state.setShowTyreWear(l.isShowTyreWear());
                            state.setShowErs(l.isShowErs());
                            log.info("Loaded live state for tier {} from database", tier.getId());
                            return state;
                        }
                    } catch (Exception e) {
                        log.error("Failed to deserialize live state for tier {}: {}", tier.getId(), e.getMessage());
                    }
                }

                LeagueSessionState state = new LeagueSessionState(tier.getId());
                state.setHideAi(l.isHideAi());
                state.setShowTyreWear(l.isShowTyreWear());
                state.setShowErs(l.isShowErs());
                refreshDriverMappings(state, tier);
                return state;
            } else if ("default".equals(t)) {
                // Fallback for default token if no league found
                return new LeagueSessionState(-1L);
            }
            return null;
        });
    }

    private void saveState(LeagueSessionState state) {
        if (state.getTierId() == null || state.getTierId() == -1) return;

        long now = System.currentTimeMillis();
        long lastSaved = lastSavedMap.getOrDefault(state.getTierId(), 0L);

        // Throttle DB writes to once per 1000ms
        if (now - lastSaved > 1000) {
            lastSavedMap.put(state.getTierId(), now);
            performAsyncSave(state);
        }
    }

    @org.springframework.scheduling.annotation.Async
    protected void performAsyncSave(LeagueSessionState state) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LiveState liveState = new LiveState();
            liveState.setTierId(state.getTierId());
            liveState.setLastUpdated(now);
            
            String json = objectMapper.writeValueAsString(state);
            liveState.setCompressedState(compress(json));
            
            liveStateRepository.save(liveState);
            lastLocalUpdate.put(state.getTierId(), now);
        } catch (Exception e) {
            log.error("Failed to persist live state for league {}: {}", state.getTierId(), e.getMessage());
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
                .filter(s -> Objects.equals(s.getTierId(), leagueId))
                .findFirst()
                .ifPresent(state -> {
                    leagueRepository.findById(leagueId).ifPresent(league -> state.setHideAi(league.isHideAi()));
                });
    }

    public void refreshDriverMappings(Long tierId) {
        leagueStates.values().stream()
                .filter(s -> Objects.equals(s.getTierId(), tierId))
                .findFirst()
                .ifPresent(state -> {
                    tierRepository.findById(tierId).ifPresent(tier -> refreshDriverMappings(state, tier));
                });
    }

    private void refreshDriverMappings(LeagueSessionState state, Tier tier) {
        List<DriverMapping> mappings = driverMappingRepository.findByTier(tier);
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

    private int getPointsForPosition(List<SessionPointConfig> configs, int sessionType, int position) {
        // Check for custom point configuration for this session type and position
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
        String key = (p.getName() != null ? p.getName() : "") + "|" + p.getRaceNumber() + "|" + p.getDriverId();
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
        if (state.getTierId() == null || state.getTierId() == -1) return;

        Tier tier = tierRepository.findById(state.getTierId()).orElse(null);
        if (tier == null) return;

        boolean changed = false;
        for (int i = 0; i < participants.getParticipants().size(); i++) {
            ParticipantData p = participants.getParticipants().get(i);
            
            // Allow discovery even with empty names for humans, BUT skip if it looks like an uninitialized slot
            if (p.getAiControlled() == 1 && (p.getName() == null || p.getName().trim().isEmpty())) continue;
            
            // If it's a human slot but name is empty AND race number is 0, it's likely uninitialized
            if (p.getAiControlled() == 0 && (p.getName() == null || p.getName().trim().isEmpty()) && p.getRaceNumber() == 0) continue;

            // Track if this car was EVER controlled by a human during this session.
            if (p.getAiControlled() == 0 && i < state.getIsHuman().length) {
                state.getIsHuman()[i] = true;
            }

            String telemetryName = p.getName() != null ? p.getName() : "";
            String key = telemetryName + "|" + p.getRaceNumber() + "|" + p.getDriverId();
            if (state.getDriverNameOverrides().containsKey(key)) continue;

            // Check if we already have a mapping (even without override)
            Optional<DriverMapping> mapping = driverMappingRepository.findByTierAndTelemetryNameAndRaceNumberAndDriverId(tier, telemetryName, p.getRaceNumber(), p.getDriverId());
            if (mapping.isEmpty()) {
                DriverMapping newMapping = new DriverMapping();
                newMapping.setTier(tier);
                newMapping.setTelemetryName(telemetryName);
                newMapping.setRaceNumber(p.getRaceNumber());
                newMapping.setDriverId(p.getDriverId());
                
                // If name is missing or "Player", set a default display name
                if (telemetryName.trim().isEmpty() || "Player".equalsIgnoreCase(telemetryName.trim())) {
                    String displayName = "Player #" + p.getRaceNumber();
                    newMapping.setOverriddenName(displayName);
                    state.getDriverNameOverrides().put(key, displayName);
                } else {
                    state.getDriverNameOverrides().put(key, "");
                }
                
                driverMappingRepository.save(newMapping);
                changed = true;
                log.info("Auto-discovered new driver in tier {}: {} (#{}, ID: {})", tier.getId(), telemetryName, p.getRaceNumber(), p.getDriverId());
            } else {
                // Already in DB, add to cache to avoid re-checking DB
                String displayName = mapping.get().getOverriddenName() != null ? mapping.get().getOverriddenName() : "";
                state.getDriverNameOverrides().put(key, displayName);
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
            log.info("Incoming Packet 8 (Final Classification) for UID: {} (League: {})", packetSessionUID, state.getTierId());
        }

        // Reset session-specific state if session UID changed OR if there was a long gap
        boolean sessionChanged = (packetSessionUID != 0 && packetSessionUID != state.getCurrentSessionUID());
        boolean timeout = (now - state.getLastPacketTime() > 5000 && state.getLastPacketTime() > 0);

        if (sessionChanged || timeout) {
            log.info("{} detected for league {}, resetting live tracking state. (New UID: {}, Old UID: {}, Gap: {}ms)",
                timeout ? "Timeout" : "Session change",
                state.getTierId(),
                packetSessionUID, state.getCurrentSessionUID(), (now - state.getLastPacketTime()));
            
            // If it was a real session that just ended/timed out, save what we have before resetting?
            // Usually SEND or Final Classification handles this, but this is a safety net.
            
            state.reset();
            clearState(state.getTierId());
            state.setCurrentSessionUID(packetSessionUID);
            // Clear the live UI
            broadcaster.broadcastLeaderboard(state.getTierId(), Collections.emptyList());
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
                    log.info("DRS Enabled event received for league {}", state.getTierId());
                    state.setDrsEnabled(true);
                    broadcastSessionInfo(state);
                } else if ("DRSD".equals(eventData.getEventStringCode())) {
                    log.info("DRS Disabled event received for league {}", state.getTierId());
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
                clearState(state.getTierId());
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
            broadcaster.broadcastSessionInfo(state.getTierId(), info);
        }
    }

    public SessionInfo getSessionInfo(Long leagueId) {
        return leagueStates.values().stream()
                .filter(s -> Objects.equals(s.getTierId(), leagueId))
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
            broadcaster.broadcastLeaderboard(state.getTierId(), board);
        }
    }

    public List<DriverBoardState> getLeaderboard(Long leagueId) {
        return leagueStates.values().stream()
                .filter(s -> Objects.equals(s.getTierId(), leagueId))
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
            LapData ld = state.getCurrentLapData().getLapData().get(i);
            
            // Skip empty/inactive slots
            if (ld.getResultStatus() <= 1) continue;

            // Don't skip players without name if they are active, use fallback instead
            if (p.getAiControlled() == 1 && (p.getName() == null || p.getName().isEmpty())) continue;

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
        
        if (state.getTierId() == null || state.getTierId() == -1) {
            log.warn("Cannot save results: No valid league associated with state.");
            return;
        }

        // Fallback fetch from DB if critical data is missing
        if (state.getCurrentSession() == null || state.getCurrentParticipants() == null) {
            log.info("Session or Participants data missing for UID: {}, trying fallback fetch from DB.", sessionUID);
            liveStateRepository.findById(state.getTierId()).ifPresent(remote -> {
                try {
                    String json = decompress(remote.getCompressedState());
                    if (!json.isEmpty()) {
                        LeagueSessionState remoteState = objectMapper.readValue(json, LeagueSessionState.class);
                        if (remoteState.getCurrentSessionUID() == sessionUID) {
                            if (state.getCurrentSession() == null) state.setCurrentSession(remoteState.getCurrentSession());
                            if (state.getCurrentParticipants() == null) state.setCurrentParticipants(remoteState.getCurrentParticipants());
                            log.info("Successfully recovered missing data from database for league {}", state.getTierId());
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

        Tier tier = tierRepository.findById(state.getTierId()).orElse(null);
        League league = tier != null ? tier.getLeague() : null;
        if (league == null) {
            log.warn("Cannot save results: Activated league ID {} not found in database.", state.getTierId());
            return;
        }

        // Check if session already recorded for this specific tier
        boolean wasOverwritten = false;
        Optional<SessionResult> existing = sessionResultRepository.findBySessionUIDAndTier(sessionUID, tier);
        if (existing.isPresent()) {
            log.info("Session UID: {} for Tier: {} already recorded as ID: {}. Overwriting with Final Classification data.", 
                sessionUID, tier.getName(), existing.get().getId());
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
        sessionResult.setTier(tier);
        sessionResult.setEvent(event);
        sessionResult.setSessionUID(classification.getHeader().getSessionUID());
        sessionResult.setSessionType(state.getCurrentSession().getSessionType());
        sessionResult.setTrackId(trackIdStr);

        boolean isRace = state.getCurrentSession().getSessionType() >= 15 && state.getCurrentSession().getSessionType() <= 17;

        List<SessionPointConfig> pointConfigs = sessionPointConfigRepository.findByLeague(league);
        for (int i = 0; i < classification.getNumCars(); i++) {
            FinalClassificationData data = classification.getClassificationData().get(i);
            if (data.getResultStatus() <= 1) continue; // Inactive/Invalid

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
            driverResult.setPointsAwarded(getPointsForPosition(pointConfigs, state.getCurrentSession().getSessionType(), data.getPosition()));
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
                    updateStandings(tier, league, driverResult, isReserve, driverResult.getRaceNumber(), isRace);
                }
            }
        }

        log.info("Saved {} results for session UID: {} in event: {}", 
                isRace ? "Race" : "Qualifying", sessionResult.getSessionUID(), event.getEventName());
    }

    @Transactional
    public void saveResultsFromLiveState(LeagueSessionState state, long sessionUID) {
        if (state.getTierId() == null || state.getTierId() == -1 || state.getCurrentSession() == null || state.getCurrentParticipants() == null || state.getCurrentLapData() == null) {
            log.warn("Cannot save live results: Missing critical context (League, Session, Participants or LapData)");
            return;
        }

        // Check if session already recorded for this tier
        int sessionType = state.getCurrentSession().getSessionType();
        Tier tier = tierRepository.findById(state.getTierId()).orElse(null);
        if (tier == null) return;

        Optional<SessionResult> existing = sessionResultRepository.findBySessionUIDAndTier(sessionUID, tier);
        if (existing.isPresent()) {
            return; // Already saved
        }

        League league = tier.getLeague();

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
        sessionResult.setTier(tier);
        sessionResult.setEvent(event);
        sessionResult.setSessionUID(sessionUID);
        sessionResult.setSessionType(sessionType);
        sessionResult.setTrackId(trackIdStr);

        boolean isRace = sessionType >= 15 && sessionType <= 17;

        for (int i = 0; i < state.getCurrentParticipants().getParticipants().size(); i++) {
            ParticipantData participant = state.getCurrentParticipants().getParticipants().get(i);
            // Don't skip players without name, use fallback instead
            if (participant.getAiControlled() == 1 && (participant.getName() == null || participant.getName().isEmpty())) continue;

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
            List<SessionPointConfig> pointConfigs = sessionPointConfigRepository.findByLeague(league);
            driverResult.setPointsAwarded(getPointsForPosition(pointConfigs, sessionType, ld.getCarPosition()));

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
                updateStandings(tier, league, driverResult, isReserve, driverResult.getRaceNumber(), isRace);
            }
        }

        clearState(state.getTierId());

        log.info("Saved Fallback {} results (from live state) for session UID: {} in event: {}", 
                isRace ? "Race" : "Qualifying", sessionUID, event.getEventName());
    }

    @Transactional
    public void updateDriverNamesFromMappings(Long leagueId) {
        League league = leagueRepository.findByIdWithEvents(leagueId).orElse(null);
        if (league == null) return;

        java.util.Set<SessionResult> allSessions = league.getEvents().stream()
                .flatMap(e -> e.getSessionResults().stream())
                .collect(java.util.stream.Collectors.toSet());

        for (SessionResult session : allSessions) {
            Tier tier = session.getTier();
            if (tier == null) continue;

            List<DriverMapping> mappings = driverMappingRepository.findByTier(tier);
            Map<String, String> nameMap = mappings.stream()
                    .filter(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty())
                    .collect(Collectors.toMap(
                            m -> m.getTelemetryName() + "|" + m.getRaceNumber() + "|" + m.getDriverId(),
                            DriverMapping::getOverriddenName,
                            (existing, replacement) -> existing
                    ));

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

        // Clear existing standings
        for (Tier tier : tierRepository.findByLeagueId(league.getId())) {
            driverStandingRepository.deleteAll(driverStandingRepository.findByTier(tier));
            teamStandingRepository.deleteAll(teamStandingRepository.findByTier(tier));
        }
        teamStandingRepository.deleteAll(teamStandingRepository.findByLeagueAndTierIsNull(league));

        List<SessionPointConfig> pointConfigs = sessionPointConfigRepository.findByLeague(league);

        // Get all sessions from events
        java.util.Set<SessionResult> allSessions = league.getEvents().stream()
                .flatMap(e -> e.getSessionResults().stream())
                .sorted(Comparator.comparing(SessionResult::getId))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        for (SessionResult session : allSessions) {
            Tier tier = session.getTier();
            if (tier == null) continue;

            List<DriverMapping> mappings = driverMappingRepository.findByTier(tier);
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
                int finishPoints = getPointsForPosition(pointConfigs, session.getSessionType(), result.getPosition());
                
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
                        // No penalty bonus - only check for actual time penalties
                        if (result.getPenalties() == null || result.getPenalties() == 0) {
                            bonusPoints += (conf.getNoPenaltyPoints() != null ? conf.getNoPenaltyPoints() : 0);
                        }
                    }
                }

                result.setPointsAwarded(finishPoints + bonusPoints);
                driverResultRepository.save(result);

                // Update standings
                boolean isReserve = false;
                Integer raceNumber = result.getRaceNumber();
                if (result.getTelemetryName() != null && result.getRaceNumber() != null && result.getDriverId() != null) {
                    isReserve = reserveSet.contains(result.getTelemetryName() + "|" + result.getRaceNumber() + "|" + result.getDriverId());
                }
                
                if (result.getPointsAwarded() != null && result.getPointsAwarded() > 0) {
                    updateStandings(tier, league, result, isReserve, raceNumber, isRace);
                } else if (isRace) {
                    // Still need to update standings for races for Wins/Podiums even if 0 points
                    updateStandings(tier, league, result, isReserve, raceNumber, true);
                }
            }
        }
        log.info("Recalculated standings for league: {}", league.getName());
    }

    private void updateStandings(Tier tier, League league, DriverResult result, boolean isReserve, Integer raceNumber, boolean isRaceSession) {
        // Update Driver Standings
        DriverStanding ds = driverStandingRepository.findByTierAndDriverName(tier, result.getDriverName())
                .orElseGet(() -> {
                    DriverStanding newDs = new DriverStanding();
                    newDs.setTier(tier);
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

        // Update Team Standings (Tier level)
        TeamStanding tierTs = teamStandingRepository.findByTierAndTeamName(tier, result.getTeamName())
                .orElseGet(() -> {
                    TeamStanding newTs = new TeamStanding();
                    newTs.setLeague(league);
                    newTs.setTier(tier);
                    newTs.setTeamName(result.getTeamName());
                    newTs.setPoints(0);
                    return newTs;
                });
        tierTs.setPoints((tierTs.getPoints() != null ? tierTs.getPoints() : 0) + result.getPointsAwarded());
        teamStandingRepository.save(tierTs);

        // Update Team Standings (Overall level)
        TeamStanding overallTs = teamStandingRepository.findByLeagueAndTierIsNullAndTeamName(league, result.getTeamName())
                .orElseGet(() -> {
                    TeamStanding newTs = new TeamStanding();
                    newTs.setLeague(league);
                    newTs.setTeamName(result.getTeamName());
                    newTs.setPoints(0);
                    return newTs;
                });
        overallTs.setPoints((overallTs.getPoints() != null ? overallTs.getPoints() : 0) + result.getPointsAwarded());
        teamStandingRepository.save(overallTs);
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
