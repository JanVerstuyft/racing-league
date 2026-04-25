package be.jabapage.racingleague.collector;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class UdpForwarderService {

    @Value("${telemetry.udp.port:20777}")
    private int port;

    @Value("${telemetry.cloud.url:http://localhost:8080/api/telemetry}")
    private String cloudUrl;

    @Value("${telemetry.recording.enabled:false}")
    private boolean recordingEnabled;

    @Value("${telemetry.recording.path:data/recorded_session.bin}")
    private String recordingPath;

    private DatagramSocket socket;
    private boolean running;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Autowired
    private RestTemplate restTemplate;

    @PostConstruct
    public void start() {
        running = true;
        executorService.submit(this::listen);
        log.info("UDP Forwarder Service started on port {}", port);
        log.info("Forwarding telemetry to {}", cloudUrl);
        if (recordingEnabled) {
            log.info("Recording telemetry to {}", recordingPath);
            // Ensure directory exists
            java.io.File file = new java.io.File(recordingPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
        }
    }

    private void listen() {
        try (java.io.FileOutputStream fos = recordingEnabled ? new java.io.FileOutputStream(recordingPath) : null) {
            socket = new DatagramSocket(port);
            byte[] buffer = new byte[2048];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

                log.debug("Received UDP packet: {} bytes, Data(hex): {}", data.length, bytesToHex(data, 32));

                if (fos != null) {
                    // Write length (short) then data
                    fos.write((data.length >> 8) & 0xFF);
                    fos.write(data.length & 0xFF);
                    fos.write(data);
                    fos.flush(); // Ensure it's written to disk immediately
                }

                try {
                    restTemplate.postForObject(cloudUrl, data, Void.class);
                    log.debug("Forwarded {} bytes to cloud", data.length);
                } catch (Exception e) {
                    log.error("Failed to forward telemetry to cloud: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            if (running) {
                log.error("Error in UDP Forwarder", e);
            }
        }
    }

    private String bytesToHex(byte[] bytes, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, max); i++) {
            sb.append(String.format("%02x ", bytes[i]));
        }
        if (bytes.length > max) sb.append("...");
        return sb.toString();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        executorService.shutdown();
        log.info("UDP Forwarder Service stopped.");
    }
}
