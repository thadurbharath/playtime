# PlayTime Technical Documentation Audit

**Generated Date:** March 2024  
**Project Name:** PlayTime (Sample1)  
**Status:** Alpha/Development  

---

# 1. Application Information

*   **Project name:** Sample1 (Internal) / PlayTime (External)
*   **Version:** 1.0 (Version Code: 1)
*   **Package name:** `com.example.sample1`
*   **Min SDK:** 24 (Android 7.0 Nougat)
*   **Target SDK:** 36 (Android 15+)
*   **Compile SDK:** 36 (Minor API Level 1)
*   **Supported Android versions:** Android 7.0 to Android 15.
*   **Supported devices:** Optimized for Phones and Tablets. Supports Foldables via adaptive Compose layouts.
*   **Unsupported devices:** Android TV, Wear OS (Not implemented).

# 2. Architecture

*   **Architecture Pattern:** MVVM (Model-View-ViewModel).
*   **Modules:** Single module (`:app`).
*   **Package structure:** 
    *   `com.example.sample1`: Logic (ViewModels, Services, Receivers, Database).
    *   `com.example.sample1.ui.theme`: Compose styling (Theme, Color, Type).
*   **Dependency Injection:** Manual injection (ViewModels initialized via `viewModel()` in Compose). No Dagger/Hilt.
*   **Repository pattern:** Partially implemented. ViewModels directly access DAOs or specialized schedulers.
*   **ViewModels:** 
    *   `PlaylistViewModel`: Manages Room DB data and alarm scheduling.
    *   `MediaViewModel`: Handles `ContentResolver` querying for local music.
    *   `SettingsViewModel`: Manages persistent user preferences via `SharedPreferences`.
*   **Room database structure:** `AppDatabase` (Version 6) with `Playlist` and `Song` entities.
*   **Media3 architecture:** Uses `MediaSessionService` (`MusicService`) with `ExoPlayer` and `MediaSession`.
*   **AlarmManager implementation:** Centered in `AlarmScheduler` using `setAlarmClock` for precision.
*   **Foreground Service implementation:** `MusicService` handles audio playback and persistent notifications.
*   **Boot Receiver:** `AlarmReceiver` filters for `BOOT_COMPLETED`.
*   **Broadcast Receivers:** 
    *   `AlarmReceiver`: Handles alarms, boot, and time/timezone changes.
*   **WorkManager usage:** **Not Implemented.**

# 3. Permissions

| Permission | Requirement | Feature | Type | Limitations |
| :--- | :--- | :--- | :--- | :--- |
| `READ_EXTERNAL_STORAGE` | Access local audio files | Track/Album loading | Runtime | Max SDK 32 |
| `READ_MEDIA_AUDIO` | Access local audio files | Track/Album loading | Runtime | Android 13+ |
| `POST_NOTIFICATIONS` | Show playback controls | Music Player | Runtime | Android 13+ |
| `SCHEDULE_EXACT_ALARM` | Precise alarm triggering | Scheduling | Runtime | Android 12+ |
| `USE_EXACT_ALARM` | Standard alarm permission | Scheduling | Manifest | - |
| `FOREGROUND_SERVICE` | Background playback | Music Engine | Manifest | - |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media playback policy | Music Engine | Manifest | Android 14+ |
| `WAKE_LOCK` | CPU activity during alarm | Alarm Trigger | Manifest | - |
| `USE_FULL_SCREEN_INTENT` | Show UI on lock screen | Alarm Activity | Manifest | - |
| `RECEIVE_BOOT_COMPLETED` | Restore alarms on reboot | Persistence | Manifest | - |

# 4. Alarm System

*   **APIs:** `AlarmManager`.
*   **Method:** Exclusively uses `setAlarmClock()` in `AlarmScheduler`. This is the highest-priority Android alarm, bypassing most battery restrictions.
*   **PendingIntent flags:** `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`.
*   **Alarm replacement:** Alarms use `playlist.id` as the RequestCode. Scheduling an alarm with an existing ID replaces the previous one.
*   **Simultaneous alarms:** Handled by the system. If two trigger at once, both `AlarmReceiver` calls occur. `MusicService` logic dictates that the second start intent will replace the current playlist in `ExoPlayer`.
*   **Missed alarms:** Inferred behavior from `setAlarmClock`—triggers immediately upon wake if the window was missed.
*   **Rescheduling:** Recurring alarms (Daily/Days) are rescheduled by `AlarmReceiver` immediately after the current one is processed.
*   **Boot handling:** `AlarmReceiver` listens for `BOOT_COMPLETED` and queries all enabled playlists from Room to re-schedule them.
*   **Time change handling:** `AlarmReceiver` listens for `TIME_SET` and `TIMEZONE_CHANGED` to refresh all schedules.
*   **DST handling:** Relies on Java `Calendar` API which handles offsets automatically.

