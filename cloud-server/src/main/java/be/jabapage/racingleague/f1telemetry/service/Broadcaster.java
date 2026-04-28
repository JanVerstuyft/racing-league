package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.model.DriverBoardState;
import be.jabapage.racingleague.f1telemetry.model.SessionInfo;
import com.vaadin.flow.shared.Registration;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Service
public class Broadcaster {
    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();
    private final Map<Long, List<Consumer<List<DriverBoardState>>>> leaderboardListeners = new ConcurrentHashMap<>();
    private final Map<Long, List<Consumer<SessionInfo>>> sessionInfoListeners = new ConcurrentHashMap<>();

    public synchronized Registration registerLeaderboard(Long leagueId, Consumer<List<DriverBoardState>> listener) {
        leaderboardListeners.computeIfAbsent(leagueId, k -> new LinkedList<>()).add(listener);
        return () -> {
            synchronized (Broadcaster.this) {
                List<Consumer<List<DriverBoardState>>> listeners = leaderboardListeners.get(leagueId);
                if (listeners != null) {
                    listeners.remove(listener);
                }
            }
        };
    }

    public synchronized Registration registerSessionInfo(Long leagueId, Consumer<SessionInfo> listener) {
        sessionInfoListeners.computeIfAbsent(leagueId, k -> new LinkedList<>()).add(listener);
        return () -> {
            synchronized (Broadcaster.this) {
                List<Consumer<SessionInfo>> listeners = sessionInfoListeners.get(leagueId);
                if (listeners != null) {
                    listeners.remove(listener);
                }
            }
        };
    }

    public synchronized void broadcastLeaderboard(Long leagueId, List<DriverBoardState> data) {
        List<Consumer<List<DriverBoardState>>> listeners = leaderboardListeners.get(leagueId);
        if (listeners != null) {
            for (Consumer<List<DriverBoardState>> listener : listeners) {
                EXECUTOR.execute(() -> listener.accept(data));
            }
        }
    }

    public synchronized void broadcastSessionInfo(Long leagueId, SessionInfo info) {
        List<Consumer<SessionInfo>> listeners = sessionInfoListeners.get(leagueId);
        if (listeners != null) {
            for (Consumer<SessionInfo> listener : listeners) {
                EXECUTOR.execute(() -> listener.accept(info));
            }
        }
    }
}
