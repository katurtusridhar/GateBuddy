package com.gatecse.companion.model

/**
 * Single day score point for the Analytics interactive graph.
 */
data class DayScorePoint(
    val date: String,               // YYYY-MM-DD
    val dayLabel: String,           // "Mon", "Tue", etc.
    val points: Int,                // Points earned that day
    val targetPoints: Int = 30,     // Baseline daily target
    val isSunday: Boolean = false
)

/**
 * UI representation of a scheduled or recovery task with live progress.
 */
data class TaskItemUi(
    val id: String,
    val originalDate: String,
    val displayDate: String,
    val dayOfWeek: String,
    val slot: Int,
    val subject: String,
    val subjectCode: String = "",
    val type: TaskType,
    val curNum: Int,
    val totalCount: Int,
    val title: String,
    val details: String,
    val milestone: String,
    val sourcePage: Int,
    val isBuffer: Boolean,
    val isRevision: Boolean,
    val isSundayActivity: Boolean,
    val isBooster: Boolean = false,
    val isCustom: Boolean = false,
    val status: TaskStatus,
    val isCompleted: Boolean,
    val completedAtTimestamp: Long? = null,
    val completionType: String? = null,
    val userNotes: String = "",
    val isAssignedForRecovery: Boolean = false,
    val recoveryDate: String? = null,
    val recoveryPriority: Int? = null,
    val pointsValue: Int = 10,
    val isFuture: Boolean = false,
    val isPast: Boolean = false
)

/**
 * Summary for a date in Calendar view.
 */
data class CalendarDayUi(
    val date: String,               // YYYY-MM-DD
    val dayOfWeek: String,
    val isSunday: Boolean,
    val isBuffer: Boolean,
    val isRevision: Boolean,
    val milestone: String,
    val totalTasks: Int,
    val completedTasks: Int,
    val missedTasks: Int,
    val recoveryTasks: Int,
    val dailyPoints: Int = 0,
    val isToday: Boolean,
    val isPast: Boolean,
    val isFuture: Boolean
)

/**
 * Subject card item with progress metrics.
 */
data class SubjectUi(
    val code: String,
    val name: String,
    val type: TaskType,
    val priorCompletedLectures: Int,
    val totalLectures: Int,
    val completedLectures: Int,
    val remainingLectures: Int,
    val completionPercentage: Int,
    val missedLectures: Int,
    val lateCompletedLectures: Int,
    val nextLecture: String?,
    val completionMilestone: String?,
    val isFullyCompleted: Boolean
)

/**
 * Daily Score and execution status.
 */
data class DailyExecutionScoreUi(
    val date: String,
    val scorePercentage: Int,       // 0 - 100
    val dailyPoints: Int,
    val completedScheduledTasks: Int,
    val totalScheduledTasks: Int,
    val isInProgress: Boolean,
    val statusDescription: String
)

/**
 * Preparation health and forecasting analytics model with Point System.
 */
data class PreparationAdherenceUi(
    val measuredOnDate: String,
    val totalTasksToDate: Int,
    val completedOnTime: Int,
    val completedLate: Int,
    val currentBacklog: Int,
    val recoveryCompleted: Int,
    val onTimeAdherencePercent: Int,
    val sevenDayAdherencePercent: Int,
    val twentyEightDayAdherencePercent: Int,
    val currentStreakDays: Int,
    val bestStreakDays: Int,
    val dailyPointsToday: Int,
    val averageDailyPoints: Double,
    val totalPointsAllTime: Int,
    val recentDaysScores: List<DayScorePoint>,
    val health: PreparationHealth,
    val healthReason: String,
    val forecastCompletionDate: String?,
    val plannedEndDate: String = "2027-09-28",
    val forecastConfidence: String, // "Preliminary", "Moderate", "High"
    val forecastExplanation: String,
    val recommendedActions: List<String>
)
