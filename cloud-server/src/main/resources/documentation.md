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
You need Java 21 Runtime environment to run the application. You can download it from [here](https://github.com/JanVerstuyft/racing-league/releases).

### Collector Application
1. Launch the Local Collector application.
2. In the 'Collector Settings' section, paste your **Telemetry Token** (found under the **Tiers** tab in the Season Details page).
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
    * Paste your **Telemetry Token** (found under the **Tiers** tab in the Season Details page) into the 'Cloud UUID' field.
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
* **Manual Driver Additions:** Administrators can manually register drivers before their telemetry is received by using the **Add Manual Driver** button, with the option to pre-assign them to specific Tiers.
* **Reserves:** Mark a driver as 'Reserve' to group them separately in the standings. They will still receive points, but their team will be displayed as 'Reserve Driver'.

## 3. Public Pages
You can share the following pages with your league members. They do not require a login to view:

* **Season Standings:** The main season page shows current driver and team standings inside individual tiers.
* **League Team Standings:** Displays aggregated team standings computed over all active tiers of the season.
* **Event Results:** Detailed results for each race weekend, including lap times, tyre stints, and analytics.
* **Live Leaderboard:** A dedicated live dashboard for spectators. Use the token from the Tiers tab in the Season Details page: `/leaderboard/{token}`.

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
League administrators can customize the live leaderboard and stats calculation behavior via the **Settings** tab in the Season Details view.

* **Hide AI Drivers:** If enabled, AI drivers will be filtered out from the standings and the live leaderboard.
* **Show Tyre Wear:** Displays the current maximum tyre wear percentage for each driver on the live leaderboard.
* **Show ERS:** Displays the current ERS battery percentage for each driver on the live leaderboard. When a driver is actively using ERS (Overtake mode), the value is highlighted in bold yellow.
* **Minimum Laps Percentage:** Configures the minimum percentage of completed laps (default 60%) required for a driver's session to be factored into the race statistics and pace calculations.

## 6. Points Configuration Overrides
League administrators can customize the points awarded for any session type via the **Points** tab in the Season Details view.

* **Standard System:** By default, the system uses the standard F1 point system (25, 18, 15, 12, 10, 8, 6, 4, 2, 1) for **Race** sessions.
* **Sprint Races Point System:** **Sprint Race** sessions (session type 19) are natively supported and default to the standard F1 Sprint points layout (8, 7, 6, 5, 4, 3, 2, 1) for the top 8 positions.
* **Custom Overrides:** You can define custom points for any finishing position in any session type.
    * **Example (Pole Position):** Add an override for 'Qualifying 3' or 'Short Qualifying', Position 1, with 1 point.
* **Extra Point Rules:** You can configure dynamic rules to award extra points for a session type based on driver statistics. The system evaluates a metric value for each driver using a Spring Expression Language (SpEL) expression and applies a rule to determine who gets the points.

    #### Built-in Presets & Default Expressions
    The UI provides presets for common formulas:

    | Preset Metric | Default SpEL Expression | Description |
    |---|---|---|
    | **Most Places Gained** | `gridPosition != null && gridPosition > 0 ? gridPosition - position : null` | Number of positions gained from starting grid to finish. |
    | **Fastest Lap** | `bestLapTime != null && bestLapTime > 0 ? bestLapTime : null` | The driver's best lap time in seconds. |
    | **Cleanest Driver (Penalties Only)** | `penalties` | Total penalty time in seconds. |
    | **Cleanest Driver (Warnings Only)** | `warnings` | Total number of warnings. |
    | **Cleanest Driver (Penalties & Warnings)** | `penalties + warnings` | Sum of penalties and warnings. |
    | **Closest Gap to Car Ahead** | `#previous != null && numLaps != null && #previous.numLaps != null && numLaps == #previous.numLaps && totalTime != null && #previous.totalTime != null ? totalTime - #previous.totalTime : null` | Time gap to the driver ahead (only if both completed the same number of laps). |

    #### Custom SpEL Rules & Context
    Administrators can select **Custom Expression** to write custom evaluation rules. When evaluating a custom expression, the root object is the driver's result (`DriverResult`), meaning its properties can be referenced directly. The following properties are available:

    | Property | Type | Description |
    |---|---|---|
    | `position` | Integer | Finishing position in the session (1-indexed). |
    | `gridPosition` | Integer | Starting grid position (1-indexed). |
    | `bestLapTime` | Float | Fastest lap time in seconds. |
    | `totalTime` | Double | Total race time in seconds. |
    | `numLaps` | Integer | Total number of laps completed. |
    | `penalties` | Integer | Total penalty seconds. |
    | `warnings` | Integer | Total number of warnings. |
    | `resultStatus` | Integer | Status code (e.g., `3` = Finished, `4` = DNF, `5` = DSQ). |
    | `ai` | Boolean | Whether the driver is AI-controlled. |
    | `driverName` | String | Custom display name of the driver. |
    | `telemetryName` | String | Original telemetry name of the driver. |

    In addition, the following context variables can be accessed using the `#` symbol:
    * `#driver`: The current driver result (`DriverResult` entity).
    * `#session`: The current session result details (`SessionResult` entity), which includes properties like `sessionType` and `trackId`.
    * `#previous`: The `DriverResult` of the driver who finished directly ahead in the standings order (or `null` if the current driver is in 1st place).

    *Example custom rules:*
    * Gained more than 5 positions: `gridPosition - position > 5 ? 1 : null`
    * Had warnings but no penalties: `penalties == 0 && warnings > 0 ? 1 : null`

    #### Rule Types
    * **Highest Value:** Points are awarded to the driver(s) with the highest numeric value (e.g., Most Places Gained).
    * **Lowest Value:** Points are awarded to the driver(s) with the lowest numeric value (e.g., Fastest Lap).
    * **Threshold (Below or Equal):** Points are awarded to **all** drivers whose metric value is less than or equal to a configured threshold (e.g., Cleanest Driver with penalties + warnings `<= 0`).
    * **Threshold (Above or Equal):** Points are awarded to **all** drivers whose metric value is greater than or equal to a configured threshold.

    #### Filter Options
    * **Must finish session:** Requires the driver to have finished the session (result status is Finished/3). Drivers with DNF or DSQ status are ignored.
    * **Only for point scorers:** Only awards the extra points to drivers who finished in a base point-scoring position (based on the standard points grid or overrides for that session).
    * **Exclude AI drivers:** Exclude computer-controlled drivers from participating in extra points.

* **Standings Integration:** Any points awarded via custom overrides or extra rules are automatically added to the driver and team standings.
