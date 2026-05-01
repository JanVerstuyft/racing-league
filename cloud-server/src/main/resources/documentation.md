# Racing League Documentation

## 1. Local Collector Setup
The Local Collector is a desktop application that forwards data from your F1 game to this cloud server. It provides a graphical interface for easy configuration.
You need Java 21 Runtime environment to run the application.  You can download it from [here](https://github.com/JanVerstuyft/racing-league/releases).

### Collector Application
1. Launch the Local Collector application.
2. In the 'Collector Settings' section, paste your 'Cloud Telemetry Token' (found on your Season page).
3. Ensure 'Enable Cloud Sync' is checked.
4. Click 'Save & Apply' to start the bridge.

### F1 25 Game Settings
To allow the collector to receive data, configure your game as follows:
* Go to Settings > Telemetry Settings.
* Set UDP Telemetry to 'On'.
* Set UDP IP Address to '127.0.0.1' (or the address shown in the Collector UI if playing on a different device).
* Set UDP Port to '20777'.
* Set UDP Format to '2025'.

### Local UDP Forwarding (Optional)
If you use a mobile dashboard (like RS Dash) or SimHub, the Local Collector can forward the game's telemetry to those devices simultaneously.
* Check 'Enable Local UDP Forwarding'.
* Set 'UDP Forward Host' to the IP address of your phone or secondary PC.
* Set 'UDP Forward Port' to the port expected by your app (usually 20777).

## 2. Managing Drivers & Names
When a driver joins a session for the first time, they are automatically 'discovered' and added to the 'Drivers' tab in your Season details.

* **Display Names:** Use the Edit button to set a custom name. This name will be used in the standings and live leaderboard instead of the game's telemetry name.
* **Reserves:** Mark a driver as 'Reserve' to group them separately in the standings. They will still receive points, but their team will be displayed as 'Reserve Driver'.

## 3. Public Pages
You can share the following pages with your league members. They do not require a login to view:

* **Season Standings:** The main season page shows current driver and team standings.
* **Event Results:** Detailed results for each race weekend, including lap times and tyre stints.
* **Live Leaderboard:** A dedicated live dashboard for spectators. Use the token from the Season page: `/leaderboard/{token}`.

## 4. AI Drivers
By default, the system tracks all drivers. You can toggle 'Hide AI Drivers' on the Season page to filter out non-human participants from the public standings.