# 5. Music Engine

*   **Implementation:** `Media3` (1.2.1).
*   **ExoPlayer:** Configured with `USAGE_MEDIA` and `AUDIO_CONTENT_TYPE_MUSIC`.
*   **MediaSession:** Integrated within `MusicService` to allow system-level media control.
*   **Notification:** Custom `MediaStyle` notification created via `NotificationCompat`.
*   **AudioAttributes:** Explicitly set to `USAGE_MEDIA`.
*   **Audio Focus:** **Manual handling.** Automatic focus is disabled in `ExoPlayer.Builder` to prevent startup crashes with non-media usage profiles.
*   **Bluetooth/Wired Headsets:** Handled by system routing (standard `USAGE_MEDIA` behavior).
*   **Android Auto:** Basic compatibility via `MediaSession`.
*   **Queue management:** `MusicService` clears and populates the `ExoPlayer` media list on every "Start" intent.
*   **Playlist implementation:** `Song` objects contain URIs; `MusicService` maps these to `MediaItem` instances.

# 6. Playback Edge Cases

*   **Missing files:** `MusicService` logs an error in a `try-catch` block during `addMediaItem`. Playback of remaining items continues.
*   **Permission revoked:** `MainActivity` checks permissions on every launch. `MusicService` has a `SecurityException` catch for `startIndex` requests.
*   **SD Card/USB:** System URIs will fail to load; `MusicService` will catch the exception and stop if no items are valid.
*   **Incoming calls:** **Not Implemented.** (Requires `PhoneStateListener` or explicit `AudioFocus` logic currently missing from the service).
*   **Navigation voice:** Music "ducks" automatically if system handles it, but explicit code-level ducking is **Not Implemented**.
*   **Device locked:** `AlarmActivity` uses `showWhenLocked="true"` and `fullScreenIntent` to bypass lock screen.
*   **App killed:** Service is self-terminating (`onTaskRemoved` calls `stopSelf`). Alarms survive via `AlarmManager`.

# 7. Schedule Logic

*   **Once alarms:** Triggers once. If `isAutoDelete` is true, deleted from DB after trigger.
*   **Daily alarms:** Rescheduled for same time next day.
*   **Weekly/Days:** Rescheduled by finding the next matching day in a 7-day loop.
*   **Auto delete:** Functional only for "Once" mode. Protected against recurring modes in UI.
*   **Maximum schedules:** Limited only by Room DB / SQLite limits (effectively unlimited).
*   **Duplicate schedules:** Avoided by using `playlist.id` as the primary key.
*   **Conflict resolution:** The last "Play" intent received by `MusicService` takes priority.

# 8. Settings

| Setting | Default | Storage | Effect | Screen |
| :--- | :--- | :--- | :--- | :--- |
| Home Tabs | All Enabled | SharedPreferences | Shows/Hides Tracks, Playlists, Albums | Settings |
| Dark Mode | System (0) | SharedPreferences | Force Light/Dark/System theme | Settings |
| Volume Boost | 1.0 (100%) | SharedPreferences | Adjusts `exoPlayer.volume` (0.5x to 2.0x) | Settings |
| Movie Mode | False | SharedPreferences | UI Placeholder (Logic Not Implemented) | Settings |
| Equalizer | False | SharedPreferences | UI Placeholder (Logic Not Implemented) | Settings |
| Fade-In | True | SharedPreferences | Ramps volume 0% -> 100% over 30s | Settings |
| Sleep Timer | 0 (Off) | SharedPreferences | Stops playback after X minutes | Settings |

# 9. Database

*   **Entities:** `Playlist` (Schedule metadata), `Song` (URI/Title links).
*   **DAOs:** `PlaylistDao` (CRUD for both entities, Transactional queries).
*   **Migrations:** Uses `fallbackToDestructiveMigration()` (Data lost on schema change).
*   **Indexes:** **Not Implemented.** No explicit `@Index` annotations on columns.
*   **Performance:** Queries return `Flow` for real-time UI updates.
*   **Auto Backup:** Enabled in Manifest (`android:allowBackup="true"`).

