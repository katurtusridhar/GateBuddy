package com.gatecse.companion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.gatecse.companion.model.ThemeMode
import com.gatecse.companion.sync.SyncApiClient
import com.gatecse.companion.ui.MainNavigation
import com.gatecse.companion.ui.Screen
import com.gatecse.companion.ui.theme.GateCompanionTheme
import com.gatecse.companion.widget.WidgetUpdateHelper
import com.gatecse.companion.worker.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+ (API 33+)
        requestNotificationPermissionIfNeeded()

        // Ensure cadence notification alarms & workers are registered
        NotificationScheduler.scheduleAllCadenceNotifications(this)

        val app = application as GateCompanionApp
        val repository = app.repository

        // Trigger immediate bidirectional cloud sync on app start
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!SyncApiClient.hasCredentials(this@MainActivity) && !SyncApiClient.isSessionExpired(this@MainActivity)) {
                    android.util.Log.i("GateBuddySync", "Initializing default companion session...")
                    SyncApiClient.authenticateAndSaveSession(
                        this@MainActivity,
                        "katurtusridhar",
                        "Sridhar@758"
                    )
                }
                repository.syncWithCloud()
            } catch (e: Exception) {
                android.util.Log.e("GateBuddySync", "Initial sync error: ${e.message}")
            }
        }

        val initialRoute = extractDeepLinkRoute(intent)

        setContent {
            val userSettings by repository.getUserSettingsFlow().collectAsState(initial = null)
            var currentThemeMode by remember(userSettings?.themeMode) {
                mutableStateOf(
                    when (userSettings?.themeMode) {
                        "LIGHT" -> ThemeMode.LIGHT
                        "SYSTEM" -> ThemeMode.SYSTEM
                        else -> ThemeMode.DARK
                    }
                )
            }

            GateCompanionTheme(themeMode = currentThemeMode) {
                val navController = rememberNavController()
                MainNavigation(
                    repository = repository,
                    currentThemeMode = currentThemeMode,
                    onThemeModeChanged = { currentThemeMode = it },
                    navController = navController,
                    initialRoute = initialRoute
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private var foregroundSyncJob: kotlinx.coroutines.Job? = null

    override fun onResume() {
        super.onResume()
        val app = application as GateCompanionApp
        lifecycleScope.launch {
            WidgetUpdateHelper.updateAllWidgets(this@MainActivity)
            try {
                app.repository.syncWithCloud()
            } catch (_: Exception) {}
        }

        // Realtime foreground sync loop while user interacts with the app
        foregroundSyncJob?.cancel()
        foregroundSyncJob = lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(4000)
                try {
                    app.repository.syncWithCloud()
                } catch (_: Exception) {}
            }
        }
    }

    override fun onPause() {
        super.onPause()
        foregroundSyncJob?.cancel()
        foregroundSyncJob = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractDeepLinkRoute(intent: Intent?): String {
        val uri = intent?.data ?: return Screen.Today.route
        return when (uri.host) {
            "today" -> Screen.Today.route
            "calendar" -> Screen.Calendar.route
            "subjects" -> Screen.Subjects.route
            "analytics" -> Screen.Analytics.route
            "tests" -> Screen.Tests.route
            "achievements" -> Screen.Achievements.route
            "settings" -> Screen.Settings.route
            else -> {
                when (uri.path?.removePrefix("/")) {
                    "calendar" -> Screen.Calendar.route
                    "subjects" -> Screen.Subjects.route
                    "analytics" -> Screen.Analytics.route
                    "tests" -> Screen.Tests.route
                    "achievements" -> Screen.Achievements.route
                    "settings" -> Screen.Settings.route
                    else -> Screen.Today.route
                }
            }
        }
    }
}
