# Privacy Policy

Last Updated: June 1, 2026

This Privacy Policy describes how we collect, use, and handle your data when you use the **F1 Telemetry Cloud Server** and the **Android Telemetry Collector** (the "Services").

---

## 1. Data Collected by the Cloud Server

The F1 Telemetry Cloud Server is designed to capture, process, and display racing league telemetry in real-time. The server collects two main categories of data:

### A. User Account Information
When you register an account on the Cloud Server, we collect:
* **Username**: Used to identify your account and attribute league roles.
* **Email Address**: Used exclusively for account verification and password recovery.
* **Password**: Stored securely as a salted hash using the BCrypt hashing algorithm. We never store or see your password in plain text.

### B. Game Telemetry Data
When telemetry streams are sent to the server (via direct connection or forwarders), we process:
* **Telemetry Identifiers**: Telemetry name configured in the game (which may represent your gaming moniker or real name), race number, and driver ID.
* **Race Performance & Status**: Lap times, sector times, current positions, gaps, tyre compound selection, speed, throttle/brake inputs, fuel levels, tyre wear, and damage.
* **League/Session Details**: Track ID, event type (e.g., practice, qualifying, race), and session identifiers.

---

## 2. Data Collected by the Android Telemetry Collector

The Android Telemetry Collector is an companion mobile application that captures telemetry broadcasted by your PC or console over your local Wi-Fi network and forwards it to the Cloud Server.

### A. Local Network Capture
* **UDP Listener**: The application binds to local port **20777** to intercept UDP telemetry packets broadcasted by your console or PC on your home network.
* **Active Collection Only**: Telemetry packets are only captured, processed, and forwarded when you explicitly enable the capture service inside the active app interface. The app does not capture telemetry in the background when closed.

### B. App Permissions
To perform its core functions, the Android application requests the following permissions:
* **Internet (`android.permission.INTERNET`)**: Required to securely forward telemetry data to the remote Cloud Server API.
* **Access Wi-Fi/Network State (`android.permission.ACCESS_WIFI_STATE`, `android.permission.ACCESS_NETWORK_STATE`)**: Required to bind to local ports and verify that the device is connected to a local Wi-Fi network containing the console or PC broadcasting the telemetry.

### C. Absolute Data Minimality
* **No Device Tracking**: The app does **not** collect, store, or transmit your device's unique identifier (IMEI/Android ID), phone calls, contacts, calendar entries, photos, files, or local storage.
* **No Location Services**: The app does **not** request or use GPS or fine/coarse location permissions.
* **No General Traffic Analysis**: The app only reads UDP packets arriving on the specific F1 telemetry port (20777) and completely ignores all other local network traffic.

---

## 3. How We Use Your Data

We process and use the collected data strictly for the following purposes:
1. **Standings & Results**: To automatically compute driver standings, team standings, and event-level results inside your designated racing league and tier.
2. **Real-time Leaderboard**: To broadcast live session progress (positions, tyre wear, ERS states) via Vaadin web pages and WebSockets.
3. **League Administration**: To allow league administrators to map drivers to tiers, demote/promote drivers, and correct results if necessary.

---

## 4. Data Sharing & Disclosures

* **Public Display**: Your telemetry name, race number, country flag, and racing statistics are visible to other members of your racing league and anyone visiting the public leaderboard or results pages.
* **No Commercialization**: We **do not** sell, trade, rent, or monetize your personal information or telemetry data under any circumstances.
* **No Third-Party Analytics**: We do not include third-party tracking cookies or advertising SDKs in the Cloud Server or the Android application.

---

## 5. Data Retention & Deletion

* **Live Data**: Active in-memory session states are automatically cleared from the server's cache after 5 minutes of inactivity or when a session change is detected.
* **Historical Data**: Race results, sector times, and standing histories are kept permanently to maintain the history of the league seasons.
* **Your Rights**: If you wish to delete your account or wipe your historical driver mappings and telemetry data from the database, please contact your League Administrator or submit a request, and your data will be purged.
