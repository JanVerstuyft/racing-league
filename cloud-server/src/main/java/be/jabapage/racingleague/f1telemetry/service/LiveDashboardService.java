package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.model.*;
import be.jabapage.racingleague.f1telemetry.util.CountryProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LiveDashboardService {

    @Autowired
    @Lazy
    private TelemetryStateService telemetryStateService;

    @Autowired
    private Broadcaster broadcaster;

    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private final Map<Long, Long> lastLeaderboardBroadcast = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Boolean> leaderboardBroadcastScheduled = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Long> lastSessionInfoBroadcast = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, Boolean> sessionInfoBroadcastScheduled = new java.util.concurrent.ConcurrentHashMap<>();

    @jakarta.annotation.PreDestroy
    public void destroy() {
        scheduler.shutdown();
    }

    public void broadcastSessionInfo(LeagueSessionState state) {
        if (state.getTierId() == null) return;
        long tierId = state.getTierId();
        long now = System.currentTimeMillis();
        long last = lastSessionInfoBroadcast.getOrDefault(tierId, 0L);
        
        if (now - last >= 500) {
            lastSessionInfoBroadcast.put(tierId, now);
            sessionInfoBroadcastScheduled.put(tierId, false);
            SessionInfo info = buildSessionInfo(state);
            if (info != null) {
                broadcaster.broadcastSessionInfo(tierId, info);
            }
        } else {
            if (sessionInfoBroadcastScheduled.putIfAbsent(tierId, true) == null || !sessionInfoBroadcastScheduled.get(tierId)) {
                sessionInfoBroadcastScheduled.put(tierId, true);
                long delay = 500 - (now - last);
                try {
                    scheduler.schedule(() -> {
                        sessionInfoBroadcastScheduled.put(tierId, false);
                        telemetryStateService.getLeagueStates().values().stream()
                            .filter(s -> Objects.equals(s.getTierId(), tierId))
                            .findFirst()
                            .ifPresent(this::broadcastSessionInfo);
                    }, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    sessionInfoBroadcastScheduled.put(tierId, false);
                }
            }
        }
    }

    public SessionInfo getSessionInfo(Long tierId) {
        return telemetryStateService.getLeagueStates().values().stream()
                .filter(s -> Objects.equals(s.getTierId(), tierId))
                .findFirst()
                .map(this::buildSessionInfo)
                .orElse(null);
    }

    public SessionInfo buildSessionInfo(LeagueSessionState state) {
        if (state.getCurrentSession() == null) return null;

        String sessionName = TelemetryProcessingService.SESSION_TYPE_NAMES.getOrDefault(
                state.getCurrentSession().getSessionType(),
                "Unknown (" + state.getCurrentSession().getSessionType() + ")"
        );
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

    public void broadcastLeaderboard(LeagueSessionState state) {
        if (state.getTierId() == null) return;
        long tierId = state.getTierId();
        long now = System.currentTimeMillis();
        long last = lastLeaderboardBroadcast.getOrDefault(tierId, 0L);
        
        if (now - last >= 500) {
            lastLeaderboardBroadcast.put(tierId, now);
            leaderboardBroadcastScheduled.put(tierId, false);
            List<DriverBoardState> board = buildLeaderboard(state);
            if (board != null) {
                broadcaster.broadcastLeaderboard(tierId, board);
            }
        } else {
            if (leaderboardBroadcastScheduled.putIfAbsent(tierId, true) == null || !leaderboardBroadcastScheduled.get(tierId)) {
                leaderboardBroadcastScheduled.put(tierId, true);
                long delay = 500 - (now - last);
                try {
                    scheduler.schedule(() -> {
                        leaderboardBroadcastScheduled.put(tierId, false);
                        telemetryStateService.getLeagueStates().values().stream()
                            .filter(s -> Objects.equals(s.getTierId(), tierId))
                            .findFirst()
                            .ifPresent(this::broadcastLeaderboard);
                    }, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    leaderboardBroadcastScheduled.put(tierId, false);
                }
            }
        }
    }

    public List<DriverBoardState> getLeaderboard(Long tierId) {
        return telemetryStateService.getLeagueStates().values().stream()
                .filter(s -> Objects.equals(s.getTierId(), tierId))
                .findFirst()
                .map(this::buildLeaderboard)
                .orElse(Collections.emptyList());
    }

    public List<DriverBoardState> buildLeaderboard(LeagueSessionState state) {
        if (state.getCurrentParticipants() == null || state.getCurrentLapData() == null || state.getCurrentCarStatus() == null || state.getCurrentSession() == null) return null;

        boolean isQualifying = state.getCurrentSession().getSessionType() >= 5 && state.getCurrentSession().getSessionType() <= 14;

        List<DriverBoardState> board = new ArrayList<>();
        int maxCars = (state.getCurrentSession() != null && state.getCurrentSession().getHeader() != null && state.getCurrentSession().getHeader().getPacketFormat() == 2026) ? 24 : 22;
        for (int i = 0; i < maxCars; i++) {
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
            int gameYear = (state.getCurrentSession() != null && state.getCurrentSession().getHeader() != null)
                    ? state.getCurrentSession().getHeader().getGameYear()
                    : 25;
            String carType = TelemetryProcessingService.detectCarType(state.getCurrentParticipants(), gameYear);
            driverState.setTeam(TelemetryProcessingService.getTeamNameStatic(p.getTeamId(), carType));
            driverState.setTeamId(p.getTeamId());
            driverState.setCountry(CountryProvider.getCountryInfo(p.getNationality()).getName());
            driverState.setTyreCompound(TelemetryProcessingService.TYRE_COMPOUNDS.getOrDefault(csd.getVisualTyreCompound(), "Unknown"));
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

            driverState.setBestLapTime(formatLapTimeFull(state.getDriverBestLap()[i]));
            driverState.setBestLap(state.getDriverBestLap()[i] > 0 && state.getDriverBestLap()[i] == state.getSessionBestLap());

            if (isQualifying) {
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

    public String formatTime(long ms) {
        if (ms <= 0) return "-";
        return String.format("+%.3fs", ms / 1000.0f);
    }

    public String formatLapTimeFull(long ms) {
        if (ms <= 0) return "-";
        int minutes = (int) (ms / 60000);
        float seconds = (ms % 60000) / 1000.0f;
        return String.format("%d:%06.3f", minutes, seconds);
    }

    public String getDriverName(LeagueSessionState state, ParticipantData p) {
        String key = p.getName() + "|" + p.getRaceNumber() + "|" + p.getDriverId() + "|" + CountryProvider.getCountryInfo(p.getNationality()).getName();
        String overridden = state.getDriverNameOverrides().get(key);
        if (overridden != null && !overridden.isEmpty()) return overridden;

        return p.getName();
    }

    public boolean isAi(LeagueSessionState state, ParticipantData p, int carIndex) {
        if (carIndex >= 0 && carIndex < state.getIsHuman().length && state.getIsHuman()[carIndex]) {
            return false;
        }
        return p.getAiControlled() == 1;
    }
}
