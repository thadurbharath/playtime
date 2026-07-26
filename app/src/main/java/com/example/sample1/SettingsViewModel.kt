package com.example.sample1

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpdateAvailable(val version: String, val url: String) : UpdateStatus()
    object NoUpdate : UpdateStatus()
    data class Downloading(val progress: Float) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
    object ReadyToInstall : UpdateStatus()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("playtime_settings", Context.MODE_PRIVATE)

    // Update state
    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    // Placeholders for your GitHub URLs
    private val UPDATE_JSON_URL = "https://raw.githubusercontent.com/thadurbharath/playtime/master/update.json"

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateStatus.value = UpdateStatus.Checking
            try {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val url = URL(UPDATE_JSON_URL)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        
                        val responseCode = conn.responseCode
                        if (responseCode == 200) {
                            val response = conn.inputStream.bufferedReader().use { it.readText() }
                            JSONObject(response)
                        } else if (responseCode == 404) {
                            throw Exception("Update file (update.json) not found on GitHub (404)")
                        } else {
                            throw Exception("Server returned code: $responseCode")
                        }
                    } catch (e: Exception) {
                        throw e
                    }
                }

                if (result != null) {
                    val latestVersionCode = result.getInt("versionCode")
                    val latestVersionName = result.getString("versionName")
                    val apkUrl = result.getString("apkUrl")
                    
                    val packageInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
                    val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()

                    if (latestVersionCode > currentVersionCode) {
                        _updateStatus.value = UpdateStatus.UpdateAvailable(latestVersionName, apkUrl)
                    } else {
                        _updateStatus.value = UpdateStatus.NoUpdate
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Update check failed", e)
                val msg = when {
                    e.message?.contains("404") == true -> "Error: update.json not found in your GitHub repo. Please create it."
                    e is java.net.UnknownHostException -> "No internet connection"
                    else -> "Failed to fetch: ${e.message}"
                }
                _updateStatus.value = UpdateStatus.Error(msg)
            }
        }
    }

    fun downloadAndInstall(apkUrl: String) {
        viewModelScope.launch {
            try {
                _updateStatus.value = UpdateStatus.Downloading(0f)
                val apkFile = withContext(Dispatchers.IO) {
                    var currentUrl = apkUrl
                    var conn: HttpURLConnection
                    var responseCode: Int
                    var redirects = 0
                    val maxRedirects = 5

                    // Handle redirects manually because GitHub redirects to objects.githubusercontent.com
                    // which HttpURLConnection doesn't always follow across domains automatically.
                    do {
                        val url = URL(currentUrl)
                        conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 15000
                        conn.readTimeout = 15000
                        conn.instanceFollowRedirects = true
                        
                        responseCode = conn.responseCode
                        if (responseCode in 301..308) {
                            val newUrl = conn.getHeaderField("Location")
                            conn.disconnect()
                            currentUrl = newUrl
                            redirects++
                        } else {
                            break
                        }
                    } while (redirects < maxRedirects)

                    if (responseCode != 200) {
                        throw Exception("Server returned code $responseCode. Check your APK URL on GitHub.")
                    }

                    val totalSize = conn.contentLength
                    val input = conn.inputStream
                    // Use internal cache directory which is always accessible to the app
                    val file = File(getApplication<Application>().cacheDir, "update.apk")
                    val output = FileOutputStream(file)
                    
                    val buffer = ByteArray(8192)
                    var downloaded = 0
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalSize > 0) {
                            _updateStatus.value = UpdateStatus.Downloading(downloaded.toFloat() / totalSize.toFloat())
                        }
                    }
                    output.flush()
                    output.close()
                    input.close()
                    file
                }
                _updateStatus.value = UpdateStatus.ReadyToInstall
                installApk(apkFile)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Download failed", e)
                val errorMsg = when {
                    e.message?.contains("404") == true -> "Error 404: APK file not found at the provided URL."
                    e is java.net.UnknownHostException -> "No internet connection."
                    else -> "Download failed: ${e.message}"
                }
                _updateStatus.value = UpdateStatus.Error(errorMsg)
            }
        }
    }

    private fun installApk(file: File) {
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    // Manage Tabs
    private val _enabledTabs = MutableStateFlow(getSavedTabs())
    val enabledTabs: StateFlow<Set<String>> = _enabledTabs.asStateFlow()

    // Dark Mode: 0 = System, 1 = Light, 2 = Dark
    private val _darkMode = MutableStateFlow(prefs.getInt("dark_mode", 0))
    val darkMode: StateFlow<Int> = _darkMode.asStateFlow()

    // Volume & Audio
    private val _volumeBoost = MutableStateFlow(prefs.getFloat("volume_boost", 1.0f))
    val volumeBoost: StateFlow<Float> = _volumeBoost.asStateFlow()
    
    private val _isMovieMode = MutableStateFlow(prefs.getBoolean("movie_mode", false))
    val isMovieMode: StateFlow<Boolean> = _isMovieMode.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(prefs.getBoolean("eq_enabled", false))
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _isFadeInEnabled = MutableStateFlow(prefs.getBoolean("fade_in", true))
    val isFadeInEnabled: StateFlow<Boolean> = _isFadeInEnabled.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(prefs.getInt("sleep_timer", 0))
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    fun toggleTab(tab: String) {
        val current = _enabledTabs.value.toMutableSet()
        if (current.contains(tab)) {
            if (current.size > 1) current.remove(tab) // Keep at least one tab
        } else {
            current.add(tab)
        }
        _enabledTabs.value = current
        prefs.edit().putStringSet("enabled_tabs", current).apply()
    }

    fun setDarkMode(mode: Int) {
        _darkMode.value = mode
        prefs.edit().putInt("dark_mode", mode).apply()
    }

    fun setVolumeBoost(boost: Float) {
        _volumeBoost.value = boost
        prefs.edit().putFloat("volume_boost", boost).apply()
    }

    fun toggleMovieMode(enabled: Boolean) {
        _isMovieMode.value = enabled
        prefs.edit().putBoolean("movie_mode", enabled).apply()
    }

    fun toggleEqualizer(enabled: Boolean) {
        _equalizerEnabled.value = enabled
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
    }

    fun toggleFadeIn(enabled: Boolean) {
        _isFadeInEnabled.value = enabled
        prefs.edit().putBoolean("fade_in", enabled).apply()
    }

    fun setSleepTimer(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        prefs.edit().putInt("sleep_timer", minutes).apply()
    }

    private fun getSavedTabs(): Set<String> {
        return prefs.getStringSet("enabled_tabs", setOf("Tracks", "Playlists", "Albums")) ?: setOf("Tracks", "Playlists", "Albums")
    }
}
