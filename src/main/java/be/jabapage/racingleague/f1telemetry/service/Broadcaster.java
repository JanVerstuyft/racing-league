package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.model.CarDamageData;
import be.jabapage.racingleague.f1telemetry.model.CarTelemetryData;
import be.jabapage.racingleague.f1telemetry.model.DriverBoardState;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Service
public class Broadcaster {
    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();
    private final List<Consumer<CarTelemetryData>> telemetryListeners = new LinkedList<>();
    private final List<Consumer<CarDamageData>> damageListeners = new LinkedList<>();
    private final List<Consumer<List<DriverBoardState>>> leaderboardListeners = new LinkedList<>();

    public synchronized void registerTelemetry(Consumer<CarTelemetryData> listener) {
        telemetryListeners.add(listener);
    }

    public synchronized void registerDamage(Consumer<CarDamageData> listener) {
        damageListeners.add(listener);
    }

    public synchronized void registerLeaderboard(Consumer<List<DriverBoardState>> listener) {
        leaderboardListeners.add(listener);
    }

    public synchronized void broadcastTelemetry(CarTelemetryData data) {
        for (Consumer<CarTelemetryData> listener : telemetryListeners) {
            EXECUTOR.execute(() -> listener.accept(data));
        }
    }

    public synchronized void broadcastDamage(CarDamageData data) {
        for (Consumer<CarDamageData> listener : damageListeners) {
            EXECUTOR.execute(() -> listener.accept(data));
        }
    }

    public synchronized void broadcastLeaderboard(List<DriverBoardState> data) {
        for (Consumer<List<DriverBoardState>> listener : leaderboardListeners) {
            EXECUTOR.execute(() -> listener.accept(data));
        }
    }
}
