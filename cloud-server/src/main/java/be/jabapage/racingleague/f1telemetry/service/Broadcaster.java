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
    private static final Executor EXECUTOR = Executors.newCachedThreadPool();
    private final Map<Long, List<Consumer<List<DriverBoardState>>>> leaderboardListeners = new ConcurrentHashMap<>();
    private final Map<Long, List<Consumer<SessionInfo>>> sessionInfoListeners = new ConcurrentHashMap<>();

    public synchronized Registration registerLeaderboard(Long tierId, Consumer<List<DriverBoardState>> listener) {
        if (tierId == null) return () -> {};
        leaderboardListeners.computeIfAbsent(tierId, k -> new LinkedList<>()).add(listener);
        return () -> {
            synchronized (Broadcaster.this) {
                List<Consumer<List<DriverBoardState>>> listeners = leaderboardListeners.get(tierId);
                if (listeners != null) {
                    listeners.remove(listener);
                    if (listeners.isEmpty()) {
                        leaderboardListeners.remove(tierId);
                    }
                }
            }
        };
    }

    public synchronized Registration registerSessionInfo(Long tierId, Consumer<SessionInfo> listener) {
        if (tierId == null) return () -> {};
        sessionInfoListeners.computeIfAbsent(tierId, k -> new LinkedList<>()).add(listener);
        return () -> {
            synchronized (Broadcaster.this) {
                List<Consumer<SessionInfo>> listeners = sessionInfoListeners.get(tierId);
                if (listeners != null) {
                    listeners.remove(listener);
                    if (listeners.isEmpty()) {
                        sessionInfoListeners.remove(tierId);
                    }
                }
            }
        };
    }

    public synchronized boolean hasListeners(Long tierId) {
        if (tierId == null) return false;
        List<Consumer<List<DriverBoardState>>> lListeners = leaderboardListeners.get(tierId);
        List<Consumer<SessionInfo>> sListeners = sessionInfoListeners.get(tierId);
        return (lListeners != null && !lListeners.isEmpty()) || (sListeners != null && !sListeners.isEmpty());
    }

    public synchronized java.util.Set<Long> getActiveTierIds() {
        java.util.Set<Long> ids = new java.util.HashSet<>(leaderboardListeners.keySet());
        ids.addAll(sessionInfoListeners.keySet());
        return ids;
    }

    public synchronized void broadcastLeaderboard(Long tierId, List<DriverBoardState> data) {
        if (tierId == null) return;
        List<Consumer<List<DriverBoardState>>> listeners = leaderboardListeners.get(tierId);
        if (listeners != null) {
            for (Consumer<List<DriverBoardState>> listener : listeners) {
                EXECUTOR.execute(() -> listener.accept(data));
            }
        }
    }

    public synchronized void broadcastSessionInfo(Long tierId, SessionInfo info) {
        if (tierId == null) return;
        List<Consumer<SessionInfo>> listeners = sessionInfoListeners.get(tierId);
        if (listeners != null) {
            for (Consumer<SessionInfo> listener : listeners) {
                EXECUTOR.execute(() -> listener.accept(info));
            }
        }
    }
}
