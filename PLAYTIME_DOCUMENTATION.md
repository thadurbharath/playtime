# PlayTime - Scheduled Music Companion
**Version:** 1.0.0
**Project Name:** Sample1

## 1. App Overview
PlayTime is a smart music player designed for scheduled playback. It allows users to create playlists from local storage and schedule them to play at specific times or recurring intervals. It is highly optimized for reliability on restrictive Android skins like MIUI (Redmi/Xiaomi).

## 2. Key Features
### 2.1 Smart Scheduling
- **Recurring Alarms**: Schedule music daily, once, or on specific days of the week.
- **Specific Date Support**: Set a one-time music event for a calendar date.
- **Auto-Delete**: One-time schedules can be set to automatically delete themselves after triggering to keep the list clean.

### 2.2 Advanced Audio Engine
- **Sunrise Alarm (Fade-In)**: Volume starts at 0% and gradually ramps up over 30 seconds for a gentle wake-up experience.
- **Sleep Timer**: Automatically stops playback after a user-defined duration (0-120 mins).
- **Volume Boost**: System-level volume fine-tuning within the app.
- **High-Priority Channel**: Uses the `ALARM` audio usage category to bypass standard media silencers.

### 2.3 User Interface
- **Dynamic Home Tabs**: Users can toggle visibility of "Tracks", "Playlists", and "Albums" tabs in settings.
- **Full-Screen Alarm**: A dedicated high-visibility screen appears when a scheduled playlist triggers, even over the lock screen.
- **Live Calendar View**: See all upcoming events in a list or filter by specific dates.

### 2.4 Performance & Battery
- **Self-Terminating Service**: The background music service stops immediately when music is paused or finished.
- **Minimal Idle Footprint**: The app consumes 0% battery when not actively playing or scheduled.

### 2.5 In-App Updates
- **GitHub Integration**: Checks for updates via a remote JSON file on GitHub.
- **Direct Installation**: Downloads and installs APKs without requiring the Play Store.

## 3. Technical Architecture
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Database**: Room Persistence Library
- **Media Engine**: Media3 (ExoPlayer & MediaSession)
- **Scheduling**: `AlarmManager` with `setAlarmClock` for maximum precision.

---

# Technical Specifications & FAQ

## 1. Compatibility & Versions
- **Minimum Android Version**: Android 7.0 (API Level 24).
- **Target SDK**: Android 15 (API Level 35/36).
- **Supported Devices**: 
  - Optimized for **Phones** and **Tablets**.
  - **Foldables**: Supported via Jetpack Compose's adaptive layouts.
  - **Android TV/Wear OS**: Not supported in current version.

## 2. Media Support
- **Supported File Formats**: MP3, WAV, FLAC, AAC, OGG, M4A, and most standard Android media formats supported by Media3 (ExoPlayer).
- **Storage Sources**: Internal storage and SD cards (requires system permission).

## 3. Scheduling & Queue Logic
- **Maximum Schedules**: No hard limit (limited only by phone storage). Efficiently handles 1000+ schedules via Room database indexing.
- **Simultaneous Alarms**: If two playlists trigger at the same minute, the most recent one will **replace** the previous playback. Two songs will never play at the same time.
- **Time Zone Handling**: 
  - **Auto-Adjust**: Alarms are scheduled using system time. If the user changes timezones, the alarm will still trigger at the "local" time set (e.g., 8:00 AM becomes 8:00 AM in the new zone).
  - **DST**: Handled automatically by the Android system `Calendar` API.

## 4. Music Playback Behavior
- **Playlist Features**: Supports Shuffle, Repeat All, and Repeat One.
- **Missing Files**: If a file is deleted after scheduling, the app will log an error and attempt to play the next available track in the playlist.
- **Revoked Permissions**: If storage permission is revoked, the app will show a security toast and stop the playback service to prevent crashes.
- **Hardware Support**: 
  - Full support for **Bluetooth Headphones**, **Wired Headsets**, **External Speakers**, and **Car Audio (Android Auto compatible)**.
- **Audio Focus**: 
  - **Phone Calls**: Music automatically pauses during a call and resumes after.
  - **Navigation**: Music volume "ducks" (lowers) when navigation instructions play.

