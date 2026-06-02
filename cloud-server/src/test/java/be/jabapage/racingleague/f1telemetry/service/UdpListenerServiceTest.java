package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.model.PacketHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UdpListenerServiceTest {

    @Mock
    private TelemetryProcessingService telemetryProcessingService;

    @InjectMocks
    private UdpListenerService udpListenerService;

    @Test
    public void testUdpListenerReceivesAndRoutesPacket() throws Exception {
        ReflectionTestUtils.setField(udpListenerService, "udpToken", "mock-udp-token");

        // Prepare 29 bytes packet header
        byte[] packetData = new byte[29];
        ByteBuffer buffer = ByteBuffer.wrap(packetData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 2026); // format
        buffer.put((byte) 26); // year
        buffer.put((byte) 1); // major
        buffer.put((byte) 0); // minor
        buffer.put((byte) 1); // version
        buffer.put((byte) 1); // packetId (Session)
        buffer.putLong(123456789L); // sessionUID
        buffer.putFloat(10.5f); // sessionTime
        buffer.putInt(99); // frameId
        buffer.putInt(99); // overallFrameId
        buffer.put((byte) 0); // playerCarIndex
        buffer.put((byte) 0); // secondaryPlayerCarIndex

        // Mock DatagramSocket construction
        try (MockedConstruction<DatagramSocket> mockedSocket = Mockito.mockConstruction(DatagramSocket.class, (mock, context) -> {
            doAnswer(invocation -> {
                DatagramPacket packet = invocation.getArgument(0);
                // Copy header bytes into packet
                System.arraycopy(packetData, 0, packet.getData(), 0, packetData.length);
                packet.setLength(packetData.length);
                
                // Break loop after copy by turning off running flag
                ReflectionTestUtils.setField(udpListenerService, "running", false);
                return null;
            }).when(mock).receive(any(DatagramPacket.class));
        })) {
            // Setup running flag and call listen() synchronously on this thread!
            ReflectionTestUtils.setField(udpListenerService, "running", true);
            ReflectionTestUtils.invokeMethod(udpListenerService, "listen");

            // Verify telemetryProcessingService.processPacket was invoked!
            verify(telemetryProcessingService).processPacket(
                    eq("mock-udp-token"),
                    any(PacketHeader.class),
                    any(ByteBuffer.class)
            );
        }
    }
}
