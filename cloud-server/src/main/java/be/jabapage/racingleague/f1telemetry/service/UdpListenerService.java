package be.jabapage.racingleague.f1telemetry.service;

import be.jabapage.racingleague.f1telemetry.model.PacketHeader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@ConditionalOnProperty(name = "telemetry.udp.enabled", havingValue = "true", matchIfMissing = true)
public class UdpListenerService {

    private static final int PORT = 20777;
    private static final int BUFFER_SIZE = 2048;

    private DatagramSocket socket;
    private boolean running;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Autowired
    private TelemetryProcessingService telemetryProcessingService;

    @PostConstruct
    public void start() {
        running = true;
        executorService.submit(this::listen);
        log.info("UDP Listener Service started on port {}", PORT);
    }

    private void listen() {
        try {
            socket = new DatagramSocket(PORT);
            byte[] buffer = new byte[BUFFER_SIZE];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
                PacketHeader header = PacketHeader.fromByteBuffer(byteBuffer);

                telemetryProcessingService.processPacket(header, byteBuffer);
            }
        } catch (Exception e) {
            if (running) {
                log.error("Error in UDP Listener", e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        executorService.shutdown();
        log.info("UDP Listener Service stopped.");
    }
}
