package be.jabapage.racingleague.collector;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class UdpForwarderService {

    private final CollectorSettings settings;
    private DatagramSocket socket;
    private DatagramSocket forwardSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    private InetAddress forwardAddress;

    @Autowired
    private RestTemplate restTemplate;

    public UdpForwarderService(CollectorSettings settings) {
        this.settings = settings;
    }

    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) {
            executorService = Executors.newSingleThreadExecutor();
            applyForwardingConfig();
            executorService.submit(this::listen);
            log.info("UDP Forwarder Service started on port {}", settings.getPort());
            log.info("Forwarding telemetry to {} with token {}", settings.getCloudUrl(), settings.getCloudToken());
            if (settings.isRecordingEnabled()) {
                log.info("Recording telemetry to {}", settings.getRecordingPath());
                java.io.File file = new java.io.File(settings.getRecordingPath());
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
            }
        }
    }

    public void restart() {
        log.info("Restarting UDP Forwarder Service with new settings...");
        stop();
        start();
    }

    private void applyForwardingConfig() {
        if (forwardSocket != null && !forwardSocket.isClosed()) {
            forwardSocket.close();
            forwardSocket = null;
        }
        if (settings.isForwardEnabled()) {
            try {
                forwardAddress = InetAddress.getByName(settings.getForwardHost());
                forwardSocket = new DatagramSocket();
                log.info("UDP Forwarding enabled to {}:{}", settings.getForwardHost(), settings.getForwardPort());
            } catch (Exception e) {
                log.error("Failed to initialize UDP forwarding to {}: {}", settings.getForwardHost(), e.getMessage());
            }
        }
    }

    public List<String> getLocalIpAddresses() {
        List<String> ips = new ArrayList<>();
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
                        ips.add(networkInterface.getDisplayName() + ": " + addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not determine local IP addresses: {}", e.getMessage());
        }
        return ips;
    }

    private void listen() {
        try (java.io.FileOutputStream fos = settings.isRecordingEnabled() ? new java.io.FileOutputStream(settings.getRecordingPath()) : null) {
            socket = new DatagramSocket(settings.getPort());
            byte[] buffer = new byte[2048];

            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (Exception e) {
                    if (running.get()) {
                        log.error("Error receiving UDP packet: {}", e.getMessage());
                    }
                    continue;
                }

                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

                log.debug("Received UDP packet: {} bytes, Data(hex): {}", data.length, bytesToHex(data, 32));

                if (fos != null) {
                    fos.write((data.length >> 8) & 0xFF);
                    fos.write(data.length & 0xFF);
                    fos.write(data);
                    fos.flush();
                }

                if (settings.isForwardEnabled() && forwardAddress != null && forwardSocket != null) {
                    try {
                        DatagramPacket forwardPacket = new DatagramPacket(data, data.length, forwardAddress, settings.getForwardPort());
                        forwardSocket.send(forwardPacket);
                        log.debug("Forwarded UDP packet to {}:{}", forwardAddress, settings.getForwardPort());
                    } catch (Exception e) {
                        log.warn("Failed to forward UDP packet (destination may be unreachable): {}", e.getMessage());
                    }
                }

                if (settings.isCloudForwardEnabled() && data.length > 6 && settings.getCloudToken() != null && !settings.getCloudToken().isEmpty()) {
                    int packetId = data[6] & 0xFF;
                    boolean shouldForward = switch (packetId) {
                        case 1, 2, 3, 4, 7, 8 -> true;
                        default -> false;
                    };

                    if (shouldForward) {
                        try {
                            String urlWithToken = settings.getCloudUrl();
                            if (!urlWithToken.endsWith("/")) urlWithToken += "/";
                            urlWithToken += settings.getCloudToken();

                            restTemplate.postForObject(urlWithToken, data, Void.class);
                            log.debug("Forwarded {} bytes (Packet ID: {}) to cloud", data.length, packetId);
                        } catch (Exception e) {
                            log.error("Failed to forward telemetry to cloud: {}", e.getMessage());
                        }
                    } else {
                        log.trace("Skipping packet ID {} (not needed by cloud)", packetId);
                    }
                }
            }
        } catch (Exception e) {
            if (running.get()) {
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
        if (running.compareAndSet(true, false)) {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (forwardSocket != null && !forwardSocket.isClosed()) {
                forwardSocket.close();
            }
            if (executorService != null) {
                executorService.shutdown();
            }
            log.info("UDP Forwarder Service stopped.");
        }
    }
}
