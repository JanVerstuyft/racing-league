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
- [7. Incident & Penalty System](#7-incident--penalty-system)
- [8. Event Lineups](#8-event-lineups)
- [9. Provisional vs Finalized Race Weekends](#9-provisional-vs-finalized-race-weekends)
- [10. Championship Teams (A vs B)](#10-championship-teams-a-vs-b)

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
* Set UDP Format to '2025' or '2026' if you downloaded the DLC.

## 2. Managing Drivers & Names
When a driver joins a session for the first time, they are automatically 'discovered' and added to the 'Drivers' tab in your Season details.

* **Display Names:** Use the Edit button to set a custom name. This name will be used in the standings and live leaderboard instead of the game's telemetry name.
* **Manual Driver Additions:** Administrators can manually register drivers before their telemetry is received by using the **Add Manual Driver** button, with the option to pre-assign them to specific Tiers.
* **Reserves & Team Clearing:** Drivers can be marked as 'Reserve'. Checking 'Reserve' automatically clears and disables their team selector. If they are later unchecked, the selector is re-enabled for manual team assignments.
* **Strict Team Capacity & Replacements:** Within any single tier, a team can have at most **2 active (non-reserve)** drivers. If you attempt to assign a third active driver to a team within the same tier:
    - A replacement dialog displays the current 2 active drivers.
    - Selecting one of the existing drivers will immediately set them to reserve (clearing their team) and assign their team slot to the new driver.
* **Copy to Tier:** You can copy any driver mapping from one tier to another tier of the league.
    - The system validates that the driver is not already mapped in the target tier (checking telemetry name, race number, driver ID, and country).
    - You are prompted to set the destination reserve status and team, subject to the same strict 2-driver capacity limits in the target tier.

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

* **Car Type:** Determines the game and car generation for the league (**F1 25** or **F1 26**).
    - Under **F1 26**, team selections across the app are automatically filtered to show only official 2026 teams (IDs >= 400).
    - Legacy or truncated telemetry team IDs (e.g. truncated uint8 Mercedes ID 220) are matched by name to their official uint16 IDs (e.g. 476) automatically.
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

## 7. Incident & Penalty System
League stewards can issue manual penalties and points deductions for race incidents via the **Penalties** dashboard.

* **Accessing the Dashboard:** Navigate to the **Race Weekends** tab on the Season Details page, and click **Penalties** next to the specific event.
* **Steward Penalty Types:**
    * **Time Penalties (seconds):** Positive or negative seconds added directly to the driver's total race time. Drivers who finished the race are automatically re-classified (re-ranked) based on their adjusted total times.
    * **Points Deductions:** Point values subtracted from the driver's final points awarded for the session.
    * **Reason / Comment:** Stewards must specify a comment describing the incident or reason for the penalty.
* **Standings & Visibility:**
    * Incident stats are aggregated across all sessions and displayed in the **Standings > Penalties** (tier-specific) and **League Penalties** (league-wide) dashboards.
    * Existing penalties can be deleted by administrators to automatically trigger recalculation of positions and standings.

## 8. Event Lineups
Coordinators can manage the grid lineup for each race weekend via the Event Details view.

* **Lineup Settings & Styling:** League owners can define an **Accent Color** in the Season Settings. This color is dynamically applied to display ribbons on the lineup layout. The ribbons also proudly display the League Name.
* **Lineup Syncing (Update with Real Lineup):** When a session has been driven, administrators can click **Update with Real Lineup** in the Lineup view. This action clears the pre-race lineup configuration and automatically queries telemetry results for that event, mapping all recorded human participants and their team IDs to form the correct actual race lineup automatically.
* **Reordering Race Weekends:** Administrators can customize the order of race weekends in the Season Details view. Use the **Up** (arrow up) and **Down** (arrow down) buttons in the event table actions column to move weekends. The adjusted sequence is saved to the database as the permanent display order, preserving it for all users when viewing standings and race results.

## 9. Provisional vs Finalized Race Weekends
To ensure data integrity and give league stewards time to review incidents and apply penalties before standings are permanently affected, the system implements a provisional versus finalized event workflow.

* **Default Status:** When a new race weekend (event) is created or when telemetry is first received, the event is marked as **Provisional** (displayed with a red "Provisional" badge in the UI).
* **Standings Updates:** Driver and team standings are **not** updated while an event is provisional. Standings are only updated after a steward explicitly marks the race weekend as **Final** (displayed with a green "Final" badge).
* **Locking Penalties:** When a race weekend is marked as final, the event's results are locked. Stewards can no longer add new manual penalties or delete existing ones.
* **Managing Status (Stewards only):**
    * Logged-in stewards can finalize a provisional event by clicking the **Mark Final** button on the Season Details or Event Results pages. This locks penalties and recalculates/updates all standings.
    * If a finalized event needs to be adjusted, stewards can click **Reopen** to make it provisional again. This unlocks the penalty system and removes the event's results from the standings until it is finalized again.

## 10. Championship Teams (A vs B)
The Championship Teams feature introduces a team-vs-team competition (typically "Team A" vs "Team B") layered on top of the traditional Driver and Constructor Championships.

### Enabling Championship Teams
To activate this feature, go to the **Settings** tab in the Season Details view:
* Check the **Enable Championship Teams** checkbox.
* Fill in both the **Championship Team A Name** and **Championship Team B Name** fields.
* The feature is only active in the UI (standings tabs, driver mappings, weekend lineups, manual results dropdowns, etc.) when both names are configured and the feature is enabled.

### Driver Mappings & Assignments
Once enabled, a new **Championship Team** column appears in the Driver Mapping grid under the **Drivers** tab:
* For regular (full-time) drivers, assign them to either Team A or Team B.
* For reserve drivers, leave them unassigned ("None"). Reserve driver team assignments are handled dynamically on each specific race weekend.
* **Validation Rules & Constraints:**
  - **Player Limit:** A maximum of 11 regular players can be assigned to a single Championship Team (Team A or B) per tier. If an edit, manual addition, or tier copy violates this limit, a validation error is shown and the change is rejected.
  - **Split Constructors:** Only 1 constructor (team mapping) per tier can be split between both Championship Teams (i.e. having one driver in Team A and another in Team B). All other constructors must have their drivers assigned to the same team, or left unassigned.
  - **Reserve Cleanup:** Since reserve drivers' team assignments are weekend-specific, they cannot have a permanent team mapping. Saving a driver mapping with the "Reserve" checkbox enabled will automatically clear and disable their Championship Team assignment.

### Weekend Lineups & Reserves
Since reserve drivers can fill in for different teams on different race weekends, their Championship Team assignment is set on a per-event basis:
* In the **Event Lineup** manager, when adding a reserve driver, select the Championship Team they are representing for that specific event.
* If manually entering results, you can specify or override the Championship Team for that result.
* Points earned by a reserve driver will count towards:
  1. The driver's own Driver Championship.
  2. The constructor they drove for in that race.
  3. The Championship Team they were assigned to for that specific race weekend.

### Standings Aggregation
* **Tier-Level Standings:** The **Championship Teams** tab in the Standings section shows points aggregated for Team A and Team B within the selected tier.
* **League-Wide Standings:** The **League Championship Teams** tab aggregates points across all tiers in the league.

### Lineup Poster Layout
When Championship Teams are enabled, the **Lineup Poster** layout dynamically adjusts:
* Constructors assigned to **Team A** are rendered in the left column.
* Constructors assigned to **Team B** are rendered in the right column.
* **Split Constructors** (any constructor that has drivers from both Team A and Team B, such as Cadillac) are centered below the league logo/trophy.
* In a split constructor card, the driver representing **Team A** is placed on the left slot (Slot 1) and the driver representing **Team B** is placed on the right slot (Slot 2).
