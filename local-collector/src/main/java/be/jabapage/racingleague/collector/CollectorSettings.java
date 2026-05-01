package be.jabapage.racingleague.collector;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

@Slf4j
@Data
@Component
public class CollectorSettings {

    private static final String CONFIG_FILE = "application.properties";

    @Value("${telemetry.udp.port:20777}")
    private int port;

    @Value("${telemetry.cloud.url:https://racingleague.jabapage.be/api/telemetry}")
    private String cloudUrl;

    @Value("${telemetry.cloud.token:}")
    private String cloudToken;
    
    @Value("${telemetry.cloud.forward.enabled:true}")
    private boolean cloudForwardEnabled;

    @Value("${telemetry.recording.enabled:false}")
    private boolean recordingEnabled;

    @Value("${telemetry.recording.path:data/recorded_session.bin}")
    private String recordingPath;

    @Value("${telemetry.udp.forward.enabled:false}")
    private boolean forwardEnabled;

    @Value("${telemetry.udp.forward.host:127.0.0.1}")
    private String forwardHost;

    @Value("${telemetry.udp.forward.port:20778}")
    private int forwardPort;

    @PostConstruct
    public void init() {
        load();
    }

    public void load() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                Properties props = new Properties();
                props.load(fis);
                
                this.port = Integer.parseInt(props.getProperty("telemetry.udp.port", String.valueOf(this.port)));
                this.cloudUrl = props.getProperty("telemetry.cloud.url", this.cloudUrl);
                this.cloudToken = props.getProperty("telemetry.cloud.token", this.cloudToken);
                this.cloudForwardEnabled = Boolean.parseBoolean(props.getProperty("telemetry.cloud.forward.enabled", String.valueOf(this.cloudForwardEnabled)));
                this.recordingEnabled = Boolean.parseBoolean(props.getProperty("telemetry.recording.enabled", String.valueOf(this.recordingEnabled)));
                this.recordingPath = props.getProperty("telemetry.recording.path", this.recordingPath);
                this.forwardEnabled = Boolean.parseBoolean(props.getProperty("telemetry.udp.forward.enabled", String.valueOf(this.forwardEnabled)));
                this.forwardHost = props.getProperty("telemetry.udp.forward.host", this.forwardHost);
                this.forwardPort = Integer.parseInt(props.getProperty("telemetry.udp.forward.port", String.valueOf(this.forwardPort)));
                
                log.info("Loaded settings from {}", CONFIG_FILE);
            } catch (Exception e) {
                log.error("Failed to load application.properties", e);
            }
        }
    }

    public void save() {
        Properties props = new Properties();
        // Load existing to not overwrite other properties (like server.port)
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            } catch (Exception e) {
                log.warn("Could not load existing properties before saving", e);
            }
        }

        props.setProperty("telemetry.udp.port", String.valueOf(this.port));
        props.setProperty("telemetry.cloud.url", this.cloudUrl);
        props.setProperty("telemetry.cloud.token", this.cloudToken != null ? this.cloudToken : "");
        props.setProperty("telemetry.cloud.forward.enabled", String.valueOf(this.cloudForwardEnabled));
        props.setProperty("telemetry.recording.enabled", String.valueOf(this.recordingEnabled));
        props.setProperty("telemetry.recording.path", this.recordingPath != null ? this.recordingPath : "");
        props.setProperty("telemetry.udp.forward.enabled", String.valueOf(this.forwardEnabled));
        props.setProperty("telemetry.udp.forward.host", this.forwardHost != null ? this.forwardHost : "");
        props.setProperty("telemetry.udp.forward.port", String.valueOf(this.forwardPort));

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Racing League Tools Local Collector Settings");
            log.info("Saved settings to {}", CONFIG_FILE);
        } catch (Exception e) {
            log.error("Failed to save application.properties", e);
        }
    }
}
