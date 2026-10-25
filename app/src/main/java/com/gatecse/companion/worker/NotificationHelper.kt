package com.gatecse.companion.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gatecse.companion.GateCompanionApp
import com.gatecse.companion.MainActivity
import com.gatecse.companion.R
import com.gatecse.companion.data.GateCompanionDatabase
import com.gatecse.companion.data.ScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotificationHelper {

    private val MOTIVATIONAL_QUOTES = listOf(
        "Master the syllabus topic by topic, Sridhar. Stay relentless!",
        "Every completed lecture brings you closer to AIR < 100 at IIT Bombay, Sridhar.",
        "Consistency beats intensity every single day. Let's execute, Sridhar!",
        "Your future self at IIT Bombay will thank you for today's focused hours, Sridhar.",
        "Discipline is choosing what you want most over what you want now, Sridhar.",
        "Turn your preparation into a masterclass in persistence, Sridhar.",
        "High ranks are built on regular, quiet mornings like today. Keep going, Sridhar!",
        "Solve problems today so exam day feels like second nature, Sridhar.",
        "Mistakes in practice are stepping stones to perfection in GATE, Sridhar.",
        "Focus on the process, Sridhar, and your dream rank will take care of itself.",
        "Every hour logged today separates you from the competition, Sridhar.",
        "One concept mastered at a time. Complete today's mission, Sridhar!"
    )

    fun getRandomMotivationalQuote(): String {
        return MOTIVATIONAL_QUOTES.random()
    }

    /**
     * Sends the scheduled cadence notification (5:00 AM Mission Briefing or every 4 hours motivation).
     */
    suspend fun sendCadenceNotification(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        val database = GateCompanionDatabase.getInstance(context)
        val settings = database.scheduleDao().getUserSettings()

        if (!force && settings != null && !settings.reminderEnabled) {
            return@withContext
        }

        val kolkataZone = ZoneId.of("Asia/Kolkata")
        val nowTime = LocalTime.now(kolkataZone)
        val nowDate = LocalDate.now(kolkataZone)
        val currentHour = nowTime.hour

        // Quiet hours: strictly after 11:00 PM (23:00) to before 5:00 AM (05:00)
        if (!force && (currentHour > 23 || currentHour < 5)) {
            return@withContext
        }

        val todayDateStr = nowDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val repository = ScheduleRepository(database)
        val todayTasks = repository.getTasksForDate(todayDateStr, todayDateStr)

        val isMorningBriefing = currentHour in 5..7
        val quote = getRandomMotivationalQuote()

        if (isMorningBriefing) {
            val title = "Good Morning, Sridhar! 🌅 5:00 AM Briefing"
            val taskLines = if (todayTasks.isNotEmpty()) {
                todayTasks.map { task ->
                    if (task.subjectCode.isNotEmpty()) "[${task.subjectCode}] ${task.title}" else task.title
                }
            } else {
                listOf("Rest and active recall day.")
            }

            showMotivationalTaskReminder(
                context = context,
                quote = quote,
                taskLines = taskLines,
                title = title,
                notificationId = 1005
            )
        } else {
            // 4-Hour Motivation & Progress Check (9 AM, 1 PM, 5 PM, 9 PM, 11 PM)
            val title = when (currentHour) {
                in 8..10 -> "Sridhar • 9:00 AM Focus & Momentum 🎯"
                in 12..14 -> "Sridhar • 1:00 PM Afternoon Checkpoint ⚡"
                in 16..18 -> "Sridhar • 5:00 PM Evening Push 🚀"
                in 20..22 -> "Sridhar • 9:00 PM Daily Mission Wrap-Up 🏆"
                23 -> "Sridhar • 11:00 PM Final Review & Rest 🌙"
                else -> "Sridhar • Daily Motivation Check 🎯"
            }

            val remainingTasks = todayTasks.filter { !it.isCompleted }
            val taskLines = if (remainingTasks.isNotEmpty()) {
                remainingTasks.map { task ->
                    if (task.subjectCode.isNotEmpty()) "[${task.subjectCode}] ${task.title}" else task.title
                }
            } else {
                listOf("All mission tasks completed! Consistency compounds into AIR.")
            }

            showMotivationalTaskReminder(
                context = context,
                quote = quote,
                taskLines = taskLines,
                title = title,
                notificationId = 1000 + currentHour
            )
        }
    }

    /**
     * Minimal, inspiring notification that motivates and clearly lists the day's tasks.
     */
    fun showMotivationalTaskReminder(
        context: Context,
        quote: String,
        taskLines: List<String>,
        title: String = "GateBuddy • Daily Focus",
        notificationId: Int = 1001
    ) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("app://gatecse/today")).apply {
            setClass(context, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val taskBulletText = if (taskLines.isNotEmpty()) {
            taskLines.joinToString("\n") { "• $it" }
        } else {
            "• All tasks completed! Great consistency!"
        }

        val fullMessage = "\"$quote\"\n\nTODAY'S MISSION:\n$taskBulletText"

        val largeIconBitmap = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_logo)
        } catch (e: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, GateCompanionApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_gate)
            .setContentTitle(title)
            .setContentText(quote)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(fullMessage)
                    .setSummaryText(if (taskLines.isNotEmpty()) "${taskLines.size} tasks" else "Completed")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (largeIconBitmap != null) {
            builder.setLargeIcon(largeIconBitmap)
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(notificationId, builder.build())
            }
        } catch (e: SecurityException) {
            // Permission not granted on Android 13+
        }
    }
}
