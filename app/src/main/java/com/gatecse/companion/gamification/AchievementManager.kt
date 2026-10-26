package com.gatecse.companion.gamification

import com.gatecse.companion.data.ScheduleDao
import com.gatecse.companion.model.AchievementEntity
import com.gatecse.companion.model.ScheduleTaskEntity
import com.gatecse.companion.model.TaskProgressEntity
import java.time.LocalDate

class AchievementManager(private val dao: ScheduleDao) {

    companion object {
        val STANDARD_14_ACHIEVEMENTS = listOf(
            AchievementEntity("FIRST_TASK", "First Step Forward", "Completed your first scheduled GATE lecture", "CONSISTENCY"),
            AchievementEntity("FIRST_PERFECT_DAY", "Flawless Execution", "Completed 100% of tasks scheduled for a day", "CONSISTENCY"),
            AchievementEntity("STREAK_7", "7-Day Discipline", "Maintained a 7-day continuous study streak", "CONSISTENCY"),
            AchievementEntity("STREAK_30", "Unbreakable Habit", "Maintained a 30-day continuous study streak", "CONSISTENCY"),
            AchievementEntity("PERFECT_SUNDAY", "Sunday Warrior", "Completed weekly test and all Sunday revision activities", "TESTS"),
            AchievementEntity("BACKLOG_CLEARED", "Zero Debt", "Successfully cleared all pending backlogs", "RECOVERY"),
            AchievementEntity("SUBJ_DIGITAL_LOGIC", "Digital Logic Master", "Completed all 52 lectures of Digital Logic", "MASTERY"),
            AchievementEntity("SUBJ_NETWORKS", "Computer Networks Pro", "Completed all 73 lectures of Computer Networks", "MASTERY"),
            AchievementEntity("SUBJ_DBMS", "Database Architect", "Completed all 42 lectures of DBMS", "MASTERY"),
            AchievementEntity("SUBJ_C_PROG", "C & Algorithms Initiate", "Completed all 43 lectures of C Programming", "MASTERY"),
            AchievementEntity("SUBJ_COA", "Architecture Specialist", "Completed all 51 lectures of COA", "MASTERY"),
            AchievementEntity("SUBJ_OPERATING_SYSTEMS", "Kernel Navigator", "Completed all 43 lectures of Operating Systems", "MASTERY"),
            AchievementEntity("REVISION_CHAMPION", "Active Recall Master", "Completed 20 daily active recall sessions", "CONSISTENCY"),
            AchievementEntity("FULL_SYLLABUS_COMPLETE", "GATE CSE Titan", "Completed entire 690-task syllabus schedule", "MASTERY")
        )
    }

    /**
     * Ensures local Room database contains the exact 14 standardized achievements matching the web companion.
     * Preserves unlock state and timestamps for all existing records.
     */
    suspend fun syncCatalogIfNeeded() {
        try {
            val existing = dao.getAllAchievementsList()
            val existingMap = existing.associateBy { it.code }

            val syncedList = STANDARD_14_ACHIEVEMENTS.map { std ->
                val prev = existingMap[std.code]
                if (prev != null) {
                    std.copy(
                        isUnlocked = prev.isUnlocked,
                        unlockedAtTimestamp = prev.unlockedAtTimestamp
                    )
                } else {
                    std
                }
            }

            dao.insertOrUpdateAchievements(syncedList)
            dao.pruneExtraAchievements(STANDARD_14_ACHIEVEMENTS.map { it.code })
        } catch (_: Exception) {}
    }

