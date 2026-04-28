# F1 Telemetry Cloud Server

The central hub for managing racing leagues and visualizing live race data.

## Features
- **User Authentication**: Secure login and registration.
- **League Management**: Create multiple seasons and leagues.
- **Live Leaderboards**: Real-time race tracking with sector times, tyre compounds, and penalties.
- **Historical Data**: View past race results and standings.
- **Multi-Tenant**: Each user has an isolated environment keyed by UUID tokens.

## Configuration
Configuration is handled in `src/main/resources/application.properties`.

- `server.port`: Defaults to `8080`.
- `spring.datasource.url`: Defaults to H2 in-memory database.

## API Endpoints
- `POST /api/telemetry/{token}`: Public endpoint for receiving telemetry data from the local collector.

## Security
- Public: `/login`, `/register`, `/leaderboard/{id}`, `/api/telemetry/**`.
- Secured: All management views (`/`, `/details/**`, etc.) require authentication.
