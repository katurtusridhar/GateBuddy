package com.gatecse.companion.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    // Target cadence hours: 5:00 AM, 9:00 AM, 1:00 PM (13:00), 5:00 PM (17:00), 9:00 PM (21:00), 11:00 PM (23:00)
    val CADENCE_HOURS = listOf(5, 9, 13, 17, 21, 23)

    fun scheduleAllCadenceNotifications(context: Context) {
        scheduleNextExactAlarm(context)
        schedulePeriodicWorkFallback(context)
    }

    fun scheduleNextExactAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val zone = ZoneId.of("Asia/Kolkata")
        val now = LocalDateTime.now(zone)

        val nextCadence = getNextCadenceDateTime(now)
        val triggerEpochMillis = nextCadence.atZone(zone).toInstant().toEpochMilli()

        val intent = Intent(context, NotificationAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            5001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerEpochMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Inexact fallback if exact alarm permission is restricted
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerEpochMillis, pendingIntent)
        }
    }

    private fun schedulePeriodicWorkFallback(context: Context) {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = LocalDateTime.now(zone)
        val nextCadence = getNextCadenceDateTime(now)
        val initialDelayMillis = Duration.between(now, nextCadence).toMillis().coerceAtLeast(0)

        val workRequest = PeriodicWorkRequestBuilder<CadenceReminderWorker>(4, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "gatebuddy_cadence_reminders",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun getNextCadenceDateTime(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        val currentHour = now.hour
        val currentMinute = now.minute

        for (hour in CADENCE_HOURS) {
            if (hour > currentHour || (hour == currentHour && currentMinute < 2)) {
                return LocalDateTime.of(today, LocalTime.of(hour, 0))
            }
        }

        // All cadence hours for today have passed; next is 5:00 AM tomorrow
        return LocalDateTime.of(today.plusDays(1), LocalTime.of(5, 0))
    }
}
