package com.gatecse.companion.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gatecse.companion.data.GateCompanionDatabase
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DailyReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = GateCompanionDatabase.getInstance(applicationContext)
        val settings = database.scheduleDao().getUserSettings()

        // Enable reminders by default unless explicitly turned off
        if (settings != null && !settings.reminderEnabled) {
            return Result.success()
        }

        val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
        val todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val tasks = database.scheduleDao().getTasksForDate(todayStr)
        val allProgress = database.scheduleDao().getAllProgress().associateBy { it.taskId }
        val isSunday = today.dayOfWeek.name == "SUNDAY"

        val remainingTasks = tasks.filter { allProgress[it.id]?.isCompleted != true }

        val taskLines = if (isSunday) {
            listOf(
                "Weekly Active Recall & Mock Test",
                "Sunday Formula & Concept Revision",
                "Sunday PYQ Practice & Mistake Notebook"
            )
        } else if (remainingTasks.isNotEmpty()) {
            remainingTasks.map { "${it.subject} — Lec ${it.curNum}" } + listOf(
                "Daily Active Revision (30 min)",
                "GATE Problem Solving (15 Qs)"
            )
        } else {
            emptyList()
        }

        val quote = NotificationHelper.getRandomMotivationalQuote()

        NotificationHelper.showMotivationalTaskReminder(
            context = applicationContext,
            quote = quote,
            taskLines = taskLines,
            notificationId = 1002
        )

        return Result.success()
    }
}
