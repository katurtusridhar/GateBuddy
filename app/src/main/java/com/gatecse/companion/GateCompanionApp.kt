package com.gatecse.companion

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.gatecse.companion.data.GateCompanionDatabase
import com.gatecse.companion.data.ScheduleImporter
import com.gatecse.companion.data.ScheduleRepository
import com.gatecse.companion.worker.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GateCompanionApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { GateCompanionDatabase.getInstance(this) }
    val repository by lazy { ScheduleRepository(database, this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Schedule cadence notifications (5 AM Mission Briefing + every 4 hours until 11 PM)
        NotificationScheduler.scheduleAllCadenceNotifications(this)

        // Schedule periodic cloud synchronization (every 30 minutes when network connected)
        com.gatecse.companion.sync.SyncWorker.schedulePeriodicSync(this)

        // Asynchronously check and import schedule data on first launch
        applicationScope.launch {
            ScheduleImporter.importIfNeeded(this@GateCompanionApp, database)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "GateBuddy Preparation Reminders"
            val channelDesc = "Daily morning study slots, active revision alerts, and Sunday test reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_REMINDERS, channelName, importance).apply {
                description = channelDesc
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "channel_gate_reminders"
    }
}
