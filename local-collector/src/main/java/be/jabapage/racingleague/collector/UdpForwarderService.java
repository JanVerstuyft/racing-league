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
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class UdpForwarderService {

    @Value("${telemetry.udp.port:20777}")
    private int port;

    @Value("${telemetry.cloud.url:http://localhost:8080/api/telemetry}")
    private String cloudUrl;

    @Value("${telemetry.cloud.token:default}")
    private String cloudToken;

    @Value("${telemetry.recording.enabled:false}")
    private boolean recordingEnabled;

    @Value("${telemetry.recording.path:data/recorded_session.bin}")
    private String recordingPath;

    @Value("${telemetry.udp.forward.enabled:false}")
    private boolean forwardEnabled;

    @Value("${telemetry.udp.forward.host:localhost}")
    private String forwardHost;

    @Value("${telemetry.udp.forward.port:20778}")
    private int forwardPort;

    private DatagramSocket socket;
    private DatagramSocket forwardSocket;
    private boolean running;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private InetAddress forwardAddress;

    @Autowired
    private RestTemplate restTemplate;

    @PostConstruct
    public void start() {
        running = true;
        logLocalIpAddresses();
        if (forwardEnabled) {
            try {
                forwardAddress = InetAddress.getByName(forwardHost);
                forwardSocket = new DatagramSocket();
                log.info("UDP Forwarding enabled to {}:{}", forwardHost, forwardPort);
            } catch (Exception e) {
                log.error("Failed to initialize UDP forwarding to {}: {}", forwardHost, e.getMessage());
                forwardEnabled = false;
            }
        }
        executorService.submit(this::listen);
        log.info("UDP Forwarder Service started on port {}", port);
        log.info("Forwarding telemetry to {} with token {}", cloudUrl, cloudToken);
        if (recordingEnabled) {
            log.info("Recording telemetry to {}", recordingPath);
            // Ensure directory exists
            java.io.File file = new java.io.File(recordingPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
        }
    }

    private void logLocalIpAddresses() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        log.info("Local IP Address ({}): {}", networkInterface.getDisplayName(), addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not determine local IP addresses: {}", e.getMessage());
        }
    }

    private void listen() {
        try (java.io.FileOutputStream fos = recordingEnabled ? new java.io.FileOutputStream(recordingPath) : null) {
            socket = new DatagramSocket(port);
            byte[] buffer = new byte[2048];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (Exception e) {
                    if (running) {
                        log.error("Error receiving UDP packet: {}", e.getMessage());
                    }
                    continue;
                }

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

                if (forwardEnabled && forwardAddress != null && forwardSocket != null) {
                    try {
                        DatagramPacket forwardPacket = new DatagramPacket(data, data.length, forwardAddress, forwardPort);
                        forwardSocket.send(forwardPacket);
                        log.debug("Forwarded UDP packet to {}:{}", forwardAddress, forwardPort);
                    } catch (Exception e) {
                        log.warn("Failed to forward UDP packet (destination may be unreachable): {}", e.getMessage());
                    }
                }

                try {
                    String urlWithToken = cloudUrl;
                    if (!urlWithToken.endsWith("/")) urlWithToken += "/";
                    urlWithToken += cloudToken;

                    restTemplate.postForObject(urlWithToken, data, Void.class);
                    log.debug("Forwarded {} bytes to cloud with token {}", data.length, cloudToken);
                } catch (Exception e) {
                    log.error("Failed to forward telemetry to cloud: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            if (running) {
                log.error("Fatal error in UDP Forwarder listener loop", e);
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
        if (forwardSocket != null && !forwardSocket.isClosed()) {
            forwardSocket.close();
        }
        executorService.shutdown();
        log.info("UDP Forwarder Service stopped.");
    }
}