# 10. Background Execution

*   **Service Lifecycle:** Starts as Foreground Service on Alarm trigger. Stops immediately on Pause or End to save battery.
*   **Battery Handling:** `AlarmScheduler` checks `canScheduleExactAlarms` (API 31+). 
*   **OEM Restrictions:** 
    *   **Xiaomi/Redmi:** `fullScreenIntent` and `USAGE_MEDIA` used to bypass MIUI blocks. User must still manually enable "Auto-start" and "No restrictions".
*   **Doze Mode:** Bypassed using `setAlarmClock`.

# 11. Notifications

*   **Channel:** `music_service_channel`.
*   **Importance:** `IMPORTANCE_HIGH`.
*   **Full-screen intent:** Launches `AlarmActivity` on trigger.
*   **Actions:** Play, Pause, Stop, Previous, Next (depending on state).
*   **Persistence:** `setOngoing(true)` while music is playing.

# 12. Performance

*   **Memory:** `MediaController` is managed with `DisposableEffect` to prevent leaks.
*   **Startup:** Fast; `MainActivity` uses `LaunchedEffect` for lazy media loading.
*   **CPU:** `MusicService` stops when not playing, dropping CPU usage to 0%.

# 13. Security

*   **File Access:** Uses `Intent.FLAG_GRANT_READ_URI_PERMISSION` and `contentResolver.takePersistableUriPermission`.
*   **Exported components:** `MainActivity` and `MusicService` are exported (required for Launcher and MediaSession). `AlarmReceiver` is **not exported** for security.
*   **PendingIntent:** All use `FLAG_IMMUTABLE`.

# 14. Error Handling

*   **Missing music:** Caught in `MusicService` loop.
*   **Database:** `fallbackToDestructiveMigration` handles schema errors by wiping.
*   **Alarm failure:** `SecurityException` handled in `AlarmScheduler` if permission is missing.

# 15. Feature Matrix

| Feature | Implemented | Class/File | Notes |
| :--- | :--- | :--- | :--- |
| Schedule Music | Yes | `AlarmScheduler` | Uses `AlarmManager` |
| Volume Fade-in | Yes | `MusicService` | 30s ramp |
| Sleep Timer | Yes | `MusicService` | Up to 120m |
| Tab Management| Yes | `MainActivity` | Dynamic Home UI |
| Auto-Delete | Yes | `AlarmReceiver`| Only for "Once" mode |
| Lock Screen UI | Yes | `AlarmActivity` | |
| Search | Yes | `MediaViewModel`| Basic text filter |
| Equalizer | No | `SettingsScreen` | Toggle only, no logic |
| Movie Mode | No | `SettingsScreen` | Toggle only, no logic |

# 16. Manifest Audit

*   **Receivers:** `AlarmReceiver` correctly includes `BOOT_COMPLETED` and Time filters.
*   **Services:** `MusicService` correctly declares `foregroundServiceType="mediaPlayback"`.
*   **Activities:** `AlarmActivity` has necessary flags for lock screen interaction.

# 17. Release Readiness

*   **Missing:** "Snooze" functionality, Localization (Hardcoded strings), Repository Layer, Database Indexing.
*   **TODOs:** None in source code, but documentation notes "planned features".
*   **Compatibility:** Android 14/15 specific foreground service rules are implemented.

# 18. QA Recommendations

*   **Functional:** Test alarm trigger while app is force-closed.
*   **Edge Case:** Schedule alarm, delete the chosen song file, wait for trigger.
*   **Stress:** Schedule 50 alarms within 10 minutes of each other.
*   **Integration:** Connect Bluetooth during "Sunrise" fade-in.

# 19. Known Limitations

1.  **Redmi Devices:** Still require manual user intervention for "Auto-start" settings.
2.  **Audio Focus:** App does not currently pause when receiving a phone call.
3.  **Data Loss:** App wipes database if version is updated without a migration plan.

# 20. Final Summary

PlayTime is a functionally complete scheduled music player optimized for background reliability. Its primary strengths are its precision scheduling and efficient battery management. Before Play Store release, **Audio Focus handling** and **Database Migrations** should be prioritized to ensure stability and user satisfaction.
