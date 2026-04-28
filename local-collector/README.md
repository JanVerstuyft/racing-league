# F1 Telemetry Local Collector

A lightweight bridge that forwards F1 25 UDP telemetry to the Cloud Server.

## How it works
1. It listens for UDP packets from the F1 game (default port `20777`).
2. It wraps the data and sends it via HTTP POST to the Cloud Server.
3. It uses a **Telemetry Token** to identify which league the data belongs to.

## Setup
Edit `src/main/resources/application.properties`:

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
