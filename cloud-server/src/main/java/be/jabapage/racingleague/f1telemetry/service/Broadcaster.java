package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.model.DriverBoardState;
import be.jabapage.racingleague.f1telemetry.model.SessionInfo;
import com.vaadin.flow.shared.Registration;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Service
public class Broadcaster {
    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();
    private final List<Consumer<List<DriverBoardState>>> leaderboardListeners = new LinkedList<>();
    private final List<Consumer<SessionInfo>> sessionInfoListeners = new LinkedList<>();

    public synchronized Registration registerLeaderboard(Consumer<List<DriverBoardState>> listener) {
        leaderboardListeners.add(listener);
        return () -> {
            synchronized (Broadcaster.this) {
                leaderboardListeners.remove(listener);
            }
        };
    }

    public synchronized Registration registerSessionInfo(Consumer<SessionInfo> listener) {
        sessionInfoListeners.add(listener);
        return () -> {
            synchronized (Broadcaster.this) {
                sessionInfoListeners.remove(listener);
            }
        };
    }

    public synchronized void broadcastLeaderboard(List<DriverBoardState> data) {
        for (Consumer<List<DriverBoardState>> listener : leaderboardListeners) {
            EXECUTOR.execute(() -> listener.accept(data));
        }
    }

    public synchronized void broadcastSessionInfo(SessionInfo info) {
        for (Consumer<SessionInfo> listener : sessionInfoListeners) {
            EXECUTOR.execute(() -> listener.accept(info));
        }
    }
}
