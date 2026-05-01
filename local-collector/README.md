# F1 Telemetry Local Collector

A lightweight bridge that forwards F1 25 UDP telemetry to the Cloud Server.

## How it works
1. It listens for UDP packets from the F1 game (default port `20777`).
2. It wraps the data and sends it via HTTP POST to the Cloud Server.
3. It uses a **Telemetry Token** to identify which league the data belongs to.

## Setup
The application provides a Graphical User Interface (GUI) to manage settings. You can enter your Cloud Telemetry Token directly in the UI.

Settings are automatically saved to an `application.properties` file in the working directory when you click **Save & Apply**. This ensures your configuration is remembered the next time you launch the collector.

Alternatively, you can manually edit `src/main/resources/application.properties` before building:

```properties
# The unique token from your Cloud Server Season page
telemetry.cloud.token=your-uuid-here

# The URL of your cloud server
telemetry.cloud.url=http://localhost:8080/api/telemetry

# Local UDP port to listen on
telemetry.udp.port=20777
```

## Running
Execute:
```bash
mvn spring-boot:run
```

## Advanced Features
- **UDP Forwarding**: Can forward incoming packets to another IP (e.g., for mobile apps or SimHub).
- **Session Recording**: Can save telemetry sessions to a local binary file for later analysis.