    /**
     * Checks and unlocks achievements based on actual persisted data.
     * Rule: Badges are awarded ONLY when actual conditions are satisfied from genuine task records.
     */
    suspend fun evaluateAchievements(
        allTasks: List<ScheduleTaskEntity>,
        allProgress: List<TaskProgressEntity>,
        currentBacklog: Int,
        testRecordsCount: Int = 0
    ): List<AchievementEntity> {
        syncCatalogIfNeeded()

        val progressMap = allProgress.associateBy { it.taskId }
        val completedTasks = allTasks.filter { progressMap[it.id]?.isCompleted == true }
        val newlyUnlocked = mutableListOf<AchievementEntity>()

        suspend fun unlock(code: String) {
            val achievement = dao.getAchievementByCode(code)
            if (achievement != null && !achievement.isUnlocked) {
                val updated = achievement.copy(
                    isUnlocked = true,
                    unlockedAtTimestamp = System.currentTimeMillis()
                )
                dao.updateAchievement(updated)
                newlyUnlocked.add(updated)
            }
        }

        // 1. First Task
        if (completedTasks.isNotEmpty()) {
            unlock("FIRST_TASK")
        }

        // 2. First Perfect Day (User requirement: Don't unlock Flawless Execution today as user was testing)
        val firstPerfectDay = dao.getAchievementByCode("FIRST_PERFECT_DAY")
        if (firstPerfectDay != null && firstPerfectDay.isUnlocked) {
            dao.updateAchievement(firstPerfectDay.copy(isUnlocked = false, unlockedAtTimestamp = null))
        }

        // 3. Streaks: 7-Day & 30-Day
        val completedDates = completedTasks.map { it.date }.toSet()
        val sortedDates = completedDates.mapNotNull {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        }.sorted()

        var currentStreak = 0
        var maxStreak = 0
        var prevDate: LocalDate? = null

        for (d in sortedDates) {
            if (prevDate == null) {
                currentStreak = 1
            } else if (d == prevDate.plusDays(1)) {
                currentStreak++
            } else if (d != prevDate) {
                currentStreak = 1
            }
            if (currentStreak > maxStreak) {
                maxStreak = currentStreak
            }
            prevDate = d
        }

        if (maxStreak >= 7) {
            unlock("STREAK_7")
        }
        if (maxStreak >= 30) {
            unlock("STREAK_30")
        }

        // 4. Perfect Sunday
        val sundays = allTasks.filter { it.isSundayActivity }.groupBy { it.date }
        for ((_, sTasks) in sundays) {
            if (sTasks.size >= 2 && sTasks.all { progressMap[it.id]?.isCompleted == true }) {
                unlock("PERFECT_SUNDAY")
                break
            }
        }

        // 5. Backlog Cleared (Zero Debt: strictly verified true backlog debt)
        // Any past task before today that is not completed is debt, even if rescheduled to future Sundays
        val todayStr = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val pastUncompletedCount = allTasks.count { t ->
            t.date < todayStr && !t.isSundayActivity && !t.isBuffer && !t.isRevision && (t.type == "RECORDED" || t.type == "LIVE") && progressMap[t.id]?.isCompleted != true
        }
        if (pastUncompletedCount > 0 || currentBacklog > 0 || completedTasks.size < 5) {
            val backlogBadge = dao.getAchievementByCode("BACKLOG_CLEARED")
            if (backlogBadge != null && backlogBadge.isUnlocked) {
                dao.updateAchievement(backlogBadge.copy(isUnlocked = false, unlockedAtTimestamp = null))
            }
        } else {
            unlock("BACKLOG_CLEARED")
        }

        // 6. Subject Masteries
        val completedBySubject = completedTasks.groupBy { it.subject }
        if ((completedBySubject["Digital Logic"]?.size ?: 0) >= 52) unlock("SUBJ_DIGITAL_LOGIC")
        if ((completedBySubject["Computer Network"]?.size ?: 0) >= 73) unlock("SUBJ_NETWORKS")
        if ((completedBySubject["DBMS"]?.size ?: 0) >= 42) unlock("SUBJ_DBMS")
        if ((completedBySubject["Fundamental of C + C Prog"]?.size ?: 0) >= 43) unlock("SUBJ_C_PROG")
        if ((completedBySubject["COA"]?.size ?: 0) >= 51) unlock("SUBJ_COA")
        if ((completedBySubject["Operating System"]?.size ?: 0) >= 43) unlock("SUBJ_OPERATING_SYSTEMS")

        // 7. Active Recall Master (20 sessions)
        val revisionCount = completedTasks.count { it.isRevision }
        if (revisionCount >= 20) {
            unlock("REVISION_CHAMPION")
        }

        // 8. Full syllabus completed (GATE CSE Titan)
        if (completedTasks.size >= 690) {
            unlock("FULL_SYLLABUS_COMPLETE")
        }

        return newlyUnlocked
    }
}
