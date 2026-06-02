package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.model.DriverBoardState;
import be.jabapage.racingleague.f1telemetry.model.SessionInfo;
import com.vaadin.flow.shared.Registration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class BroadcasterTest {

    private Broadcaster broadcaster;

    @BeforeEach
    public void setUp() {
        broadcaster = new Broadcaster();
    }

    @Test
    public void testRegisterAndUnregisterLeaderboard() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<DriverBoardState>> receivedRef = new AtomicReference<>();

        Registration reg = broadcaster.registerLeaderboard(10L, data -> {
            receivedRef.set(data);
            latch.countDown();
        });

        assertTrue(broadcaster.hasListeners(10L));
        assertTrue(broadcaster.getActiveTierIds().contains(10L));

        List<DriverBoardState> mockData = new ArrayList<>();
        broadcaster.broadcastLeaderboard(10L, mockData);

        // Wait for thread pool to invoke listener
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertSame(mockData, receivedRef.get());

        // Unregister
        reg.remove();
        assertFalse(broadcaster.hasListeners(10L));
    }

    @Test
    public void testRegisterAndUnregisterSessionInfo() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SessionInfo> receivedRef = new AtomicReference<>();

        Registration reg = broadcaster.registerSessionInfo(10L, info -> {
            receivedRef.set(info);
            latch.countDown();
        });

        assertTrue(broadcaster.hasListeners(10L));
        assertTrue(broadcaster.getActiveTierIds().contains(10L));

        SessionInfo mockInfo = SessionInfo.builder().sessionType("Race").build();
        broadcaster.broadcastSessionInfo(10L, mockInfo);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertSame(mockInfo, receivedRef.get());

        reg.remove();
        assertFalse(broadcaster.hasListeners(10L));
    }

    @Test
    public void testBroadcastWithNoListenersDoesNotThrow() {
        assertDoesNotThrow(() -> {
            broadcaster.broadcastLeaderboard(999L, Collections.emptyList());
            broadcaster.broadcastSessionInfo(999L, SessionInfo.builder().build());
        });
    }
}
