package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.model.DriverBoardState;
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
    private final List<Consumer<String>> sessionTypeListeners = new LinkedList<>();

    public synchronized Registration registerLeaderboard(Consumer<List<DriverBoardState>> listener) {
        leaderboardListeners.add(listener);
        return () -> {
            synchronized (Broadcaster.this) {
                leaderboardListeners.remove(listener);
            }
        };
    }

    public synchronized Registration registerSessionType(Consumer<String> listener) {
        sessionTypeListeners.add(listener);
        return () -> {
            synchronized (Broadcaster.this) {
                sessionTypeListeners.remove(listener);
            }
        };
    }

    public synchronized void broadcastLeaderboard(List<DriverBoardState> data) {
        for (Consumer<List<DriverBoardState>> listener : leaderboardListeners) {
            EXECUTOR.execute(() -> listener.accept(data));
        }
    }

    public synchronized void broadcastSessionType(String sessionType) {
        for (Consumer<String> listener : sessionTypeListeners) {
            EXECUTOR.execute(() -> listener.accept(sessionType));
        }
    }
}
