# F1 Telemetry Cloud Server

The central hub for managing racing leagues and visualizing live race data.

## Features
- **User Authentication**: Secure login and registration.
- **League Management**: Create multiple seasons and leagues.
- **Live Leaderboards**: Real-time race tracking with sector times, tyre compounds, and penalties.
- **Historical Data**: View past race results and standings.
- **Multi-Tenant**: Each user has an isolated environment keyed by UUID tokens.

## Configuration
Configuration is handled via Spring profiles.

- **Local Development**: Uses a local PostgreSQL database.
- **Production**: Uses the Supabase PostgreSQL database.

### Running Locally
1. Start the local database:
   ```bash
   cd cloud-server
   docker-compose up -d
   ```
2. Run the application with the `local` profile:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

### Running in Production
Run the application with the `prod` profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

## API Endpoints
- `POST /api/telemetry/{token}`: Public endpoint for receiving telemetry data from the local collector.

## Security
- Public: `/login`, `/register`, `/leaderboard/{id}`, `/api/telemetry/**`.
- Secured: All management views (`/`, `/details/**`, etc.) require authentication.
