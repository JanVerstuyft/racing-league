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

    public void broadcastSessionInfo(LeagueSessionState state) {
        if (state.getTierId() == null) return;
        SessionInfo info = buildSessionInfo(state);
        if (info != null) {
            broadcaster.broadcastSessionInfo(state.getTierId(), info);
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
        List<DriverBoardState> board = buildLeaderboard(state);
        if (board != null) {
            broadcaster.broadcastLeaderboard(state.getTierId(), board);
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
            int gameYear = (state.getCurrentSession() != null && state.getCurrentSession().getHeader() != null)
                    ? state.getCurrentSession().getHeader().getGameYear()
                    : 25;
            driverState.setTeam(TelemetryProcessingService.getTeamName(p.getTeamId(), gameYear));
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