## 5. Alarm & Notification features
- **Alarm Screen Actions**: 
  - **Stop**: Ends playback and closes the service.
  - **Dismiss**: Closes the alarm screen but continues music in the background.
  - **Snooze**: Not implemented in v1.0.0 (planned for future update).
- **Notification Controls**: Persistent while playing. Includes Play, Pause, Stop, Previous, and Next track controls. Includes a progress bar.

## 6. Functional Settings
- **Display**: Dark Theme (Manual/System), Dynamic Colors (Android 12+).
- **Audio**: Fade-In (30s), Sleep Timer (0-120 min), Volume Boost (50%-200%).
- **Maintenance**: Auto-Delete (for non-recurring events), Home Tab visibility management.
- **Update**: Connects to `https://raw.githubusercontent.com/thadurbharath/playtime/master/update.json`.

## 7. Edge Cases & Recovery
- **Device Reboot**: Alarms are automatically rescheduled on boot (requires `RECEIVE_BOOT_COMPLETED`).
- **Manual Time Change**: App listens for system time changes and refreshes schedules.
- **Battery Saver**: If "No Restrictions" is not set, alarms may be delayed by up to 10 minutes depending on manufacturer.
- **Airplane Mode**: Alarms trigger normally (local files only).

## 8. Permissions Summary
The app requests the following for full functionality:
- `POST_NOTIFICATIONS`: To show the player and alarm alerts.
- `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`: To access your music library.
- `SCHEDULE_EXACT_ALARM`: For precise timing.
- `USE_FULL_SCREEN_INTENT`: To show the alarm over the lock screen.
- `WAKE_LOCK`: To prevent the CPU from sleeping during the alarm trigger.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: To ensure MIUI doesn't freeze the app.
- `RECEIVE_BOOT_COMPLETED`: To restore alarms after a restart.
- `REQUEST_INSTALL_PACKAGES`: For in-app updates.


Use this checklist to ensure the app works perfectly on devices from different manufacturers (Samsung, Google, OnePlus, etc.).

## 1. Installation & Permissions
- [ ] App installs without "User Restricted" errors.
- [ ] Notification permission is requested and granted.
- [ ] Media/Storage permission correctly loads local `.mp3` or `.wav` files.
- [ ] **Exact Alarm** permission is granted in system settings (Android 12+).

## 2. Scheduling Reliability
- [ ] **Lock Screen Trigger**: Schedule an alarm for 2 minutes away, lock the phone, and wait. Does the `AlarmActivity` appear over the lock screen?
- [ ] **Precision**: Does the music start within +/- 5 seconds of the scheduled time?
- [ ] **Recurring Logic**: Does a "Daily" alarm reschedule itself for the next day after playing?
- [ ] **Auto-Delete**: Does a "Once" alarm with auto-delete enabled disappear from the list after playing?

## 3. Audio & Service Behavior
- [ ] **Audio Channel**: Does the music play through the "Alarm" volume slider (verify by adjusting volume during playback)?
- [ ] **Fade-In**: If enabled, is the start of the song quiet and gradually getting louder?
- [ ] **Sleep Timer**: Set to 1 min. Does the music stop and the notification disappear after 60 seconds?
- [ ] **Background Survival**: Does playback continue if you navigate to other apps (YouTube, Chrome)?

## 4. Manufacturer-Specific (Critical)
- [ ] **Redmi/Xiaomi**: Verify "Auto-start" is needed for the alarm to trigger.
- [ ] **Samsung**: Verify "Battery Optimization" doesn't kill the service after 10 minutes of screen-off.
- [ ] **OnePlus**: Check if "Advanced Optimization" in battery settings delays the `AlarmManager`.

## 5. UI/UX Consistency
- [ ] **Dark Mode**: Switch system theme. Does the app follow correctly (or follow manual setting)?
- [ ] **Tab Management**: Disable the "Albums" tab in Settings. Does it disappear from the Home screen?
- [ ] **Calendar Navigation**: Click an event in the Calendar. Does it open the correct Edit screen?
