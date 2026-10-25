package com.gatecse.companion.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Baseline source schedule task extracted from authoritative PDF.
 * Immutable schedule definition.
 */
@Entity(
    tableName = "schedule_tasks",
    indices = [
        Index(value = ["date"]),
        Index(value = ["subject"]),
        Index(value = ["isBuffer"]),
        Index(value = ["isSundayActivity"])
    ]
)
data class ScheduleTaskEntity(
    @PrimaryKey
    val id: String,                 // e.g. "TASK_0001"
    val date: String,               // YYYY-MM-DD
    val dayOfWeek: String,          // Mon, Tue, etc.
    val slot: Int,                  // 1 or 2
    val subject: String,            // "Digital Logic", "Computer Network", etc.
    val type: String,               // RECORDED, LIVE, BUFFER, REVISION, SUNDAY_TEST, SUNDAY_PYQ
    val curNum: Int,                // Lecture/day sequence number (e.g. 42)
    val totalCount: Int,            // Total lectures/days (e.g. 52)
    val title: String,              // "Digital Logic — Lec 42/52"
    val details: String,
    val milestone: String,          // Milestone string if applicable
    val sourcePage: Int,            // Page 1 to 16 in source PDF
    val isBuffer: Boolean,          // Buffer/catch-up slot
    val isRevision: Boolean,        // Revision slot
    val isSundayActivity: Boolean   // Sunday test/PYQ activity
)

/**
 * Explicit user progress record.
 * A task is completed ONLY if isCompleted == true.
 */
@Entity(
    tableName = "task_progress",
    indices = [
        Index(value = ["taskId"], unique = true),
        Index(value = ["originalDate"])
    ]
)
data class TaskProgressEntity(
    @PrimaryKey
    val taskId: String,
    val originalDate: String,       // YYYY-MM-DD
    val isCompleted: Boolean,
    val completedAtTimestamp: Long? = null,
    val completionType: String? = null, // "ON_TIME", "LATE", "RECOVERY", "RECOVERY_LATE"
    val notes: String = ""
)

/**
 * Intelligent recovery assignment for missed tasks.
 * Assigns an unfinished task to future buffer days or Sunday capacity.
 */
@Entity(
    tableName = "recovery_assignments",
    indices = [
        Index(value = ["taskId"], unique = true),
        Index(value = ["recoveryDate"])
    ]
)
data class RecoveryAssignmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: String,
    val originalDate: String,
    val recoveryDate: String,       // Future date assigned for recovery
    val assignedAtTimestamp: Long,
    val isCompleted: Boolean = false,
    val completedAtTimestamp: Long? = null,
    val priorityLevel: Int,         // 1: Buffer day, 2: Sunday capacity, 3: Next available
    val status: String = "ACTIVE"   // ACTIVE, COMPLETED, CANCELLED
)

/**
 * Assessment result for weekly tests, active recall, and PYQ tests.
 */
@Entity(
    tableName = "assessment_results",
    indices = [
        Index(value = ["testDate"]),
        Index(value = ["subjectName"])
    ]
)
data class AssessmentResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val testDate: String,
    val testName: String,
    val subjectName: String,
    val marksObtained: Double,
    val maxMarks: Double,
    val percentage: Double,
    val questionsAttempted: Int? = null,
    val questionsCorrect: Int? = null,
    val topic: String = "",
    val mistakesAnalysis: String = "",
    val followUpTasks: String = "",
    val createdAtTimestamp: Long = System.currentTimeMillis()
)

/**
 * Daily Practice Problems (DPP) tracking entity.
 */
@Entity(
    tableName = "dpp_records",
    indices = [
        Index(value = ["date"]),
        Index(value = ["subjectCode"])
    ]
)
data class DppRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val subjectCode: String,
    val subjectName: String,
    val topic: String = "",
    val totalQuestions: Int,
    val questionsCorrect: Int,
    val accuracyPercentage: Double,
    val notes: String = "",
    val createdAtTimestamp: Long = System.currentTimeMillis()
)

/**
 * Gamification achievements and badges.
 */
@Entity(
    tableName = "achievements",
    indices = [
        Index(value = ["code"], unique = true)
    ]
)
data class AchievementEntity(
    @PrimaryKey
    val code: String,               // e.g. "FIRST_TASK", "STREAK_7"
    val title: String,
    val description: String,
    val category: String,           // "CONSISTENCY", "MASTERY", "TESTS", "RECOVERY"
    val isUnlocked: Boolean = false,
    val unlockedAtTimestamp: Long? = null
)

/**
 * User-created custom task for extra study activities.
 */
@Entity(
    tableName = "custom_tasks",
    indices = [
        Index(value = ["date"])
    ]
)
data class CustomTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,               // YYYY-MM-DD
    val title: String,
    val subjectCode: String = "GATE",
    val subjectName: String = "General / Practice",
    val isCompleted: Boolean = false,
    val completedAtTimestamp: Long? = null,
    val notes: String = "",
    val pointsValue: Int = 5
)

/**
 * User preferences and exam countdown settings.
 */
@Entity(
    tableName = "user_settings"
)
data class UserSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val targetExamDate: String? = null, // YYYY-MM-DD
    val targetExamName: String = "GATE CSE 2028",
    val dailyRecoveryLimit: Int = 2,    // Max extra recovery tasks per day
    val themeMode: String = "DARK",     // DARK, LIGHT, SYSTEM
    val reminderEnabled: Boolean = false,
    val reminderSlot1Hour: Int = 9,
    val reminderSlot1Minute: Int = 0,
    val reminderSlot2Hour: Int = 18,
    val reminderSlot2Minute: Int = 0,
    val isOnboarded: Boolean = false
)
