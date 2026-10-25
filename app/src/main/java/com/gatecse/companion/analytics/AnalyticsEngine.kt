package com.gatecse.companion.analytics

import com.gatecse.companion.model.AssessmentResultEntity
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
import kotlin.math.roundToInt

object AnalyticsEngine {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Calculates the daily execution score and points for a specific date.
     * Evaluates Sunday test score proportionally (max 20 pts) and applies negative penalties for missed tasks.
     */
    fun calculateDailyScore(
        targetDateStr: String,
        currentDateStr: String,
        dayTasks: List<ScheduleTaskEntity>,
        progressMap: Map<String, TaskProgressEntity>,
        customTasks: List<CustomTaskEntity> = emptyList(),
        assessments: List<AssessmentResultEntity> = emptyList()
    ): DailyExecutionScoreUi {
        val targetDate = LocalDate.parse(targetDateStr, dateFormatter)
        val today = LocalDate.parse(currentDateStr, dateFormatter)
        val isToday = targetDate == today
        val isPast = targetDate.isBefore(today)
        val isSunday = targetDate.dayOfWeek.name == "SUNDAY"

        var points = 0
        var completedLectures = 0
        var totalLectures = 0

        if (isSunday || dayTasks.any { it.isSundayActivity }) {
            // --- SUNDAY POINT SYSTEM (+30 max: Test max +20, Rev +5, PYQ +5) ---
            val scheduledTest = dayTasks.firstOrNull { it.type == "SUNDAY_TEST" || (it.isSundayActivity && it.slot == 1) }
            val testProg = progressMap["SUNDAY_TEST_$targetDateStr"]
                ?: scheduledTest?.let { progressMap[it.id] }
                ?: progressMap.values.firstOrNull { it.originalDate == targetDateStr && it.taskId.startsWith("SUNDAY_TEST") }
            val assessment = assessments.firstOrNull { it.testDate == targetDateStr }
            val isTestComplete = testProg?.isCompleted == true || assessment != null

            if (isTestComplete) {
                val testPercentage = assessment?.percentage ?: run {
                    val notes = testProg?.notes.orEmpty()
                    val match = Regex("""Marks:\s*(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)""").find(notes)
                    if (match != null) {
                        val scored = match.groupValues[1].toDoubleOrNull() ?: 0.0
                        val maxM = match.groupValues[2].toDoubleOrNull() ?: 50.0
                        if (maxM > 0) (scored / maxM) * 100.0 else 100.0
                    } else {
                        100.0
                    }
                }
                points += ((testPercentage / 100.0) * 20.0).roundToInt().coerceIn(0, 20)
            } else if (isPast && (scheduledTest != null || isSunday)) {
                points -= 4 // -4 pts penalty for missed Sunday test
            }

            // Sunday Revision (+5 pts)
            val revProg = progressMap["BOOSTER_REV_$targetDateStr"]
                ?: dayTasks.firstOrNull { it.isRevision }?.let { progressMap[it.id] }
            val isRevComplete = revProg?.isCompleted == true
            if (isRevComplete) {
                points += 5
            } else if (isPast && dayTasks.any { it.isRevision }) {
                points -= 1 // -1 pt penalty for missed scheduled revision
            }

            // Sunday PYQ Practice (+5 pts)
            val scheduledPyq = dayTasks.firstOrNull { it.type == "SUNDAY_PYQ" || (it.isSundayActivity && it.slot == 2) }
            val pyqProg = progressMap["BOOSTER_PS_$targetDateStr"]
                ?: scheduledPyq?.let { progressMap[it.id] }
            val isPyqComplete = pyqProg?.isCompleted == true
            if (isPyqComplete) {
                points += 5
            } else if (isPast && (scheduledPyq != null || (isSunday && dayTasks.size >= 2))) {
                points -= 1 // -1 pt penalty for missed Sunday PYQ
            }

            // Non-Sunday catch-up or recovery tasks scheduled for Sunday
            val nonSundayTasks = dayTasks.filter { !it.isSundayActivity && it.type != "SUNDAY_TEST" && it.type != "SUNDAY_PYQ" }
            for (task in nonSundayTasks) {
                totalLectures++
                val isComplete = progressMap[task.id]?.isCompleted == true
                if (isComplete) {
                    completedLectures++
                    points += 10
                } else if (isPast) {
                    points -= 2
                }
            }
        } else {
            // --- WEEKDAY POINT SYSTEM (Lectures +10 each, Booster Rev +5, Booster PS +5) ---
            for (task in dayTasks) {
                totalLectures++
                val isComplete = progressMap[task.id]?.isCompleted == true
                if (isComplete) {
                    completedLectures++
                    points += 10
                } else if (isPast) {
                    points -= 2 // Missed lecture penalty on past days
                }
            }

            // Weekday Booster tasks (Revision and Problem Solving)
            val revKey = "BOOSTER_REV_$targetDateStr"
            val psKey = "BOOSTER_PS_$targetDateStr"
            if (progressMap[revKey]?.isCompleted == true) points += 5
            if (progressMap[psKey]?.isCompleted == true) points += 5
        }

        // Custom tasks
        for (ct in customTasks) {
            if (ct.isCompleted) points += ct.pointsValue
        }

        // Total scheduled tasks count and completed count for execution score
        val (totalScheduled, completedCount) = if (isSunday || dayTasks.any { it.isSundayActivity }) {
            val nonSundayCount = dayTasks.count { !it.isSundayActivity && it.type != "SUNDAY_TEST" && it.type != "SUNDAY_PYQ" }
            val total = 3 + nonSundayCount + customTasks.size
            val scheduledTest = dayTasks.firstOrNull { it.type == "SUNDAY_TEST" || (it.isSundayActivity && it.slot == 1) }
            val testDone = progressMap["SUNDAY_TEST_$targetDateStr"]?.isCompleted == true 
                || scheduledTest?.let { progressMap[it.id]?.isCompleted == true } == true 
                || assessments.any { it.testDate == targetDateStr }
            val revDone = progressMap["BOOSTER_REV_$targetDateStr"]?.isCompleted == true
            val scheduledPyq = dayTasks.firstOrNull { it.type == "SUNDAY_PYQ" || (it.isSundayActivity && it.slot == 2) }
            val pyqDone = progressMap["BOOSTER_PS_$targetDateStr"]?.isCompleted == true 
                || scheduledPyq?.let { progressMap[it.id]?.isCompleted == true } == true
            val nonSundayDone = dayTasks.count { !it.isSundayActivity && it.type != "SUNDAY_TEST" && it.type != "SUNDAY_PYQ" && progressMap[it.id]?.isCompleted == true }
            val customDone = customTasks.count { it.isCompleted }
            val completed = (if (testDone) 1 else 0) + (if (revDone) 1 else 0) + (if (pyqDone) 1 else 0) + nonSundayDone + customDone
            Pair(total, completed)
        } else {
            val total = dayTasks.size + customTasks.size
            val completed = dayTasks.count { progressMap[it.id]?.isCompleted == true } + customTasks.count { it.isCompleted }
            Pair(total, completed)
        }

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
     * Evaluates 7-day rolling average points and score-based Sunday tests.
     */
    fun calculateAdherenceAndForecast(
        currentDateStr: String,
        allTasks: List<ScheduleTaskEntity>,
        allProgress: List<TaskProgressEntity>,
        activeRecoveries: List<RecoveryAssignmentEntity>,
        allCustomTasks: List<CustomTaskEntity> = emptyList(),
        allAssessments: List<AssessmentResultEntity> = emptyList(),
        plannedEndDateStr: String = "2027-09-28"
    ): PreparationAdherenceUi {
        val today = LocalDate.parse(currentDateStr, dateFormatter)
        val plannedEnd = LocalDate.parse(plannedEndDateStr, dateFormatter)
        val progressMap = allProgress.associateBy { it.taskId }
        val customByDate = allCustomTasks.groupBy { it.date }
        val assessmentsByDate = allAssessments.associateBy { it.testDate }

        val tasksUpToToday = allTasks.filter {
            val d = LocalDate.parse(it.date, dateFormatter)
            !d.isAfter(today)
        }

        var completedOnTime = 0
        var completedLate = 0
        var recoveryCompleted = 0
        var currentBacklog = 0

        for (task in tasksUpToToday) {
            val isSundayTest = task.type == "SUNDAY_TEST"
            val isSundayPyq = task.type == "SUNDAY_PYQ"
            val isDone = when {
                isSundayTest -> {
                    progressMap[task.id]?.isCompleted == true 
                        || progressMap["SUNDAY_TEST_${task.date}"]?.isCompleted == true 
                        || assessmentsByDate.containsKey(task.date)
                }
                isSundayPyq -> {
                    progressMap[task.id]?.isCompleted == true 
                        || progressMap["BOOSTER_PS_${task.date}"]?.isCompleted == true
                }
                else -> progressMap[task.id]?.isCompleted == true
            }

            if (isDone) {
                val completionType = progressMap[task.id]?.completionType 
                    ?: progressMap["SUNDAY_TEST_${task.date}"]?.completionType
                    ?: "ON_TIME"
                when (completionType) {
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
            (p?.isCompleted == true && p.completionType == "ON_TIME")
                || (it.type == "SUNDAY_TEST" && (progressMap["SUNDAY_TEST_${it.date}"]?.isCompleted == true || assessmentsByDate.containsKey(it.date)))
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
            (p?.isCompleted == true && p.completionType == "ON_TIME")
                || (it.type == "SUNDAY_TEST" && (progressMap["SUNDAY_TEST_${it.date}"]?.isCompleted == true || assessmentsByDate.containsKey(it.date)))
        }
        val twentyEightDayAdherencePercent = if (last28DaysTasks.isNotEmpty()) {
            ((last28DaysOnTime.toDouble() / last28DaysTasks.size.toDouble()) * 100).toInt()
        } else {
            100
        }

        // --- Point System Computation ---
        val tasksByDate = allTasks.groupBy { it.date }
        val allDates = tasksByDate.keys.sorted().map { LocalDate.parse(it, dateFormatter) }
            .filter { !it.isAfter(today) }

        var totalPointsAllTime = 0
        var pointsToday = 0

        for (date in allDates) {
            val dStr = date.format(dateFormatter)
            val score = calculateDailyScore(
                targetDateStr = dStr,
                currentDateStr = currentDateStr,
                dayTasks = tasksByDate[dStr].orEmpty(),
                progressMap = progressMap,
                customTasks = customByDate[dStr].orEmpty(),
                assessments = allAssessments
            )
            totalPointsAllTime += score.dailyPoints

            if (date == today) {
                pointsToday = score.dailyPoints
            }
        }

        // 7-day rolling average calculation: past 7 days ending today
        val sevenDaysList = (0..6).map { today.minusDays(it.toLong()) }
        var sum7DaysPoints = 0
        for (d in sevenDaysList) {
            val dStr = d.format(dateFormatter)
            val score = calculateDailyScore(
                targetDateStr = dStr,
                currentDateStr = currentDateStr,
                dayTasks = tasksByDate[dStr].orEmpty(),
                progressMap = progressMap,
                customTasks = customByDate[dStr].orEmpty(),
                assessments = allAssessments
            )
            sum7DaysPoints += score.dailyPoints
        }

        val averageDailyPoints = sum7DaysPoints.toDouble() / 7.0

        // Generate past 14 days points list matching website Recharts AreaChart
        val recentScores = mutableListOf<DayScorePoint>()
        for (i in 13 downTo 0) {
            val checkDate = today.minusDays(i.toLong())
            val cStr = checkDate.format(dateFormatter)
            val score = calculateDailyScore(
                targetDateStr = cStr,
                currentDateStr = currentDateStr,
                dayTasks = tasksByDate[cStr].orEmpty(),
                progressMap = progressMap,
                customTasks = customByDate[cStr].orEmpty(),
                assessments = allAssessments
            )
            val dayLabel = checkDate.dayOfWeek.name.take(3)
            val isSun = checkDate.dayOfWeek.name == "SUNDAY"
            recentScores.add(
                DayScorePoint(
                    date = cStr,
                    dayLabel = dayLabel,
                    points = score.dailyPoints.coerceAtLeast(0),
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
            PreparationHealth.ON_TRACK -> "On track: 7-day average of ${String.format("%.1f", averageDailyPoints)} pts/day and minimal backlog ($currentBacklog)."
            PreparationHealth.AT_RISK -> "Caution: 7-day average at ${String.format("%.1f", averageDailyPoints)} pts/day with $currentBacklog overdue tasks."
            PreparationHealth.BEHIND -> "Behind schedule: Backlog is $currentBacklog missed tasks and 7-day average is ${String.format("%.1f", averageDailyPoints)} pts/day. Buffer recovery advised."
        }

        val explanations = buildList {
            add("Daily Points Today: $pointsToday pts (Target: 30 pts).")
            add("7-Day Rolling Average: ${String.format("%.1f", averageDailyPoints)} pts/day.")
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
