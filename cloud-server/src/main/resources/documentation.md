# Racing League Documentation

## Table of Contents
- [1. Collector Setup](#1-collector-setup)
    - [1a. Local Collector Setup](#1a-local-collector-setup)
    - [1b. Android Collector Setup](#1b-android-collector-setup)
- [2. Tiers & Multi-Tier Leagues](#2-tiers--multi-tier-leagues)
- [3. Managing Drivers & Names](#3-managing-drivers--names)
- [4. Public Pages](#4-public-pages)
- [5. Race Statistics & Analytics](#5-race-statistics--analytics)
    - [Pure Race Pace](#pure-race-pace)
    - [Longest Stints](#longest-stints)
    - [Consistency Rating](#consistency-rating)
- [6. Season Settings](#6-season-settings)
- [7. Points configuration overrides](#7-points-configuration-overrides)

## 1. Collector Setup
### 1a. Local Collector Setup
The Local Collector is a desktop application that forwards data from your F1 game to this cloud server. It provides a graphical interface for easy configuration.
You need Java 21 Runtime environment to run the application.

### Collector Application
1. Launch the Local Collector application.
2. Select your **Tier** on the Tier page to find your unique **Tier Telemetry Token**.
3. In the 'Collector Settings' section, paste this token.
4. Ensure 'Enable Cloud Sync' is checked.
5. Click 'Save & Apply' to start the bridge.

### F1 25 Game Settings
To allow the collector to receive data, configure your game as follows:
* Go to Settings > Telemetry Settings.
* Set UDP Telemetry to 'On'.
* Set UDP IP Address to '127.0.0.1' (or the address shown in the Collector UI if playing on a different device).
* Set UDP Port to '20777'.
* Set UDP Format to '2025'.

### 1b. Android Collector Setup
The Android Collector is a mobile app that acts as a bridge between your F1 game and the cloud server. This is ideal if you play on a console or prefer using a secondary mobile device.

### Collector Application
1. Install and launch the F1 Telemetry Collector app on your Android device.
2. Navigate to the **Settings** tab.
3. **Cloud Forwarding:** 
    * Enable 'Cloud Forwarding'.
    * Paste your **Tier Telemetry Token** (found on your Tiers page) into the 'Cloud UUID' field.
4. Return to the **Dashboard** tab and click **Start Collector**.

## 2. Tiers & Multi-Tier Leagues
Seasons are organized into **Tiers** (e.g., Tier 1, Tier 2, etc.), allowing you to manage multiple skill levels within the same league.

* **Unique Tokens:** Each Tier has its own Telemetry Token. Data sent using a specific token will only affect that Tier's standings and results.
* **Standings Isolation:** Driver standings are tracked independently for each Tier.
* **Team Standings:** 
    * **Per-Tier:** View how teams are performing within a specific tier.
    * **Season Overall:** Clear the Tier selection on the Season page to see aggregated team standings across all tiers.
* **Managing Tiers:** Use the **Tiers** tab to create, rename, or delete tiers.

## 3. Managing Drivers & Names
When a driver joins a session for the first time, they are automatically 'discovered' and added to the 'Drivers' tab for the active Tier.

* **Promotion & Demotion:** To move a driver between tiers, use the 'Tier' dropdown in the Drivers grid. Their historical results remain, but future points will count towards the new Tier.
* **Display Names:** Use the Edit button to set a custom name. This name will be used in the standings and live leaderboard instead of the game's telemetry name.
* **Reserves:** Mark a driver as 'Reserve' to group them separately in the standings. They will still receive points, but their team will be displayed as 'Reserve Driver'.

## 4. Public Pages
You can share the following pages with your league members. They do not require a login to view:

* **Season Standings:** Shows current driver and team standings. You can toggle between specific Tiers or the Season Overall team view.
* **Event Results:** Detailed results for each race weekend. Select a Tier to see the specific sessions for that group.
* **Live Leaderboard:** A dedicated live dashboard for spectators, unique to each Tier.

## 5. Race Statistics & Analytics
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

## 6. Season Settings
League administrators can customize settings via the **Settings** tab. These apply to all Tiers within the season.

* **Hide AI Drivers:** If enabled, AI drivers will be filtered out from the standings and the live leaderboard.
* **Show Tyre Wear:** Displays the current maximum tyre wear percentage for each driver on the live leaderboard.
* **Show ERS:** Displays the current ERS battery percentage for each driver on the live leaderboard. When a driver is actively using ERS (Overtake mode), the value is highlighted in bold yellow.

## 7. Points configuration overrides
League administrators can customize the points awarded for any session type via the **Points** tab in the Season Details view.

* **Standard System:** By default, the system uses the standard F1 point system (25, 18, 15, 12, 10, 8, 6, 4, 2, 1) only for **Race** sessions.
* **Custom Overrides:** You can define custom points for any finishing position in any session type.
    * **Example (Pole Position):** Add an override for 'Qualifying 3' or 'Short Qualifying', Position 1, with 1 point.
* **Standings Integration:** Any points awarded via custom overrides are automatically added to the driver and team standings.
