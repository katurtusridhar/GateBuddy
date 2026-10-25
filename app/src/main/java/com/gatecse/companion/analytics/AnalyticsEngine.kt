package com.gatecse.companion.analytics

import com.gatecse.companion.model.CustomTaskEntity
import com.gatecse.companion.model.DailyExecutionScoreUi
import com.gatecse.companion.model.DayScorePoint
import com.gatecse.companion.model.PreparationAdherenceUi
import com.gatecse.companion.model.PreparationHealth
import com.gatecse.companion.model.RecoveryAssignmentEntity
import com.gatecse.companion.model.ScheduleTaskEntity
import com.gatecse.companion.model.TaskProgressEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object AnalyticsEngine {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Calculates the daily execution score and points for a specific date.
     */
    fun calculateDailyScore(
        targetDateStr: String,
        currentDateStr: String,
        dayTasks: List<ScheduleTaskEntity>,
        progressMap: Map<String, TaskProgressEntity>,
        customTasks: List<CustomTaskEntity> = emptyList()
    ): DailyExecutionScoreUi {
        val targetDate = LocalDate.parse(targetDateStr, dateFormatter)
        val today = LocalDate.parse(currentDateStr, dateFormatter)
        val isToday = targetDate == today
        val isPast = targetDate.isBefore(today)

        var points = 0
        var completedLectures = 0
        var totalLectures = 0

        // Scheduled tasks points
        for (task in dayTasks) {
            val isComplete = progressMap[task.id]?.isCompleted == true
            if (task.isSundayActivity) {
                if (isComplete) {
                    if (task.type == "SUNDAY_TEST") points += 20
                    else if (task.type == "SUNDAY_PYQ") points += 5
                    else points += 10
                }
            } else {
                totalLectures++
                if (isComplete) {
                    completedLectures++
                    points += 10
                } else if (isPast) {
                    points -= 2 // Missed lecture penalty on past days
                }
            }
        }

        // Booster tasks (Revision and Problem Solving)
        val revKey = "BOOSTER_REV_$targetDateStr"
        val psKey = "BOOSTER_PS_$targetDateStr"
        if (progressMap[revKey]?.isCompleted == true) points += 5
        if (progressMap[psKey]?.isCompleted == true) points += 5

        // Custom tasks
        for (ct in customTasks) {
            if (ct.isCompleted) points += ct.pointsValue
        }

        val totalScheduled = dayTasks.size
        val completedCount = dayTasks.count { progressMap[it.id]?.isCompleted == true }
        val scorePercent = if (totalScheduled > 0) {
            ((completedCount.toDouble() / totalScheduled.toDouble()) * 100).toInt()
        } else {
            0
        }

        val statusDescription = when {
            isToday && completedCount == totalScheduled && totalScheduled > 0 -> "All core tasks completed! +$points pts earned 🔥"
            isToday -> "In progress: $completedCount of $totalScheduled tasks completed ($points pts)"
            isPast && completedCount == totalScheduled && totalScheduled > 0 -> "100% completed on time (+$points pts)"
            isPast && completedCount > 0 -> "Partially completed ($completedCount/$totalScheduled) — $points pts"
            isPast -> "Missed ($completedCount/$totalScheduled) — penalty applied ($points pts)"
            else -> "Upcoming scheduled day (Target: 30 pts)"
        }

        return DailyExecutionScoreUi(
            date = targetDateStr,
            scorePercentage = scorePercent,
            dailyPoints = points.coerceAtLeast(0),
            completedScheduledTasks = completedCount,
            totalScheduledTasks = totalScheduled,
            isInProgress = isToday && completedCount < totalScheduled,
            statusDescription = statusDescription
        )
    }

    /**
     * Calculates overall syllabus adherence, daily point system averages, and completion forecast.
     */
    fun calculateAdherenceAndForecast(
        currentDateStr: String,
        allTasks: List<ScheduleTaskEntity>,
        allProgress: List<TaskProgressEntity>,
        activeRecoveries: List<RecoveryAssignmentEntity>,
        allCustomTasks: List<CustomTaskEntity> = emptyList(),
        plannedEndDateStr: String = "2027-09-28"
    ): PreparationAdherenceUi {
        val today = LocalDate.parse(currentDateStr, dateFormatter)
        val plannedEnd = LocalDate.parse(plannedEndDateStr, dateFormatter)
        val progressMap = allProgress.associateBy { it.taskId }
        val customByDate = allCustomTasks.groupBy { it.date }

        val tasksUpToToday = allTasks.filter {
            val d = LocalDate.parse(it.date, dateFormatter)
            !d.isAfter(today)
        }

        var completedOnTime = 0
        var completedLate = 0
        var recoveryCompleted = 0
        var currentBacklog = 0

        for (task in tasksUpToToday) {
            val progress = progressMap[task.id]
            if (progress?.isCompleted == true) {
                when (progress.completionType) {
                    "ON_TIME" -> completedOnTime++
                    "LATE" -> completedLate++
                    "RECOVERY", "RECOVERY_LATE" -> {
                        recoveryCompleted++
                        completedLate++
                    }
                    else -> completedOnTime++
                }
            } else {
                val taskDate = LocalDate.parse(task.date, dateFormatter)
                if (taskDate.isBefore(today)) {
                    currentBacklog++
                }
            }
        }

        val totalPastEligible = tasksUpToToday.size
        val onTimeAdherencePercent = if (totalPastEligible > 0) {
            ((completedOnTime.toDouble() / totalPastEligible.toDouble()) * 100).toInt()
        } else {
            100
        }

        // 7-day and 28-day adherence
        val sevenDaysAgo = today.minusDays(7)
        val last7DaysTasks = allTasks.filter {
            val d = LocalDate.parse(it.date, dateFormatter)
            d.isAfter(sevenDaysAgo) && !d.isAfter(today)
        }
        val last7DaysOnTime = last7DaysTasks.count {
            val p = progressMap[it.id]
            p?.isCompleted == true && p.completionType == "ON_TIME"
        }
        val sevenDayAdherencePercent = if (last7DaysTasks.isNotEmpty()) {
            ((last7DaysOnTime.toDouble() / last7DaysTasks.size.toDouble()) * 100).toInt()
        } else {
            100
        }

        val twentyEightDaysAgo = today.minusDays(28)
        val last28DaysTasks = allTasks.filter {
            val d = LocalDate.parse(it.date, dateFormatter)
            d.isAfter(twentyEightDaysAgo) && !d.isAfter(today)
        }
        val last28DaysOnTime = last28DaysTasks.count {
            val p = progressMap[it.id]
            p?.isCompleted == true && p.completionType == "ON_TIME"
        }
        val twentyEightDayAdherencePercent = if (last28DaysTasks.isNotEmpty()) {
            ((last28DaysOnTime.toDouble() / last28DaysTasks.size.toDouble()) * 100).toInt()
        } else {
            100
        }

        // --- Point System Computation ---
        // Compute daily points for all days up to today
        val tasksByDate = allTasks.groupBy { it.date }
        val allDates = tasksByDate.keys.sorted().map { LocalDate.parse(it, dateFormatter) }
            .filter { !it.isAfter(today) }

        var totalPointsAllTime = 0
        var totalStudyDaysCounted = 0
        var sumDailyPointsForAvg = 0
        var pointsToday = 0

        for (date in allDates) {
            val dStr = date.format(dateFormatter)
            val dTasks = tasksByDate[dStr].orEmpty()
            val dCustom = customByDate[dStr].orEmpty()
            val isSunday = date.dayOfWeek.name == "SUNDAY"

            var dayPoints = 0
            for (t in dTasks) {
                val done = progressMap[t.id]?.isCompleted == true
                if (t.isSundayActivity) {
                    if (done) {
                        if (t.type == "SUNDAY_TEST") dayPoints += 20
                        else if (t.type == "SUNDAY_PYQ") dayPoints += 5
                        else dayPoints += 10
                    }
                } else {
                    if (done) {
                        dayPoints += 10
                    } else if (date.isBefore(today)) {
                        dayPoints -= 2 // -2 missed lecture penalty
                    }
                }
            }

            // Booster tasks
            if (progressMap["BOOSTER_REV_$dStr"]?.isCompleted == true) dayPoints += 5
            if (progressMap["BOOSTER_PS_$dStr"]?.isCompleted == true) dayPoints += 5

            // Custom tasks
            for (c in dCustom) {
                if (c.isCompleted) dayPoints += c.pointsValue
            }

            val finalDayPoints = dayPoints.coerceAtLeast(0)
            totalPointsAllTime += finalDayPoints

            if (date == today) {
                pointsToday = finalDayPoints
            }

            // Exclude empty Sundays unless backlog/test was completed
            if (!isSunday || finalDayPoints > 0) {
                sumDailyPointsForAvg += finalDayPoints
                totalStudyDaysCounted++
            }
        }

        val averageDailyPoints = if (totalStudyDaysCounted > 0) {
            sumDailyPointsForAvg.toDouble() / totalStudyDaysCounted.toDouble()
        } else {
            30.0
        }

        // Generate past 14 days points list matching website Recharts AreaChart
        val recentScores = mutableListOf<DayScorePoint>()
        for (i in 13 downTo 0) {
            val checkDate = today.minusDays(i.toLong())
            val cStr = checkDate.format(dateFormatter)
            val dTasks = tasksByDate[cStr].orEmpty()
            val dCustom = customByDate[cStr].orEmpty()
            val isSun = checkDate.dayOfWeek.name == "SUNDAY"

            var dPts = 0
            for (t in dTasks) {
                val done = progressMap[t.id]?.isCompleted == true
                if (t.isSundayActivity) {
                    if (done) {
                        if (t.type == "SUNDAY_TEST") dPts += 20
                        else if (t.type == "SUNDAY_PYQ") dPts += 5
                        else dPts += 10
                    }
                } else {
                    if (done) {
                        dPts += 10
                    } else if (checkDate.isBefore(today)) {
                        dPts -= 2
                    }
                }
            }
            if (progressMap["BOOSTER_REV_$cStr"]?.isCompleted == true) dPts += 5
            if (progressMap["BOOSTER_PS_$cStr"]?.isCompleted == true) dPts += 5
            for (c in dCustom) {
                if (c.isCompleted) dPts += c.pointsValue
            }

            val dayLabel = checkDate.dayOfWeek.name.take(3)
            recentScores.add(
                DayScorePoint(
                    date = cStr,
                    dayLabel = dayLabel,
                    points = dPts.coerceAtLeast(0),
                    targetPoints = 30,
                    isSunday = isSun
                )
            )
        }

        // Streak
        var streak = 0
        var checkDate = today
        while (true) {
            val dateStr = checkDate.format(dateFormatter)
            val dayTasks = allTasks.filter { it.date == dateStr }
            if (dayTasks.isEmpty()) {
                if (checkDate.isBefore(LocalDate.parse("2026-10-19", dateFormatter))) break
                checkDate = checkDate.minusDays(1)
                continue
            }
            val anyCompleted = dayTasks.any { progressMap[it.id]?.isCompleted == true } ||
                    progressMap["BOOSTER_REV_$dateStr"]?.isCompleted == true ||
                    progressMap["BOOSTER_PS_$dateStr"]?.isCompleted == true
            if (anyCompleted) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else {
                if (checkDate == today) {
                    checkDate = checkDate.minusDays(1)
                    val yesterdayTasks = allTasks.filter { it.date == checkDate.format(dateFormatter) }
                    val yestDateStr = checkDate.format(dateFormatter)
                    val yesterdayCompleted = yesterdayTasks.any { progressMap[it.id]?.isCompleted == true } ||
                            progressMap["BOOSTER_REV_$yestDateStr"]?.isCompleted == true ||
                            progressMap["BOOSTER_PS_$yestDateStr"]?.isCompleted == true
                    if (yesterdayCompleted) {
                        streak++
                        checkDate = checkDate.minusDays(1)
                        continue
                    }
                }
                break
            }
        }

        // Velocity & Forecast
        val totalSyllabusTasks = allTasks.size
        val totalCompletedAllTime = allProgress.count { it.isCompleted && !it.taskId.startsWith("BOOSTER_") }
        val remainingTasks = (totalSyllabusTasks - totalCompletedAllTime).coerceAtLeast(0)

        val daysUntilPlanEnd = ChronoUnit.DAYS.between(today, plannedEnd).coerceAtLeast(1)
        val requiredTasksPerDay = remainingTasks.toDouble() / daysUntilPlanEnd.toDouble()

        val fourteenDaysAgo = today.minusDays(14)
        val recentCompleted = allProgress.count {
            it.isCompleted && !it.taskId.startsWith("BOOSTER_") && (it.completedAtTimestamp ?: 0) >= fourteenDaysAgo.toEpochDay() * 86400000L
        }
        val daysObserved = ChronoUnit.DAYS.between(LocalDate.parse("2026-10-19", dateFormatter), today).coerceAtLeast(1)
        val actualVelocityPerDay = if (daysObserved <= 14) {
            (totalCompletedAllTime.toDouble() / daysObserved.toDouble()).coerceAtLeast(1.0)
        } else {
            (recentCompleted.toDouble() / 14.0).coerceAtLeast(1.0)
        }

        val estimatedDaysNeeded = (remainingTasks / actualVelocityPerDay).toLong()
        val forecastDate = today.plusDays(estimatedDaysNeeded)
        val forecastDateStr = forecastDate.format(dateFormatter)

        val confidenceLabel = when {
            daysObserved < 7 -> "Preliminary (Early Days)"
            daysObserved < 28 -> "Moderate Confidence"
            else -> "High Confidence"
        }

        // Preparation Health evaluation (driven by Point System & Backlog)
        val health = when {
            (currentBacklog <= 1 && (averageDailyPoints >= 15.0 || onTimeAdherencePercent >= 75)) ||
                    (averageDailyPoints >= 22.0 && currentBacklog <= 2) -> PreparationHealth.ON_TRACK
            averageDailyPoints >= 12.0 && currentBacklog <= 5 -> PreparationHealth.AT_RISK
            else -> PreparationHealth.BEHIND
        }

        val healthReason = when (health) {
            PreparationHealth.ON_TRACK -> "On track: Daily average of ${String.format("%.1f", averageDailyPoints)} pts/day and minimal backlog ($currentBacklog)."
            PreparationHealth.AT_RISK -> "Caution: Daily pace at ${String.format("%.1f", averageDailyPoints)} pts/day with $currentBacklog overdue tasks."
            PreparationHealth.BEHIND -> "Behind schedule: Backlog is $currentBacklog missed tasks and point average is ${String.format("%.1f", averageDailyPoints)} pts/day. Buffer recovery advised."
        }

        val explanations = buildList {
            add("Daily Points Today: $pointsToday pts (Target: 30 pts).")
            add("Average Non-Sunday Points: ${String.format("%.1f", averageDailyPoints)} pts/day.")
            add("Pacing: ${String.format("%.1f", actualVelocityPerDay)} tasks/day vs required ${String.format("%.1f", requiredTasksPerDay)} tasks/day.")
            add("Remaining planned syllabus: $remainingTasks of $totalSyllabusTasks tasks.")
            add("Backlog currently queued for recovery: ${activeRecoveries.size} tasks.")
        }

        val recommendations = mutableListOf<String>()
        if (currentBacklog > 0) {
            recommendations.add("Target the oldest overdue lecture on the next Sunday or buffer day (+10 pts each).")
        }
        if (averageDailyPoints < 25.0) {
            recommendations.add("Complete daily Active Revision and Problem Solving (+5 pts each) to boost your preparation health.")
        } else {
            recommendations.add("Outstanding consistency! Continue your daily lecture and booster streak.")
        }

        return PreparationAdherenceUi(
            measuredOnDate = currentDateStr,
            totalTasksToDate = totalPastEligible,
            completedOnTime = completedOnTime,
            completedLate = completedLate,
            currentBacklog = currentBacklog,
            recoveryCompleted = recoveryCompleted,
            onTimeAdherencePercent = onTimeAdherencePercent,
            sevenDayAdherencePercent = sevenDayAdherencePercent,
            twentyEightDayAdherencePercent = twentyEightDayAdherencePercent,
            currentStreakDays = streak,
            bestStreakDays = streak.coerceAtLeast(7),
            dailyPointsToday = pointsToday,
            averageDailyPoints = averageDailyPoints,
            totalPointsAllTime = totalPointsAllTime,
            recentDaysScores = recentScores,
            health = health,
            healthReason = healthReason,
            forecastCompletionDate = forecastDateStr,
            plannedEndDate = plannedEndDateStr,
            forecastConfidence = confidenceLabel,
            forecastExplanation = explanations.joinToString("\n"),
            recommendedActions = recommendations
        )
    }
}
