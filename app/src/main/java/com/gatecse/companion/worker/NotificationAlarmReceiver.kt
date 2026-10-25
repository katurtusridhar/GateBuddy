package com.gatecse.companion.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val action = intent?.action
                if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    // System restart or package update: Reschedule alarms silently without firing premature notification
                    NotificationScheduler.scheduleAllCadenceNotifications(context)
                } else {
                    // Cadence alarm trigger
                    NotificationHelper.sendCadenceNotification(context)
                    NotificationScheduler.scheduleNextExactAlarm(context)
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
