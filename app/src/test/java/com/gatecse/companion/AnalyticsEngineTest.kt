package com.gatecse.companion

import com.gatecse.companion.analytics.AnalyticsEngine
import com.gatecse.companion.model.PreparationHealth
import com.gatecse.companion.model.ScheduleTaskEntity
import com.gatecse.companion.model.SubjectRegistry
import com.gatecse.companion.model.TaskProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsEngineTest {

    private fun createDummyTask(id: String, date: String, slot: Int): ScheduleTaskEntity {
        return ScheduleTaskEntity(
            id = id,
            date = date,
            dayOfWeek = "Mon",
            slot = slot,
            subject = "Digital Logic",
            type = "RECORDED",
            curNum = 1,
            totalCount = 52,
            title = "Test Task",
            details = "Details",
            milestone = "",
            sourcePage = 1,
            isBuffer = false,
            isRevision = false,
            isSundayActivity = false
        )
    }

    @Test
    fun testDailyScoreCalculationForTodayInProgress() {
        val tasks = listOf(
            createDummyTask("T1", "2026-10-20", 1),
            createDummyTask("T2", "2026-10-20", 2)
        )
        val progressMap = mapOf(
            "T1" to TaskProgressEntity(taskId = "T1", originalDate = "2026-10-20", isCompleted = true, completionType = "ON_TIME")
        )

        val scoreUi = AnalyticsEngine.calculateDailyScore(
            targetDateStr = "2026-10-20",
            currentDateStr = "2026-10-20",
            dayTasks = tasks,
            progressMap = progressMap
        )

        assertEquals(50, scoreUi.scorePercentage)
        assertEquals(1, scoreUi.completedScheduledTasks)
        assertEquals(2, scoreUi.totalScheduledTasks)
        assertTrue("Should be marked in-progress on current date", scoreUi.isInProgress)
        assertTrue(scoreUi.statusDescription.contains("In progress"))
        assertEquals(10, scoreUi.dailyPoints)
    }

    @Test
    fun testDailyScoreCalculationForPastMissedDay() {
        val tasks = listOf(
            createDummyTask("T1", "2026-10-19", 1),
            createDummyTask("T2", "2026-10-19", 2)
        )
        // No progress marked
        val progressMap = emptyMap<String, TaskProgressEntity>()

        val scoreUi = AnalyticsEngine.calculateDailyScore(
            targetDateStr = "2026-10-19",
            currentDateStr = "2026-10-20",
            dayTasks = tasks,
            progressMap = progressMap
        )

        assertEquals(0, scoreUi.scorePercentage)
        assertEquals(0, scoreUi.completedScheduledTasks)
        assertEquals(2, scoreUi.totalScheduledTasks)
        assertFalse("Past missed day is not in-progress", scoreUi.isInProgress)
        assertTrue(scoreUi.statusDescription.contains("Missed"))
        // Penalty for 2 missed lectures = 0 floor (scoreUi clamps dailyPoints to >= 0)
        assertEquals(0, scoreUi.dailyPoints)
    }

    @Test
    fun testDailyScoreEmptyDay() {
        val scoreUi = AnalyticsEngine.calculateDailyScore(
            targetDateStr = "2026-10-20",
            currentDateStr = "2026-10-20",
            dayTasks = emptyList(),
            progressMap = emptyMap()
        )
        assertEquals(0, scoreUi.scorePercentage)
        assertFalse(scoreUi.isInProgress)
    }

    @Test
    fun testPointSystemFullExecution() {
        val dateStr = "2026-10-20"
        val tasks = listOf(
            createDummyTask("T1", dateStr, 1),
            createDummyTask("T2", dateStr, 2)
        )
        // User completes Slot 1 (+10), Slot 2 (+10), Revision (+5), Problem Solving (+5)
        val progressMap = mapOf(
            "T1" to TaskProgressEntity("T1", dateStr, true),
            "T2" to TaskProgressEntity("T2", dateStr, true),
            "BOOSTER_REV_$dateStr" to TaskProgressEntity("BOOSTER_REV_$dateStr", dateStr, true),
            "BOOSTER_PS_$dateStr" to TaskProgressEntity("BOOSTER_PS_$dateStr", dateStr, true)
        )

        val scoreUi = AnalyticsEngine.calculateDailyScore(
            targetDateStr = dateStr,
            currentDateStr = dateStr,
            dayTasks = tasks,
            progressMap = progressMap
        )

        assertEquals(30, scoreUi.dailyPoints)
        assertEquals(100, scoreUi.scorePercentage)
    }

    @Test
    fun testPreCompletedSubjectRegistryValues() {
        assertEquals(41, SubjectRegistry.getPriorCompletedLectures("Digital Logic"))
        assertEquals(27, SubjectRegistry.getPriorCompletedLectures("DBMS"))
        assertEquals(18, SubjectRegistry.getPriorCompletedLectures("Computer Network"))
        assertEquals("DL", SubjectRegistry.getCodeForSubject("Digital Logic"))
        assertEquals("DBMS", SubjectRegistry.getCodeForSubject("DBMS"))
        assertEquals("CN", SubjectRegistry.getCodeForSubject("Computer Network"))
    }

    @Test
    fun testAdherenceAndForecastingPacing() {
        val tasks = listOf(
            createDummyTask("T1", "2026-10-19", 1),
            createDummyTask("T2", "2026-10-19", 2),
            createDummyTask("T3", "2026-10-20", 1),
            createDummyTask("T4", "2026-10-20", 2)
        )
        val allProgress = listOf(
            TaskProgressEntity("T1", "2026-10-19", true, completionType = "ON_TIME"),
            TaskProgressEntity("T2", "2026-10-19", true, completionType = "ON_TIME"),
            TaskProgressEntity("T3", "2026-10-20", true, completionType = "ON_TIME")
        )

        val metrics = AnalyticsEngine.calculateAdherenceAndForecast(
            currentDateStr = "2026-10-20",
            allTasks = tasks,
            allProgress = allProgress,
            activeRecoveries = emptyList()
        )

        assertEquals("Should have 0 backlog since T4 is due today, not past", 0, metrics.currentBacklog)
        assertEquals(PreparationHealth.ON_TRACK, metrics.health)
        assertEquals("Preliminary (Early Days)", metrics.forecastConfidence)
        assertTrue(metrics.forecastCompletionDate != null)
    }

    @Test
    fun testBehindScheduleWhenBacklogAccumulates() {
        val tasks = (1..10).flatMap { day ->
            val dateStr = "2026-10-${10 + day}"
            listOf(
                createDummyTask("T_${day}_1", dateStr, 1),
                createDummyTask("T_${day}_2", dateStr, 2)
            )
        }
        val allProgress = emptyList<TaskProgressEntity>() // None completed

        val metrics = AnalyticsEngine.calculateAdherenceAndForecast(
            currentDateStr = "2026-10-22",
            allTasks = tasks,
            allProgress = allProgress,
            activeRecoveries = emptyList()
        )

        assertTrue("Should detect heavy backlog", metrics.currentBacklog > 10)
        assertEquals(PreparationHealth.BEHIND, metrics.health)
        assertTrue(metrics.healthReason.contains("Behind schedule"))
        assertTrue(metrics.recommendedActions.any { it.contains("buffer") })
    }

    @Test
    fun testSundayPointsTargetHarmonization() {
        val sundayStr = "2026-10-25"
        val sundayTasks = listOf(
            ScheduleTaskEntity(
                id = "SUN_TEST_01",
                date = sundayStr,
                dayOfWeek = "Sun",
                slot = 1,
                subject = "Full-Syllabus Mock",
                type = "SUNDAY_TEST",
                curNum = 1,
                totalCount = 49,
                title = "Sunday Test & Revision",
                details = "",
                milestone = "",
                sourcePage = 1,
                isBuffer = false,
                isRevision = false,
                isSundayActivity = true
            ),
            ScheduleTaskEntity(
                id = "SUN_PYQ_01",
                date = sundayStr,
                dayOfWeek = "Sun",
                slot = 2,
                subject = "PYQ Practice",
                type = "SUNDAY_PYQ",
                curNum = 1,
                totalCount = 49,
                title = "Sunday PYQ Practice",
                details = "",
                milestone = "",
                sourcePage = 1,
                isBuffer = false,
                isRevision = false,
                isSundayActivity = true
            )
        )

        // User completes Sunday Test (20 pts), Sunday PYQ (5 pts), and Revision Booster (5 pts)
        val progressMap = mapOf(
            "SUN_TEST_01" to TaskProgressEntity("SUN_TEST_01", sundayStr, true),
            "SUN_PYQ_01" to TaskProgressEntity("SUN_PYQ_01", sundayStr, true),
            "BOOSTER_REV_$sundayStr" to TaskProgressEntity("BOOSTER_REV_$sundayStr", sundayStr, true)
        )

        val scoreUi = AnalyticsEngine.calculateDailyScore(
            targetDateStr = sundayStr,
            currentDateStr = sundayStr,
            dayTasks = sundayTasks,
            progressMap = progressMap
        )

        assertEquals("Sunday Test (20) + PYQ (5) + Booster Revision (5) must equal exactly 30 points", 30, scoreUi.dailyPoints)
        assertEquals(100, scoreUi.scorePercentage)
    }
}
