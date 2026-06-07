package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.LiveState;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.repository.LiveStateRepository;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
public class TelemetryStateService {

    @Autowired
    private LiveStateRepository liveStateRepository;
    @Autowired
    private TierRepository tierRepository;
    @Autowired
    private LeagueRepository leagueRepository;
    @Autowired
    private DriverMappingRepository driverMappingRepository;
    @Autowired
    private Broadcaster broadcaster;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private LiveDashboardService liveDashboardService;

    private final Map<String, LeagueSessionState> leagueStates = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> lastLocalUpdate = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastSavedMap = new ConcurrentHashMap<>();

    public Map<String, LeagueSessionState> getLeagueStates() {
        return leagueStates;
    }

    @Scheduled(fixedDelay = 1000)
    public void syncDistributedState() {
        long now = System.currentTimeMillis();
        leagueStates.entrySet().removeIf(entry -> {
            LeagueSessionState state = entry.getValue();
            boolean inactive = state.getLastPacketTime() == 0 || (now - state.getLastPacketTime() > 120000);
            if (inactive) {
                log.info("Cleaning up inactive state in memory for tier {}", state.getTierId());
                if (state.getTierId() != null) {
                    lastLocalUpdate.remove(state.getTierId());
                    lastSavedMap.remove(state.getTierId());
                }
            }
            return inactive;
        });

        Set<Long> activeTierIds = getActiveTierIds();
        if (activeTierIds.isEmpty()) {
            log.trace("No active tiers, skipping sync");
            return;
        }

        List<LiveState> updates = liveStateRepository.findAllById(activeTierIds);
        
        for (LiveState remote : updates) {
            LocalDateTime local = lastLocalUpdate.get(remote.getTierId());
            if (local == null || remote.getLastUpdated().isAfter(local)) {
                leagueStates.entrySet().stream()
                        .filter(entry -> Objects.equals(entry.getValue().getTierId(), remote.getTierId()))
                        .findFirst()
                        .ifPresentOrElse(entry -> {
                            try {
                                String json = decompress(remote.getCompressedState());
                                if (json.isEmpty()) return;
                                LeagueSessionState remoteState = objectMapper.readValue(json, LeagueSessionState.class);
                                LeagueSessionState localState = entry.getValue();

                                boolean remoteIsNewerSession = remoteState.getCurrentSessionUID() != localState.getCurrentSessionUID() && remoteState.getCurrentSessionUID() != -1;
                                boolean localIsActivelyReceiving = System.currentTimeMillis() - localState.getLastPacketTime() < 2000;

                                if (remoteIsNewerSession || !localIsActivelyReceiving) {
                                    entry.setValue(remoteState);
                                    log.debug("Sync: Updated state for tier {} (Remote is newer or local is idle)", remote.getTierId());
                                } else {
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
                                        log.info("Sync: Merged missing Session/Participants for tier {} from DB", remote.getTierId());
                                    }
                                }

                                lastLocalUpdate.put(remote.getTierId(), remote.getLastUpdated());
                                
                                tierRepository.findById(remote.getTierId()).ifPresent(t -> {
                                    League l = t.getLeague();
                                    refreshDriverMappings(entry.getValue(), l);
                                    entry.getValue().setHideAi(l.isHideAi());
                                    entry.getValue().setShowTyreWear(l.isShowTyreWear());
                                    entry.getValue().setShowErs(l.isShowErs());
                                });

                                liveDashboardService.broadcastLeaderboard(entry.getValue());
                                liveDashboardService.broadcastSessionInfo(entry.getValue());
                            } catch (Exception e) {
                                log.error("Sync: Failed to update tier {}: {}", remote.getTierId(), e.getMessage());
                            }
                        }, () -> {
                            if (broadcaster.hasListeners(remote.getTierId())) {
                                loadAndBroadcast(remote);
                            }
                        });
            }
        }
    }

    private Set<Long> getActiveTierIds() {
        Set<Long> activeIds = new HashSet<>();
        leagueStates.values().forEach(s -> {
            if (s.getTierId() != null && s.getTierId() != -1) {
                activeIds.add(s.getTierId());
            }
        });
        activeIds.addAll(broadcaster.getActiveTierIds());
        return activeIds;
    }

    public void loadAndBroadcast(LiveState remote) {
        try {
            String json = decompress(remote.getCompressedState());
            if (json.isEmpty()) return;
            LeagueSessionState state = objectMapper.readValue(json, LeagueSessionState.class);
            lastLocalUpdate.put(remote.getTierId(), remote.getLastUpdated());
            
            tierRepository.findById(remote.getTierId()).ifPresent(t -> {
                League l = t.getLeague();
                refreshDriverMappings(state, l);
                state.setHideAi(l.isHideAi());
                state.setShowTyreWear(l.isShowTyreWear());
                state.setShowErs(l.isShowErs());
                liveDashboardService.broadcastLeaderboard(state);
                liveDashboardService.broadcastSessionInfo(state);
            });
        } catch (Exception e) {
            log.error("Failed to load and broadcast tier {}: {}", remote.getTierId(), e.getMessage());
        }
    }

    public LeagueSessionState getOrCreateState(String token) {
        return leagueStates.computeIfAbsent(token, t -> {
            Optional<Tier> tier = tierRepository.findByToken(t);
            if (tier.isPresent()) {
                Tier tr = tier.get();
                League l = tr.getLeague();
                Optional<LiveState> liveState = liveStateRepository.findById(tr.getId());
                if (liveState.isPresent()) {
                    try {
                        String json = decompress(liveState.get().getCompressedState());
                        if (!json.isEmpty()) {
                            LeagueSessionState state = objectMapper.readValue(json, LeagueSessionState.class);
                            state.setTierId(tr.getId());
                            state.setLeagueId(l.getId());
                            refreshDriverMappings(state, l);
                            state.setHideAi(l.isHideAi());
                            state.setShowTyreWear(l.isShowTyreWear());
                            state.setShowErs(l.isShowErs());
                            log.info("Loaded live state for tier {} from database", tr.getId());
                            return state;
                        }
                    } catch (Exception e) {
                        log.error("Failed to deserialize live state for tier {}: {}", tr.getId(), e.getMessage());
                    }
                }

                LeagueSessionState state = new LeagueSessionState(l.getId());
                state.setTierId(tr.getId());
                state.setHideAi(l.isHideAi());
                state.setShowTyreWear(l.isShowTyreWear());
                state.setShowErs(l.isShowErs());
                refreshDriverMappings(state, l);
                return state;
            } else if ("default".equals(t)) {
                return new LeagueSessionState(-1L);
            }
            return null;
        });
    }

    public void saveState(LeagueSessionState state) {
        if (state.getTierId() == null || state.getTierId() == -1) return;

        long now = System.currentTimeMillis();
        long lastSaved = lastSavedMap.getOrDefault(state.getTierId(), 0L);

        if (now - lastSaved > 1000) {
            lastSavedMap.put(state.getTierId(), now);
            performAsyncSave(state);
        }
    }

    @Async
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
            log.error("Failed to persist live state for tier {}: {}", state.getTierId(), e.getMessage());
        }
    }

    public byte[] compress(String data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length());
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data.getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }

    public String decompress(byte[] compressed) throws IOException {
        if (compressed == null || compressed.length == 0) {
            return "";
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
             GZIPInputStream gzip = new GZIPInputStream(bis)) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public void clearState(Long tierId) {
        if (tierId != null && tierId != -1) {
            liveStateRepository.deleteById(tierId);
        }
    }

    public void refreshHideAiSetting(Long leagueId) {
        leagueStates.values().stream()
                .filter(s -> Objects.equals(s.getLeagueId(), leagueId))
                .forEach(state -> {
                    leagueRepository.findById(leagueId).ifPresent(league -> state.setHideAi(league.isHideAi()));
                });
    }

    public void refreshDriverMappings(Long leagueId) {
        leagueStates.values().stream()
                .filter(s -> Objects.equals(s.getLeagueId(), leagueId))
                .forEach(state -> {
                    leagueRepository.findById(leagueId).ifPresent(league -> refreshDriverMappings(state, league));
                });
    }

    public void refreshDriverMappings(LeagueSessionState state, League league) {
        List<DriverMapping> mappings = driverMappingRepository.findByLeague(league);
        state.getDriverNameOverrides().clear();
        state.getReserveDrivers().clear();
        for (DriverMapping mapping : mappings) {
            String key = mapping.getTelemetryName() + "|" + mapping.getRaceNumber() + "|" + mapping.getDriverId() + "|" + mapping.getCountry();
            if (mapping.getOverriddenName() != null && !mapping.getOverriddenName().isEmpty()) {
                state.getDriverNameOverrides().put(key, mapping.getOverriddenName());
            }
            if (mapping.isReserve()) {
                state.getReserveDrivers().add(key);
            }
        }
    }
}
