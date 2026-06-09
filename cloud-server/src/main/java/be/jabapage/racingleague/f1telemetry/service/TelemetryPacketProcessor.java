package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.entity.DriverMapping;
import be.jabapage.racingleague.f1telemetry.entity.League;
import be.jabapage.racingleague.f1telemetry.entity.Tier;
import be.jabapage.racingleague.f1telemetry.entity.LapResult;
import be.jabapage.racingleague.f1telemetry.model.*;
import be.jabapage.racingleague.f1telemetry.repository.DriverMappingRepository;
import be.jabapage.racingleague.f1telemetry.repository.LapResultRepository;
import be.jabapage.racingleague.f1telemetry.repository.LeagueRepository;
import be.jabapage.racingleague.f1telemetry.repository.TierRepository;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class TelemetryPacketProcessor {

    @Autowired
    private LeagueRepository leagueRepository;
    @Autowired
    private TierRepository tierRepository;
    @Autowired
    private DriverMappingRepository driverMappingRepository;
    @Autowired
    private LapResultRepository lapResultRepository;

    @Autowired
    private TelemetryStateService telemetryStateService;
    @Autowired
    private TelemetryResultsService telemetryResultsService;
    @Autowired
    private LiveDashboardService liveDashboardService;
    @Autowired
    private Broadcaster broadcaster;

    public synchronized void processPacket(String token, PacketHeader header, ByteBuffer buffer) {
        LeagueSessionState state = telemetryStateService.getOrCreateState(token);
        if (state == null) {
            log.warn("Received packet for unknown token: {}", token);
            return;
        }

        long now = System.currentTimeMillis();
        long packetSessionUID = header.getSessionUID();

        if (header.getPacketId() == 8) {
            log.info("Incoming Packet 8 (Final Classification) for UID: {} (League: {})", packetSessionUID, state.getLeagueId());
        }

        boolean sessionChanged = (packetSessionUID != 0 && packetSessionUID != state.getCurrentSessionUID());
        boolean timeout = (now - state.getLastPacketTime() > 900000 && state.getLastPacketTime() > 0);

        if (sessionChanged || timeout) {
            log.info("{} detected for league {}, resetting live tracking state. (New UID: {}, Old UID: {}, Gap: {}ms)",
                timeout ? "Timeout" : "Session change",
                state.getLeagueId(),
                packetSessionUID, state.getCurrentSessionUID(), (now - state.getLastPacketTime()));
            
            state.reset();
            telemetryStateService.clearState(state.getTierId());
            state.setCurrentSessionUID(packetSessionUID);
            broadcaster.broadcastLeaderboard(state.getTierId(), Collections.emptyList());
        }
        state.setLastPacketTime(now);

        if (state.getCurrentSessionUID() != -1 && state.getCurrentSessionUID() != 0 && packetSessionUID == 0) {
            return;
        }
        
        if (state.getCurrentSessionUID() != -1 && packetSessionUID != 0 && packetSessionUID != state.getCurrentSessionUID()) {
            return;
        }

        switch (header.getPacketId()) {
            case 1:
                state.setCurrentSession(PacketSessionData.fromByteBuffer(buffer, header));
                liveDashboardService.broadcastSessionInfo(state);
                break;
            case 2:
                PacketLapData newLapData = PacketLapData.fromByteBuffer(buffer, header);
                processLapData(state, newLapData);
                state.setCurrentLapData(newLapData);
                liveDashboardService.broadcastLeaderboard(state);
                liveDashboardService.broadcastSessionInfo(state);
                break;
            case 3:
                PacketEventData eventData = PacketEventData.fromByteBuffer(buffer, header);
                if ("SEND".equals(eventData.getEventStringCode())) {
                    log.info("Session Ended event (SEND) received for UID: {}. Triggering result save.", header.getSessionUID());
                    telemetryResultsService.saveResultsFromLiveState(state, header.getSessionUID());
                } else if ("DRSE".equals(eventData.getEventStringCode())) {
                    log.info("DRS Enabled event received for league {}", state.getLeagueId());
                    state.setDrsEnabled(true);
                    liveDashboardService.broadcastSessionInfo(state);
                } else if ("DRSD".equals(eventData.getEventStringCode())) {
                    log.info("DRS Disabled event received for league {}", state.getLeagueId());
                    state.setDrsEnabled(false);
                    liveDashboardService.broadcastSessionInfo(state);
                }
                break;
            case 4:
                PacketParticipantsData participants = PacketParticipantsData.fromByteBuffer(buffer, header);
                state.setCurrentParticipants(participants);
                autoDiscoverDrivers(state, participants);
                break;
            case 7:
                state.setCurrentCarStatus(PacketCarStatusData.fromByteBuffer(buffer, header));
                liveDashboardService.broadcastLeaderboard(state);
                break;
            case 10:
                state.setCurrentCarDamageData(PacketCarDamageData.fromByteBuffer(buffer, header));
                liveDashboardService.broadcastLeaderboard(state);
                break;
            case 11:
                PacketSessionHistoryData history = PacketSessionHistoryData.fromByteBuffer(buffer, header);
                processSessionHistory(state, history);
                liveDashboardService.broadcastLeaderboard(state);
                break;
            case 8:
                PacketFinalClassificationData classification = PacketFinalClassificationData.fromByteBuffer(buffer, header);
                telemetryResultsService.handleFinalClassification(state, classification);
                telemetryStateService.clearState(state.getTierId());
                break;
            default:
                break;
        }

        telemetryStateService.saveState(state);
    }

    private void processLapData(LeagueSessionState state, PacketLapData packet) {
        for (int i = 0; i < packet.getLapData().size(); i++) {
            LapData ld = packet.getLapData().get(i);
            int carIndex = i;

            if (ld.getSector() == 1 && ld.getSector1TimeInMS() > 0) {
                state.getLastS1()[carIndex] = ld.getSector1TimeInMS();
            } else if (ld.getSector() == 2 && ld.getSector2TimeInMS() > 0) {
                state.getLastS2()[carIndex] = ld.getSector2TimeInMS();
            }

            if (ld.getCurrentLapInvalid() == 1) {
                state.getLapInvalid()[carIndex] = true;
            }

            boolean lapFinished = state.getLastLapNum()[carIndex] > 0 && ld.getCurrentLapNum() > state.getLastLapNum()[carIndex];
            boolean raceFinished = state.getLastLapNum()[carIndex] > 0 && ld.getResultStatus() == 3 && ld.getCurrentLapNum() == state.getLastLapNum()[carIndex];

            if (lapFinished || raceFinished) {
                long lastLapTime = ld.getLastLapTimeInMS();
                long s1 = state.getLastS1()[carIndex];
                long s2 = state.getLastS2()[carIndex];
                long s3 = lastLapTime - s1 - s2;

                if (!state.getLapInvalid()[carIndex] && lastLapTime > 0) {
                    if (lastLapTime < state.getDriverBestLap()[carIndex] || state.getDriverBestLap()[carIndex] == 0) state.getDriverBestLap()[carIndex] = lastLapTime;
                    if (lastLapTime < state.getSessionBestLap()) state.setSessionBestLap(lastLapTime);
                    
                    if (s1 > 0) {
                        if (s1 < state.getDriverBestS1()[carIndex] || state.getDriverBestS1()[carIndex] == 0) state.getDriverBestS1()[carIndex] = s1;
                        if (s1 < state.getSessionBestS1()) state.setSessionBestS1(s1);
                    }
                    if (s2 > 0) {
                        if (s2 < state.getDriverBestS2()[carIndex] || state.getDriverBestS2()[carIndex] == 0) state.getDriverBestS2()[carIndex] = s2;
                        if (s2 < state.getSessionBestS2()) state.setSessionBestS2(s2);
                    }
                    if (s3 > 0) {
                        if (s3 < state.getDriverBestS3()[carIndex] || state.getDriverBestS3()[carIndex] == 0) state.getDriverBestS3()[carIndex] = s3;
                        if (s3 < state.getSessionBestS3()) state.setSessionBestS3(s3);
                    }
                }

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
                state.getLapInvalid()[carIndex] = false;

                if (raceFinished) {
                    state.getLastLapNum()[carIndex] = ld.getCurrentLapNum() + 1;
                }
            }

            if (ld.getResultStatus() != 3) {
                state.getLastLapNum()[carIndex] = ld.getCurrentLapNum();
            }
            if (state.getCurrentCarStatus() != null && carIndex < state.getCurrentCarStatus().getCarStatusData().size()) {
                int visualTyre = state.getCurrentCarStatus().getCarStatusData().get(carIndex).getVisualTyreCompound();
                if (visualTyre == 0) {
                    visualTyre = state.getCurrentCarStatus().getCarStatusData().get(carIndex).getActualTyreCompound();
                }
                state.getLastTyre()[carIndex] = visualTyre;
            }
        }
    }

    private void autoDiscoverDrivers(LeagueSessionState state, PacketParticipantsData participants) {
        if (state.getLeagueId() == null || state.getLeagueId() == -1 || state.getTierId() == null || state.getTierId() == -1) return;

        League league = leagueRepository.findById(state.getLeagueId()).orElse(null);
        if (league == null) return;

        boolean changed = false;
        for (int i = 0; i < participants.getParticipants().size(); i++) {
            ParticipantData p = participants.getParticipants().get(i);
            if (p.getName() == null || p.getName().isEmpty()) continue;

            if (p.getAiControlled() == 0 && i < state.getIsHuman().length) {
                state.getIsHuman()[i] = true;
            }

            String country = CountryProvider.getCountryInfo(p.getNationality()).getName();
            String key = p.getName() + "|" + p.getRaceNumber() + "|" + p.getDriverId() + "|" + country;
            if (state.getDriverNameOverrides().containsKey(key)) continue;

            Optional<DriverMapping> mapping = driverMappingRepository.findByLeagueAndTelemetryNameAndRaceNumberAndDriverIdAndCountry(league, p.getName(), p.getRaceNumber(), p.getDriverId(), country);
            if (mapping.isEmpty()) {
                DriverMapping newMapping = new DriverMapping();
                newMapping.setLeague(league);
                newMapping.setTelemetryName(p.getName());
                newMapping.setRaceNumber(p.getRaceNumber());
                newMapping.setDriverId(p.getDriverId());
                newMapping.setCountry(CountryProvider.getCountryInfo(p.getNationality()).getName());
                
                Tier tier = tierRepository.findById(state.getTierId()).orElse(null);
                if (tier != null) {
                    newMapping.getTiers().add(tier);
                }
                
                driverMappingRepository.save(newMapping);
                state.getDriverNameOverrides().put(key, "");
                changed = true;
                log.info("Auto-discovered new driver in league {}: {} (#{}, ID: {})", league.getId(), p.getName(), p.getRaceNumber(), p.getDriverId());
            } else {
                DriverMapping existingMapping = mapping.get();
                Tier tier = tierRepository.findById(state.getTierId()).orElse(null);
                if (tier != null && !existingMapping.getTiers().contains(tier)) {
                    existingMapping.getTiers().add(tier);
                    driverMappingRepository.save(existingMapping);
                }
                state.getDriverNameOverrides().put(key, existingMapping.getOverriddenName() != null ? existingMapping.getOverriddenName() : "");
                if (existingMapping.isReserve()) {
                    state.getReserveDrivers().add(key);
                }
            }
        }
    }

    private void processSessionHistory(LeagueSessionState state, PacketSessionHistoryData history) {
        int carIdx = history.getCarIdx();
        if (carIdx < 0 || carIdx >= state.getDriverBestLap().length) {
            return;
        }

        int numLaps = history.getNumLaps();
        
        // 1. Process Best Lap
        int bestLapIdx = history.getBestLapTimeLapNum() - 1;
        if (bestLapIdx >= 0 && bestLapIdx < numLaps && bestLapIdx < history.getLapHistoryData().size()) {
            LapHistoryData lap = history.getLapHistoryData().get(bestLapIdx);
            if ((lap.getLapValidBitFlags() & 0x01) != 0 && lap.getLapTimeInMS() > 0) {
                long time = lap.getLapTimeInMS();
                state.getDriverBestLap()[carIdx] = time;
                if (state.getSessionBestLap() == 0 || time < state.getSessionBestLap()) {
                    state.setSessionBestLap(time);
                }
            }
        }

        // 2. Process Best Sector 1
        int bestS1Idx = history.getBestSector1LapNum() - 1;
        if (bestS1Idx >= 0 && bestS1Idx < numLaps && bestS1Idx < history.getLapHistoryData().size()) {
            LapHistoryData lap = history.getLapHistoryData().get(bestS1Idx);
            if ((lap.getLapValidBitFlags() & 0x02) != 0 && lap.getSector1TimeInMS() > 0) {
                long time = lap.getSector1TimeInMS();
                state.getDriverBestS1()[carIdx] = time;
                if (state.getSessionBestS1() == 0 || time < state.getSessionBestS1()) {
                    state.setSessionBestS1(time);
                }
            }
        }

        // 3. Process Best Sector 2
        int bestS2Idx = history.getBestSector2LapNum() - 1;
        if (bestS2Idx >= 0 && bestS2Idx < numLaps && bestS2Idx < history.getLapHistoryData().size()) {
            LapHistoryData lap = history.getLapHistoryData().get(bestS2Idx);
            if ((lap.getLapValidBitFlags() & 0x04) != 0 && lap.getSector2TimeInMS() > 0) {
                long time = lap.getSector2TimeInMS();
                state.getDriverBestS2()[carIdx] = time;
                if (state.getSessionBestS2() == 0 || time < state.getSessionBestS2()) {
                    state.setSessionBestS2(time);
                }
            }
        }

        // 4. Process Best Sector 3
        int bestS3Idx = history.getBestSector3LapNum() - 1;
        if (bestS3Idx >= 0 && bestS3Idx < numLaps && bestS3Idx < history.getLapHistoryData().size()) {
            LapHistoryData lap = history.getLapHistoryData().get(bestS3Idx);
            if ((lap.getLapValidBitFlags() & 0x08) != 0 && lap.getSector3TimeInMS() > 0) {
                long time = lap.getSector3TimeInMS();
                state.getDriverBestS3()[carIdx] = time;
                if (state.getSessionBestS3() == 0 || time < state.getSessionBestS3()) {
                    state.setSessionBestS3(time);
                }
            }
        }
    }
}
