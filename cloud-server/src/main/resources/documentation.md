# Racing League Documentation

## Table of Contents
- [1. Collector Setup](#1-collector-setup)
    - [1a. Local Collector Setup](#1a-local-collector-setup)
    - [1b. Android Collector Setup](#1b-android-collector-setup)
- [2. Managing Drivers & Names](#2-managing-drivers--names)
- [3. Public Pages](#3-public-pages)
- [4. Race Statistics & Analytics](#4-race-statistics--analytics)
    - [Pure Race Pace](#pure-race-pace)
    - [Longest Stints](#longest-stints)
    - [Consistency Rating](#consistency-rating)
- [5. Season Settings](#5-season-settings)
- [6. Points configuration overrides](#6-points-configuration-overrides)

## 1. Collector Setup
### 1a. Local Collector Setup
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

## 1b. Android Collector Setup
The Android Collector is a mobile app that acts as a bridge between your F1 game and the cloud server. This is ideal if you play on a console or prefer using a secondary mobile device.

### Collector Application
1. Install and launch the F1 Telemetry Collector app on your Android device.
2. Navigate to the **Settings** tab.
3. **UDP Listener Settings:** Ensure the 'UDP Port' matches your game settings (default is 20777).
4. **Cloud Forwarding:** 
    * Enable 'Cloud Forwarding'.
    * Paste your 'Cloud Telemetry Token' (found on your Season page) into the 'Cloud UUID' field.
5. **Local Forwarding (Optional):** If you use another dashboard app on the same device or network, you can enable local forwarding here.
6. Return to the **Dashboard** tab and click **Start Collector**.
7. Note the **IP Address** displayed on the Dashboard.

### F1 25 Game Settings
* Go to Settings > Telemetry Settings.
* Set UDP Telemetry to 'On'.
* Set **UDP IP Address** to the IP shown in the Android App Dashboard.
* Set **UDP Port** to the port configured in the app (default 20777).
* Set UDP Format to '2025'.

## 2. Managing Drivers & Names
When a driver joins a session for the first time, they are automatically 'discovered' and added to the 'Drivers' tab in your Season details.

* **Display Names:** Use the Edit button to set a custom name. This name will be used in the standings and live leaderboard instead of the game's telemetry name.
* **Reserves:** Mark a driver as 'Reserve' to group them separately in the standings. They will still receive points, but their team will be displayed as 'Reserve Driver'.

## 3. Public Pages
You can share the following pages with your league members. They do not require a login to view:

* **Season Standings:** The main season page shows current driver and team standings.
* **Event Results:** Detailed results for each race weekend, including lap times and tyre stints.
* **Live Leaderboard:** A dedicated live dashboard for spectators. Use the token from the Season page: `/leaderboard/{token}`.

## 4. Race Statistics & Analytics
Detailed analytics are available in the **Event Results** view to help compare driver performance beyond just the finishing position.

### Pure Race Pace
Calculates a driver's theoretical speed by analyzing sector times across the entire race.
* **Calculation:** Uses a weighted average of sector times. The fastest 30% of sectors are fully weighted, while the next 30% have a linearly decreasing influence.
* **Goal:** Filters out "outliers" like laps spent in traffic or following a safety car, providing a realistic view of a driver's true speed in clear air.

### Longest Stints
Tracks the endurance and pace of the longest continuous run on a single set of tyres for each driver.
* **Selection:** Only the single longest stint per driver is displayed.
* **Avg Lap Time:** Calculated by independently averaging S1, S2, and S3 times from that stint.
* **107% Rule:** To ensure the average represents actual racing speed, any sector time slower than 107% of the session-wide best for that sector is discarded.

### Consistency Rating
Measures how stable a driver is during the race. A higher rating (0-100) indicates more consistent sector-by-sector performance.
* **Methodology:** Compares the time difference between consecutive laps (2-lap delta) and across three consecutive laps (3-lap delta).
* **Weighting:**
    * **2-lap delta:** The smallest 25% of differences are fully weighted; the next 25% decrease linearly to zero.
    * **3-lap delta:** (Weight factor 0.75) The smallest 15% are fully weighted; the next 15% decrease linearly.
* **Improvement Reward:** If a driver improves their time (negative delta), a **0.5 coefficient** is applied to that difference. This rewards drivers who consistently get faster rather than just staying at the same speed.
* **Avg Diff:** The sum of the calculated average deviations for S1, S2, and S3.

## 5. Season Settings
League administrators can customize the live leaderboard behavior via the **Settings** tab in the Season Details view.

* **Hide AI Drivers:** If enabled, AI drivers will be filtered out from the standings and the live leaderboard.
* **Show Tyre Wear:** Displays the current maximum tyre wear percentage for each driver on the live leaderboard.
* **Show ERS:** Displays the current ERS battery percentage for each driver on the live leaderboard. When a driver is actively using ERS (Overtake mode), the value is highlighted in bold yellow.

## 6. Points Configuration Overrides
League administrators can customize the points awarded for any session type via the **Points** tab in the Season Details view.

* **Standard System:** By default, the system uses the standard F1 point system (25, 18, 15, 12, 10, 8, 6, 4, 2, 1) only for **Race** sessions.
* **Custom Overrides:** You can define custom points for any finishing position in any session type.
    * **Example (Pole Position):** Add an override for 'Qualifying 3' or 'Short Qualifying', Position 1, with 1 point.
    * **Example (Sprint):** Add overrides for positions 1-8 for 'Sprint' sessions if needed.
* **Standings Integration:** Any points awarded via custom overrides are automatically added to the driver and team standings.
