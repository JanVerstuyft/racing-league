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

    private static final String CONFIG_FILE = "config.properties";

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
                
                this.port = Integer.parseInt(props.getProperty("port", String.valueOf(this.port)));
                this.cloudUrl = props.getProperty("cloudUrl", this.cloudUrl);
                this.cloudToken = props.getProperty("cloudToken", this.cloudToken);
                this.cloudForwardEnabled = Boolean.parseBoolean(props.getProperty("cloudForwardEnabled", String.valueOf(this.cloudForwardEnabled)));
                this.recordingEnabled = Boolean.parseBoolean(props.getProperty("recordingEnabled", String.valueOf(this.recordingEnabled)));
                this.recordingPath = props.getProperty("recordingPath", this.recordingPath);
                this.forwardEnabled = Boolean.parseBoolean(props.getProperty("forwardEnabled", String.valueOf(this.forwardEnabled)));
                this.forwardHost = props.getProperty("forwardHost", this.forwardHost);
                this.forwardPort = Integer.parseInt(props.getProperty("forwardPort", String.valueOf(this.forwardPort)));
                
                log.info("Loaded settings from {}", CONFIG_FILE);
            } catch (Exception e) {
                log.error("Failed to load config.properties", e);
            }
        }
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("port", String.valueOf(this.port));
        props.setProperty("cloudUrl", this.cloudUrl);
        props.setProperty("cloudToken", this.cloudToken != null ? this.cloudToken : "");
        props.setProperty("cloudForwardEnabled", String.valueOf(this.cloudForwardEnabled));
        props.setProperty("recordingEnabled", String.valueOf(this.recordingEnabled));
        props.setProperty("recordingPath", this.recordingPath != null ? this.recordingPath : "");
        props.setProperty("forwardEnabled", String.valueOf(this.forwardEnabled));
        props.setProperty("forwardHost", this.forwardHost != null ? this.forwardHost : "");
        props.setProperty("forwardPort", String.valueOf(this.forwardPort));

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Racing League Tools Local Collector Settings");
            log.info("Saved settings to {}", CONFIG_FILE);
        } catch (Exception e) {
            log.error("Failed to save config.properties", e);
        }
    }
}
