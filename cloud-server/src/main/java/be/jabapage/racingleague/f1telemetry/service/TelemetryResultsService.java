package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.*;
import be.jabapage.racingleague.f1telemetry.model.FinalClassificationData;
import be.jabapage.racingleague.f1telemetry.model.PacketFinalClassificationData;
import be.jabapage.racingleague.f1telemetry.model.ParticipantData;
import be.jabapage.racingleague.f1telemetry.repository.*;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TelemetryResultsService {

    @Autowired
    private LeagueRepository leagueRepository;
    @Autowired
    private TierRepository tierRepository;
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
    private ExtraPointRuleRepository extraPointRuleRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TelemetryStateService telemetryStateService;

    @Transactional
    public void handleFinalClassification(LeagueSessionState state, PacketFinalClassificationData classification) {
        long sessionUID = classification.getHeader().getSessionUID();
        log.info("Received Final Classification packet (packet 8) for session UID: {}", sessionUID);
        
        if (state.getTierId() == null || state.getTierId() == -1) {
            log.warn("Cannot save results: No valid tier associated with state.");
            return;
        }

        if (state.getCurrentSession() == null || state.getCurrentParticipants() == null) {
            log.info("Session or Participants data missing for UID: {}, trying fallback fetch from DB.", sessionUID);
            liveStateRepository.findById(state.getTierId()).ifPresent(remote -> {
                try {
                    String json = telemetryStateService.decompress(remote.getCompressedState());
                    if (!json.isEmpty()) {
                        LeagueSessionState remoteState = objectMapper.readValue(json, LeagueSessionState.class);
                        if (remoteState.getCurrentSessionUID() == sessionUID) {
                            if (state.getCurrentSession() == null) state.setCurrentSession(remoteState.getCurrentSession());
                            if (state.getCurrentParticipants() == null) state.setCurrentParticipants(remoteState.getCurrentParticipants());
                            log.info("Successfully recovered missing data from database for tier {}", state.getTierId());
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

        Tier tier = tierRepository.findByIdWithEvents(state.getTierId()).orElse(null);
        if (tier == null) {
            log.warn("Cannot save results: Activated tier ID {} not found in database.", state.getTierId());
            return;
        }
        League league = tier.getLeague();

        boolean wasOverwritten = false;
        Optional<SessionResult> existing = sessionResultRepository.findBySessionUID(sessionUID);
        if (existing.isPresent()) {
            log.info("Session UID: {} already recorded as ID: {}. Overwriting with Final Classification data.", 
                sessionUID, existing.get().getId());
            SessionResult oldSession = existing.get();
            
            for (DriverResult dr : oldSession.getDriverResults()) {
                for (LapResult lap : dr.getLapResults()) {
                    lap.setDriverResult(null);
                    lapResultRepository.save(lap);
                }
                dr.getLapResults().clear();
            }
            lapResultRepository.flush();

            if (oldSession.getEvent() != null) {
                oldSession.getEvent().getSessionResults().remove(oldSession);
            }
            if (oldSession.getTier() != null) {
                oldSession.getTier().getSessionResults().remove(oldSession);
            }
            oldSession.setSessionUID(null);
            sessionResultRepository.saveAndFlush(oldSession);
            sessionResultRepository.delete(oldSession);
            sessionResultRepository.flush();
            wasOverwritten = true;
        }

        String trackIdStr = String.valueOf(state.getCurrentSession().getTrackId());
        Event event = eventRepository.findByTierAndTrackId(tier, trackIdStr)
                .orElseGet(() -> {
                    Event newEvent = new Event();
                    newEvent.setTier(tier);
                    newEvent.setTrackId(trackIdStr);
                    newEvent.setEventName(TelemetryProcessingService.TRACK_NAMES.getOrDefault(state.getCurrentSession().getTrackId(), "Track " + trackIdStr));
                    log.info("Creating new event: {} for track: {}", newEvent.getEventName(), trackIdStr);
                    return eventRepository.save(newEvent);
                });

        log.info("Processing {} results for session UID: {} (Type: {})", 
            classification.getNumCars(), sessionUID, state.getCurrentSession().getSessionType());

        // Pre-fetch point configurations and lap results outside the loop to avoid Hibernate auto-flushes on unsaved transient DriverResult entities
        List<SessionPointConfig> pointConfigs = sessionPointConfigRepository.findByLeague(league);
        List<LapResult> allLaps = lapResultRepository.findBySessionUID(classification.getHeader().getSessionUID());

        SessionResult sessionResult = new SessionResult();
        sessionResult.setTier(tier);
        sessionResult.setEvent(event);
        sessionResult.setSessionUID(classification.getHeader().getSessionUID());
        sessionResult.setSessionType(state.getCurrentSession().getSessionType());
        sessionResult.setTrackId(trackIdStr);

        boolean isRace = (state.getCurrentSession().getSessionType() >= 15 && state.getCurrentSession().getSessionType() <= 17) || state.getCurrentSession().getSessionType() == 19;

        for (int i = 0; i < classification.getNumCars(); i++) {
            FinalClassificationData data = classification.getClassificationData().get(i);
            if (data.getResultStatus() == 0) continue;

            ParticipantData participant = state.getCurrentParticipants().getParticipants().get(i);
            
            DriverResult driverResult = new DriverResult();
            driverResult.setSessionResult(sessionResult);
            driverResult.setDriverName(getDriverName(state, participant));
            driverResult.setTelemetryName(participant.getName());
            driverResult.setRaceNumber(participant.getRaceNumber());
            driverResult.setDriverId(participant.getDriverId());
            driverResult.setAi(isAi(state, participant, i));
            driverResult.setCountry(CountryProvider.getCountryInfo(participant.getNationality()).getName());
            driverResult.setTeamName(TelemetryProcessingService.TEAM_NAMES.getOrDefault(participant.getTeamId(), "Unknown"));
            driverResult.setPosition(data.getPosition());
            driverResult.setNumLaps(data.getNumLaps());
            driverResult.setPointsAwarded(getPointsForPosition(pointConfigs, sessionResult, data.getPosition()));
            driverResult.setGridPosition(data.getGridPosition());
            driverResult.setBestLapTime(data.getBestLapTimeInMS() / 1000.0f);
            driverResult.setTotalTime(data.getTotalRaceTime() + data.getPenaltiesTime());
            driverResult.setResultStatus(data.getResultStatus());
            driverResult.setPenalties(data.getPenaltiesTime());
            driverResult.setWarnings(0);
            if (state.getCurrentLapData() != null && i < state.getCurrentLapData().getLapData().size()) {
                driverResult.setWarnings(state.getCurrentLapData().getLapData().get(i).getTotalWarnings());
            }

            final int carIndex = i;
            List<LapResult> laps = allLaps.stream()
                    .filter(lap -> lap.getCarIndex() != null && lap.getCarIndex() == carIndex)
                    .collect(Collectors.toList());
            for (LapResult lap : laps) {
                lap.setDriverResult(driverResult);
                driverResult.getLapResults().add(lap);
            }

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

        sessionResultRepository.saveAndFlush(sessionResult);

        calculateGaps(sessionResult);

        applyExtraPoints(sessionResult, league, pointConfigs);

        boolean hasPoints = sessionResult.getDriverResults().stream().anyMatch(dr -> dr.getPointsAwarded() != null && dr.getPointsAwarded() > 0);
        if (isRace || hasPoints) {
            if (wasOverwritten) {
                recalculateStandings(tier.getId());
            } else {
                for (DriverResult driverResult : sessionResult.getDriverResults()) {
                    String key = driverResult.getTelemetryName() + "|" + driverResult.getRaceNumber() + "|" + driverResult.getDriverId() + "|" + driverResult.getCountry();
                    boolean isReserve = state.getReserveDrivers().contains(key);
                    updateStandings(tier, driverResult, isReserve, driverResult.getRaceNumber(), isRace);
                }
            }
        }

        log.info("Saved {} results for session UID: {} in event: {}", 
                isRace ? "Race" : "Qualifying", sessionResult.getSessionUID(), event.getEventName());
    }

    @Transactional
    public void saveResultsFromLiveState(LeagueSessionState state, long sessionUID) {
        if (state.getTierId() == null || state.getTierId() == -1 || state.getCurrentSession() == null || state.getCurrentParticipants() == null || state.getCurrentLapData() == null) {
            log.warn("Cannot save live results: Missing critical context (Tier, Session, Participants or LapData)");
            return;
        }

        int sessionType = state.getCurrentSession().getSessionType();
        Optional<SessionResult> existing = sessionResultRepository.findBySessionUIDAndSessionType(sessionUID, sessionType);
        if (existing.isPresent()) {
            return;
        }

        Tier tier = tierRepository.findByIdWithEvents(state.getTierId()).orElse(null);
        if (tier == null) return;
        League league = tier.getLeague();

        String trackIdStr = String.valueOf(state.getCurrentSession().getTrackId());
        Event event = eventRepository.findByTierAndTrackId(tier, trackIdStr)
                .orElseGet(() -> {
                    Event newEvent = new Event();
                    newEvent.setTier(tier);
                    newEvent.setTrackId(trackIdStr);
                    newEvent.setEventName(TelemetryProcessingService.TRACK_NAMES.getOrDefault(state.getCurrentSession().getTrackId(), "Track " + trackIdStr));
                    return eventRepository.save(newEvent);
                });

        // Pre-fetch point configurations and lap results outside the loop to avoid Hibernate auto-flushes on unsaved transient DriverResult entities
        List<SessionPointConfig> pointConfigs = sessionPointConfigRepository.findByLeague(league);
        List<LapResult> allLaps = lapResultRepository.findBySessionUID(sessionUID);

        SessionResult sessionResult = new SessionResult();
        sessionResult.setTier(tier);
        sessionResult.setEvent(event);
        sessionResult.setSessionUID(sessionUID);
        sessionResult.setSessionType(sessionType);
        sessionResult.setTrackId(trackIdStr);

        boolean isRace = (sessionType >= 15 && sessionType <= 17) || sessionType == 19;

        for (int i = 0; i < state.getCurrentParticipants().getParticipants().size(); i++) {
            ParticipantData participant = state.getCurrentParticipants().getParticipants().get(i);
            if (participant.getName() == null || participant.getName().isEmpty()) continue;

            if (i >= state.getCurrentLapData().getLapData().size()) break;
            be.jabapage.racingleague.f1telemetry.model.LapData ld = state.getCurrentLapData().getLapData().get(i);
            
            if (ld.getResultStatus() == 0 || ld.getResultStatus() == 1) continue;

            DriverResult driverResult = new DriverResult();
            driverResult.setSessionResult(sessionResult);
            driverResult.setDriverName(getDriverName(state, participant));
            driverResult.setTelemetryName(participant.getName());
            driverResult.setRaceNumber(participant.getRaceNumber());
            driverResult.setDriverId(participant.getDriverId());
            driverResult.setAi(isAi(state, participant, i));
            driverResult.setCountry(CountryProvider.getCountryInfo(participant.getNationality()).getName());
            driverResult.setTeamName(TelemetryProcessingService.TEAM_NAMES.getOrDefault(participant.getTeamId(), "Unknown"));
            driverResult.setPosition(ld.getCarPosition());
            driverResult.setNumLaps(ld.getCurrentLapNum() - 1);
            driverResult.setGridPosition(ld.getGridPosition());
            driverResult.setBestLapTime(state.getDriverBestLap()[i] / 1000.0f);
            driverResult.setResultStatus(ld.getResultStatus());
            driverResult.setPenalties(ld.getPenalties());
            driverResult.setWarnings(ld.getTotalWarnings());
            
            driverResult.setPointsAwarded(getPointsForPosition(pointConfigs, sessionResult, ld.getCarPosition()));

            final int carIndex = i;
            List<LapResult> laps = allLaps.stream()
                    .filter(lap -> lap.getCarIndex() != null && lap.getCarIndex() == carIndex)
                    .collect(Collectors.toList());
            long totalTimeMs = 0;
            for (LapResult lap : laps) {
                lap.setDriverResult(driverResult);
                driverResult.getLapResults().add(lap);
                if (lap.getLapTimeInMS() != null) totalTimeMs += lap.getLapTimeInMS();
            }
            if (totalTimeMs > 0) {
                driverResult.setTotalTime(totalTimeMs / 1000.0);
            }

            if (!laps.isEmpty()) {
                laps.sort(Comparator.comparingInt(LapResult::getLapNumber));
                int stintOrder = 0;
                int currentCompound = -1;
                int currentPitCount = -1;
                int startLap = laps.get(0).getLapNumber();
                int lastLap = -1;
                
                for (int j = 0; j < laps.size(); j++) {
                    LapResult lap = laps.get(j);
                    
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
                    
                    if (j == laps.size() - 1) {
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

        calculateGaps(sessionResult);

        applyExtraPoints(sessionResult, league, pointConfigs);

        boolean hasPoints = sessionResult.getDriverResults().stream().anyMatch(dr -> dr.getPointsAwarded() != null && dr.getPointsAwarded() > 0);
        if (isRace || hasPoints) {
            for (DriverResult driverResult : sessionResult.getDriverResults()) {
                String key = driverResult.getTelemetryName() + "|" + driverResult.getRaceNumber() + "|" + driverResult.getDriverId() + "|" + driverResult.getCountry();
                boolean isReserve = state.getReserveDrivers().contains(key);
                updateStandings(tier, driverResult, isReserve, driverResult.getRaceNumber(), isRace);
            }
        }

        telemetryStateService.clearState(state.getTierId());

        log.info("Saved Fallback {} results (from live state) for session UID: {} in event: {}", 
                isRace ? "Race" : "Qualifying", sessionUID, event.getEventName());
    }

    @Transactional
    public void updateDriverNamesFromMappings(Long leagueId) {
        League league = leagueRepository.findById(leagueId).orElse(null);
        if (league == null) return;

        List<DriverMapping> mappings = driverMappingRepository.findByLeague(league);
        Map<String, String> nameMap = mappings.stream()
                .filter(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty())
                .collect(Collectors.toMap(
                        m -> m.getTelemetryName() + "|" + m.getRaceNumber() + "|" + m.getDriverId() + "|" + m.getCountry(),
                        DriverMapping::getOverriddenName,
                        (existing, replacement) -> existing
                ));

        List<Tier> tiers = tierRepository.findByLeague(league);
        for (Tier tier : tiers) {
            java.util.Set<SessionResult> allSessions = tier.getEvents().stream()
                    .flatMap(e -> e.getSessionResults().stream())
                    .collect(java.util.stream.Collectors.toSet());

            for (SessionResult session : allSessions) {
                for (DriverResult result : session.getDriverResults()) {
                    if (result.getTelemetryName() != null && result.getRaceNumber() != null && result.getDriverId() != null) {
                        String key = result.getTelemetryName() + "|" + result.getRaceNumber() + "|" + result.getDriverId() + "|" + result.getCountry();
                        String nameToUse = nameMap.getOrDefault(key, result.getTelemetryName());
                        if (!nameToUse.equals(result.getDriverName())) {
                            result.setDriverName(nameToUse);
                        }
                    }
                }
            }
        }
        log.info("Updated driver names in all results for league: {}", league.getId());
    }

    @Transactional
    public void recalculateStandings(Long tierId) {
        Tier tier = tierRepository.findById(tierId).orElse(null);
        if (tier == null) return;
        League league = tier.getLeague();

        List<DriverMapping> mappings = driverMappingRepository.findByLeague(league);
        Map<String, String> nameMap = mappings.stream()
                .filter(m -> m.getOverriddenName() != null && !m.getOverriddenName().isEmpty())
                .collect(Collectors.toMap(
                        m -> m.getTelemetryName() + "|" + m.getRaceNumber() + "|" + m.getDriverId() + "|" + m.getCountry(),
                        DriverMapping::getOverriddenName,
                        (existing, replacement) -> existing
                ));

        java.util.Set<String> reserveSet = mappings.stream()
                .filter(DriverMapping::isReserve)
                .map(m -> m.getTelemetryName() + "|" + m.getRaceNumber() + "|" + m.getDriverId() + "|" + m.getCountry())
                .collect(Collectors.toSet());

        driverStandingRepository.deleteAll(driverStandingRepository.findByTier(tier));
        teamStandingRepository.deleteAll(teamStandingRepository.findByTier(tier));

        List<SessionPointConfig> pointConfigs = sessionPointConfigRepository.findByLeague(league);

        java.util.Set<SessionResult> allSessions = tier.getEvents().stream()
                .flatMap(e -> e.getSessionResults().stream())
                .collect(java.util.stream.Collectors.toSet());

        for (SessionResult session : allSessions) {
            boolean isRace = (session.getSessionType() >= 15 && session.getSessionType() <= 17) || session.getSessionType() == 19;
            
            calculateGaps(session);

            applyExtraPoints(session, league, pointConfigs);
            
            for (DriverResult result : session.getDriverResults()) {
                String nameToUse = result.getDriverName();
                if (result.getTelemetryName() != null && result.getRaceNumber() != null && result.getDriverId() != null) {
                    String key = result.getTelemetryName() + "|" + result.getRaceNumber() + "|" + result.getDriverId() + "|" + result.getCountry();
                    nameToUse = nameMap.getOrDefault(key, result.getTelemetryName());
                }

                if (!nameToUse.equals(result.getDriverName())) {
                    result.setDriverName(nameToUse);
                }

                if (result.getPointsAwarded() != null && result.getPointsAwarded() > 0) {
                    boolean isReserve = false;
                    Integer raceNumber = result.getRaceNumber();
                    if (result.getTelemetryName() != null && result.getRaceNumber() != null && result.getDriverId() != null) {
                        isReserve = reserveSet.contains(result.getTelemetryName() + "|" + result.getRaceNumber() + "|" + result.getDriverId() + "|" + result.getCountry());
                    }
                    updateStandings(tier, result, isReserve, raceNumber, isRace);
                } else if (isRace) {
                    boolean isReserve = false;
                    Integer raceNumber = result.getRaceNumber();
                    if (result.getTelemetryName() != null && result.getRaceNumber() != null && result.getDriverId() != null) {
                        isReserve = reserveSet.contains(result.getTelemetryName() + "|" + result.getRaceNumber() + "|" + result.getDriverId() + "|" + result.getCountry());
                    }
                    updateStandings(tier, result, isReserve, raceNumber, true);
                }
            }
        }
        log.info("Recalculated standings for tier: {}", tier.getName());
    }

    private void updateStandings(Tier tier, DriverResult result, boolean isReserve, Integer raceNumber, boolean isRaceSession) {
        DriverStanding ds = driverStandingRepository.findByTierAndDriverNameAndRaceNumberAndCountry(tier, result.getDriverName(), raceNumber, result.getCountry())
                .orElseGet(() -> {
                    DriverStanding newDs = new DriverStanding();
                    newDs.setTier(tier);
                    newDs.setDriverName(result.getDriverName());
                    newDs.setPoints(0);
                    newDs.setWins(0);
                    newDs.setPodiums(0);
                    newDs.setCountry(result.getCountry());
                    return newDs;
                });
        ds.setAi(result.isAi());
        ds.setReserve(isReserve);
        ds.setRaceNumber(raceNumber);
        ds.setCountry(result.getCountry());
        
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

        TeamStanding ts = teamStandingRepository.findByTierAndTeamName(tier, result.getTeamName())
                .orElseGet(() -> {
                    TeamStanding newTs = new TeamStanding();
                    newTs.setTier(tier);
                    newTs.setTeamName(result.getTeamName());
                    newTs.setPoints(0);
                    return newTs;
                });
        ts.setPoints((ts.getPoints() != null ? ts.getPoints() : 0) + result.getPointsAwarded());
        teamStandingRepository.save(ts);
    }

    public void calculateGaps(SessionResult session) {
        if (session.getDriverResults() == null || session.getDriverResults().isEmpty()) return;

        boolean isRace = (session.getSessionType() >= 15 && session.getSessionType() <= 17) || session.getSessionType() == 19;

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

                    if (winnerLaps != null && dr.getNumLaps() != null && dr.getNumLaps() < winnerLaps) {
                        int lapGap = winnerLaps - dr.getNumLaps();
                        dr.setGapToLeader("+" + lapGap + (lapGap == 1 ? " Lap" : " Laps"));
                    } 
                    else if (dr.getTotalTime() != null && dr.getTotalTime() > 0 && winnerTime > 0) {
                        double gap = dr.getTotalTime() - winnerTime;
                        dr.setGapToLeader(String.format("+%.3fs", gap));
                    } 
                    else {
                        dr.setGapToLeader("-");
                    }
                }
            } else {
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
            for (DriverResult dr : session.getDriverResults()) {
                dr.setGapToLeader("-");
            }
        }
        driverResultRepository.saveAll(session.getDriverResults());
    }

    @Transactional
    public void deleteEvent(Long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        Tier tier = event.getTier();
        
        if (tier != null) {
            tier.getEvents().remove(event);
        }
        
        eventRepository.delete(event);
        
        if (tier != null) {
            recalculateStandings(tier.getId());
        }
    }

    public int getPointsForPosition(List<SessionPointConfig> configs, SessionResult session, int position) {
        if (session == null) return 0;
        int sessionType = session.getSessionType();
        
        int lookupSessionType = sessionType;
        boolean isSprintWeekend = session.getEvent() != null && session.getEvent().getSessionResults().stream()
                .anyMatch(s -> s.getSessionType() == 16);
        
        if (sessionType == 15) {
            if (isSprintWeekend) {
                lookupSessionType = 19;
            } else {
                lookupSessionType = 15;
            }
        } else if (sessionType == 16) {
            lookupSessionType = 15;
        }

        final int finalLookupType = lookupSessionType;

        Optional<SessionPointConfig> config = configs.stream()
                .filter(c -> c.getSessionType() == finalLookupType && c.getPosition() == position)
                .findFirst();

        if (config.isPresent()) {
            return config.get().getPoints();
        }

        if (finalLookupType == 19) {
            if (position >= 1 && position <= 8) {
                int[] pointsMap = {0, 8, 7, 6, 5, 4, 3, 2, 1};
                return pointsMap[position];
            }
            return 0;
        }

        boolean isRace = (finalLookupType >= 15 && finalLookupType <= 17);
        if (isRace && position >= 1 && position <= 10) {
            int[] pointsMap = {0, 25, 18, 15, 12, 10, 8, 6, 4, 2, 1};
            return pointsMap[position];
        }

        return 0;
    }

    private String getDriverName(LeagueSessionState state, ParticipantData p) {
        String key = p.getName() + "|" + p.getRaceNumber() + "|" + p.getDriverId() + "|" + CountryProvider.getCountryInfo(p.getNationality()).getName();
        String overridden = state.getDriverNameOverrides().get(key);
        if (overridden != null && !overridden.isEmpty()) return overridden;

        return p.getName();
    }

    private boolean isAi(LeagueSessionState state, ParticipantData p, int carIndex) {
        if (carIndex >= 0 && carIndex < state.getIsHuman().length && state.getIsHuman()[carIndex]) {
            return false;
        }
        return p.getAiControlled() == 1;
    }

    public void applyExtraPoints(SessionResult session, League league, List<SessionPointConfig> pointConfigs) {
        List<ExtraPointRule> rules = extraPointRuleRepository.findByLeagueAndSessionType(league, session.getSessionType());
        
        Map<DriverResult, Integer> bonusMap = new HashMap<>();
        for (DriverResult dr : session.getDriverResults()) {
            bonusMap.put(dr, 0);
        }

        for (ExtraPointRule rule : rules) {
            List<DriverResult> candidates = session.getDriverResults().stream()
                .filter(dr -> {
                    if (rule.getExcludeAi() != null && rule.getExcludeAi() && dr.isAi()) return false;
                    if (rule.getMustFinish() != null && rule.getMustFinish() && dr.getResultStatus() != 3) return false;
                    if (rule.getOnlyForPointScorers() != null && rule.getOnlyForPointScorers()) {
                        int basePts = getPointsForPosition(pointConfigs, session, dr.getPosition());
                        if (basePts <= 0) return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

            if (candidates.isEmpty()) continue;

            if (rule.getMetric() == ExtraPointRule.Metric.PLACES_GAINED) {
                List<DriverResult> validCandidates = candidates.stream()
                    .filter(dr -> dr.getGridPosition() != null && dr.getGridPosition() > 0 && dr.getPosition() != null)
                    .collect(Collectors.toList());
                if (validCandidates.isEmpty()) continue;

                int maxGained = validCandidates.stream()
                    .mapToInt(dr -> dr.getGridPosition() - dr.getPosition())
                    .max()
                    .orElse(Integer.MIN_VALUE);

                if (rule.getRuleType() == ExtraPointRule.RuleType.HIGHEST_VALUE) {
                    validCandidates.stream()
                        .filter(dr -> (dr.getGridPosition() - dr.getPosition()) == maxGained)
                        .forEach(dr -> bonusMap.put(dr, bonusMap.get(dr) + rule.getPoints()));
                }
            }
            else if (rule.getMetric() == ExtraPointRule.Metric.FASTEST_LAP) {
                List<DriverResult> validCandidates = candidates.stream()
                    .filter(dr -> dr.getBestLapTime() != null && dr.getBestLapTime() > 0)
                    .collect(Collectors.toList());
                if (validCandidates.isEmpty()) continue;

                float minLap = validCandidates.stream()
                    .map(DriverResult::getBestLapTime)
                    .min(Float::compare)
                    .orElse(Float.MAX_VALUE);

                if (rule.getRuleType() == ExtraPointRule.RuleType.LOWEST_VALUE) {
                    validCandidates.stream()
                        .filter(dr -> dr.getBestLapTime().equals(minLap))
                        .forEach(dr -> bonusMap.put(dr, bonusMap.get(dr) + rule.getPoints()));
                }
            }
            else if (rule.getMetric() == ExtraPointRule.Metric.PENALTIES) {
                if (rule.getRuleType() == ExtraPointRule.RuleType.THRESHOLD_BELOW) {
                    double threshold = rule.getThresholdValue() != null ? rule.getThresholdValue() : 0.0;
                    candidates.stream()
                        .filter(dr -> (dr.getPenalties() != null ? dr.getPenalties() : 0) <= threshold)
                        .forEach(dr -> bonusMap.put(dr, bonusMap.get(dr) + rule.getPoints()));
                } else if (rule.getRuleType() == ExtraPointRule.RuleType.LOWEST_VALUE) {
                    int minPenalties = candidates.stream()
                        .mapToInt(dr -> dr.getPenalties() != null ? dr.getPenalties() : 0)
                        .min()
                        .orElse(Integer.MAX_VALUE);
                    candidates.stream()
                        .filter(dr -> (dr.getPenalties() != null ? dr.getPenalties() : 0) == minPenalties)
                        .forEach(dr -> bonusMap.put(dr, bonusMap.get(dr) + rule.getPoints()));
                }
            }
            else if (rule.getMetric() == ExtraPointRule.Metric.WARNINGS) {
                if (rule.getRuleType() == ExtraPointRule.RuleType.THRESHOLD_BELOW) {
                    double threshold = rule.getThresholdValue() != null ? rule.getThresholdValue() : 0.0;
                    candidates.stream()
                        .filter(dr -> (dr.getWarnings() != null ? dr.getWarnings() : 0) <= threshold)
                        .forEach(dr -> bonusMap.put(dr, bonusMap.get(dr) + rule.getPoints()));
                } else if (rule.getRuleType() == ExtraPointRule.RuleType.LOWEST_VALUE) {
                    int minWarnings = candidates.stream()
                        .mapToInt(dr -> dr.getWarnings() != null ? dr.getWarnings() : 0)
                        .min()
                        .orElse(Integer.MAX_VALUE);
                    candidates.stream()
                        .filter(dr -> (dr.getWarnings() != null ? dr.getWarnings() : 0) == minWarnings)
                        .forEach(dr -> bonusMap.put(dr, bonusMap.get(dr) + rule.getPoints()));
                }
            }
            else if (rule.getMetric() == ExtraPointRule.Metric.PENALTIES_AND_WARNINGS) {
                if (rule.getRuleType() == ExtraPointRule.RuleType.THRESHOLD_BELOW) {
                    double threshold = rule.getThresholdValue() != null ? rule.getThresholdValue() : 0.0;
                    candidates.stream()
                        .filter(dr -> ((dr.getPenalties() != null ? dr.getPenalties() : 0) + (dr.getWarnings() != null ? dr.getWarnings() : 0)) <= threshold)
                        .forEach(dr -> bonusMap.put(dr, bonusMap.get(dr) + rule.getPoints()));
                } else if (rule.getRuleType() == ExtraPointRule.RuleType.LOWEST_VALUE) {
                    int minTotal = candidates.stream()
                        .mapToInt(dr -> (dr.getPenalties() != null ? dr.getPenalties() : 0) + (dr.getWarnings() != null ? dr.getWarnings() : 0))
                        .min()
                        .orElse(Integer.MAX_VALUE);
                    candidates.stream()
                        .filter(dr -> ((dr.getPenalties() != null ? dr.getPenalties() : 0) + (dr.getWarnings() != null ? dr.getWarnings() : 0)) == minTotal)
                        .forEach(dr -> bonusMap.put(dr, bonusMap.get(dr) + rule.getPoints()));
                }
            }
            else if (rule.getMetric() == ExtraPointRule.Metric.GAP_TO_PREVIOUS) {
                List<DriverResult> sortedCandidates = candidates.stream()
                    .filter(dr -> dr.getPosition() != null && dr.getTotalTime() != null && dr.getTotalTime() > 0 && dr.getNumLaps() != null)
                    .sorted(Comparator.comparingInt(DriverResult::getPosition))
                    .collect(Collectors.toList());

                if (sortedCandidates.size() < 2) continue;

                Map<DriverResult, Double> gapMap = new HashMap<>();
                for (int j = 1; j < sortedCandidates.size(); j++) {
                    DriverResult dr = sortedCandidates.get(j);
                    DriverResult prevDr = sortedCandidates.get(j - 1);
                    if (dr.getNumLaps().equals(prevDr.getNumLaps())) {
                        double gap = dr.getTotalTime() - prevDr.getTotalTime();
                        if (gap >= 0) {
                           gapMap.put(dr, gap);
                        }
                    }
                }

                if (gapMap.isEmpty()) continue;

                if (rule.getRuleType() == ExtraPointRule.RuleType.LOWEST_VALUE) {
                    double minGap = gapMap.values().stream()
                        .min(Double::compare)
                        .orElse(Double.MAX_VALUE);

                    gapMap.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(minGap))
                        .forEach(entry -> bonusMap.put(entry.getKey(), bonusMap.get(entry.getKey()) + rule.getPoints()));
                }
            }
        }

        for (DriverResult dr : session.getDriverResults()) {
            int finishPoints = getPointsForPosition(pointConfigs, session, dr.getPosition());
            int bonus = bonusMap.getOrDefault(dr, 0);
            dr.setPointsAwarded(finishPoints + bonus);
        }
        
        driverResultRepository.saveAll(session.getDriverResults());
    }
}

