package com.gatecse.companion.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            NotificationHelper.sendCadenceNotification(context)
            NotificationScheduler.scheduleNextExactAlarm(context)
        }
    }
}
