package com.gatecse.companion

import com.gatecse.companion.analytics.AnalyticsEngine
import com.gatecse.companion.model.AssessmentResultEntity
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

    @Test
    fun testSundayScoreProportionalPoints() {
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
                title = "Sunday Test",
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
                title = "Sunday PYQ",
                details = "",
                milestone = "",
                sourcePage = 1,
                isBuffer = false,
                isRevision = false,
                isSundayActivity = true
            )
        )

        // Case A: 80% marks recorded (e.g. 40/50 -> 16 pts). With PYQ (5) + Rev (5) -> 26 pts total
        val progressMap80 = mapOf(
            "SUN_TEST_01" to TaskProgressEntity("SUN_TEST_01", sundayStr, true, notes = "[TEST] Marks: 40/50"),
            "SUN_PYQ_01" to TaskProgressEntity("SUN_PYQ_01", sundayStr, true),
            "BOOSTER_REV_$sundayStr" to TaskProgressEntity("BOOSTER_REV_$sundayStr", sundayStr, true)
        )
        val score80 = AnalyticsEngine.calculateDailyScore(
            targetDateStr = sundayStr,
            currentDateStr = sundayStr,
            dayTasks = sundayTasks,
            progressMap = progressMap80
        )
        assertEquals("80% score (40/50) should award 16 test pts + 5 PYQ + 5 Rev = 26 pts", 26, score80.dailyPoints)

        // Case B: 50% marks recorded (e.g. 25/50 -> 10 pts). With PYQ (5) + Rev (5) -> 20 pts total
        val progressMap50 = mapOf(
            "SUN_TEST_01" to TaskProgressEntity("SUN_TEST_01", sundayStr, true, notes = "[TEST] Marks: 25/50"),
            "SUN_PYQ_01" to TaskProgressEntity("SUN_PYQ_01", sundayStr, true),
            "BOOSTER_REV_$sundayStr" to TaskProgressEntity("BOOSTER_REV_$sundayStr", sundayStr, true)
        )
        val score50 = AnalyticsEngine.calculateDailyScore(
            targetDateStr = sundayStr,
            currentDateStr = sundayStr,
            dayTasks = sundayTasks,
            progressMap = progressMap50
        )
        assertEquals("50% score (25/50) should award 10 test pts + 5 PYQ + 5 Rev = 20 pts", 20, score50.dailyPoints)
    }

    @Test
    fun testMissedSundayTestPenalty() {
        val sundayStr = "2026-10-18" // Past Sunday
        val sundayTasks = listOf(
            ScheduleTaskEntity(
                id = "SUN_TEST_PAST",
                date = sundayStr,
                dayOfWeek = "Sun",
                slot = 1,
                subject = "Weekly Test",
                type = "SUNDAY_TEST",
                curNum = 1,
                totalCount = 49,
                title = "Sunday Test",
                details = "",
                milestone = "",
                sourcePage = 1,
                isBuffer = false,
                isRevision = false,
                isSundayActivity = true
            )
        )
        // User only completed revision booster (+5 pts) on past Sunday, but missed the test (-4 pts)
        val progressMap = mapOf(
            "BOOSTER_REV_$sundayStr" to TaskProgressEntity("BOOSTER_REV_$sundayStr", sundayStr, true)
        )
        val scoreUi = AnalyticsEngine.calculateDailyScore(
            targetDateStr = sundayStr,
            currentDateStr = "2026-10-25", // Current is later date
            dayTasks = sundayTasks,
            progressMap = progressMap
        )
        // 5 (Revision) - 4 (Missed Sunday Test penalty) = 1 pt
        assertEquals("Missed Sunday test penalty (-4) applied against earned points (5) = 1 pt", 1, scoreUi.dailyPoints)
    }

    @Test
    fun testSevenDayRollingAverageCalculation() {
        val todayStr = "2026-10-25"
        val tasks = (0..6).map { dayOffset ->
            val date = java.time.LocalDate.parse(todayStr).minusDays(dayOffset.toLong()).toString()
            createDummyTask("T_${dayOffset}_1", date, 1)
        }

        // Complete 1 lecture (10 pts) on each of the 7 days -> 70 total pts / 7 days = 10.0 pts/day
        val allProgress = (0..6).map { dayOffset ->
            val date = java.time.LocalDate.parse(todayStr).minusDays(dayOffset.toLong()).toString()
            TaskProgressEntity("T_${dayOffset}_1", date, true, completionType = "ON_TIME")
        }

        val metrics = AnalyticsEngine.calculateAdherenceAndForecast(
            currentDateStr = todayStr,
            allTasks = tasks,
            allProgress = allProgress,
            activeRecoveries = emptyList()
        )

        // 7 days * 10 pts = 70 pts total -> averageDailyPoints = 10.0
        assertEquals(10.0, metrics.averageDailyPoints, 0.01)
        assertTrue(metrics.healthReason.contains("7-day average"))
    }

    @Test
    fun testSundayAssessmentAndSyntheticTaskIdScoring() {
        val sundayDateStr = "2026-10-25"
        val sundayTasks = listOf(
            ScheduleTaskEntity(
                id = "TASK_0013",
                date = sundayDateStr,
                dayOfWeek = "Sun",
                slot = 1,
                subject = "Weekly Test & Active Recall",
                type = "SUNDAY_TEST",
                curNum = 0,
                totalCount = 0,
                title = "SUNDAY TEST & REVISION",
                details = "",
                milestone = "",
                sourcePage = 1,
                isBuffer = false,
                isRevision = true,
                isSundayActivity = true
            ),
            ScheduleTaskEntity(
                id = "TASK_0014",
                date = sundayDateStr,
                dayOfWeek = "Sun",
                slot = 2,
                subject = "PYQ Practice & Mistake Analysis",
                type = "SUNDAY_PYQ",
                curNum = 0,
                totalCount = 0,
                title = "SUNDAY PYQ PRACTICE",
                details = "",
                milestone = "",
                sourcePage = 1,
                isBuffer = false,
                isRevision = false,
                isSundayActivity = true
            )
        )

        // User enters marks: 40/50 (80%)
        val assessment = AssessmentResultEntity(
            testDate = sundayDateStr,
            testName = "Sunday Assessment (DL)",
            subjectName = "Digital Logic",
            marksObtained = 40.0,
            maxMarks = 50.0,
            percentage = 80.0
        )

        // Progress stored under UI task IDs
        val progressMap = mapOf(
            "SUNDAY_TEST_$sundayDateStr" to TaskProgressEntity("SUNDAY_TEST_$sundayDateStr", sundayDateStr, true, notes = "[DL] Marks: 40.0/50.0"),
            "BOOSTER_REV_$sundayDateStr" to TaskProgressEntity("BOOSTER_REV_$sundayDateStr", sundayDateStr, true),
            "BOOSTER_PS_$sundayDateStr" to TaskProgressEntity("BOOSTER_PS_$sundayDateStr", sundayDateStr, true)
        )

        val score = AnalyticsEngine.calculateDailyScore(
            targetDateStr = sundayDateStr,
            currentDateStr = sundayDateStr,
            dayTasks = sundayTasks,
            progressMap = progressMap,
            assessments = listOf(assessment)
        )

        // 80% of 20 = 16 pts + 5 (Rev) + 5 (PS) = 26 pts total!
        assertEquals("Sunday 80% test score (16 pts) + Rev (5 pts) + PYQ (5 pts) must equal 26 pts", 26, score.dailyPoints)
        assertEquals("Completed tasks should be 3 of 3", 3, score.completedScheduledTasks)

        // Verify calculateAdherenceAndForecast also yields exact same 26 points and 0 backlog
        val metrics = AnalyticsEngine.calculateAdherenceAndForecast(
            currentDateStr = sundayDateStr,
            allTasks = sundayTasks,
            allProgress = progressMap.values.toList(),
            activeRecoveries = emptyList(),
            allAssessments = listOf(assessment)
        )

        assertEquals("Adherence daily points today must be 26", 26, metrics.dailyPointsToday)
        assertEquals("Completed Sunday test must not be counted as backlog", 0, metrics.currentBacklog)
    }

    @Test
    fun testMissedSundayTestNeverCountedAsBacklog() {
        val sundayDateStr = "2026-10-25"
        val mondayDateStr = "2026-10-26" // Next day (Sunday is now past)

        val sundayTest = ScheduleTaskEntity(
            id = "TASK_0013",
            date = sundayDateStr,
            dayOfWeek = "Sun",
            slot = 1,
            subject = "Weekly Test & Active Recall",
            type = "SUNDAY_TEST",
            curNum = 0,
            totalCount = 0,
            title = "Weekly Active Recall & Mock Test",
            details = "",
            milestone = "",
            sourcePage = 1,
            isBuffer = false,
            isRevision = true,
            isSundayActivity = true
        )
        val sundayPyq = ScheduleTaskEntity(
            id = "TASK_0014",
            date = sundayDateStr,
            dayOfWeek = "Sun",
            slot = 2,
            subject = "PYQ Practice & Mistake Analysis",
            type = "SUNDAY_PYQ",
            curNum = 0,
            totalCount = 0,
            title = "SUNDAY PYQ PRACTICE",
            details = "",
            milestone = "",
            sourcePage = 1,
            isBuffer = false,
            isRevision = false,
            isSundayActivity = true
        )

        // Neither task was completed on Sunday
        val metricsMissedSunday = AnalyticsEngine.calculateAdherenceAndForecast(
            currentDateStr = mondayDateStr,
            allTasks = listOf(sundayTest, sundayPyq),
            allProgress = emptyList(),
            activeRecoveries = emptyList()
        )

        assertEquals("Missed Sunday mock test and PYQ must NEVER be counted as backlog", 0, metricsMissedSunday.currentBacklog)

        // Now test with an actual core lecture missed
        val coreLecture = ScheduleTaskEntity(
            id = "TASK_0001",
            date = "2026-10-24", // Saturday lecture missed
            dayOfWeek = "Sat",
            slot = 1,
            subject = "Digital Logic",
            type = "RECORDED",
            curNum = 42,
            totalCount = 52,
            title = "Digital Logic — Lec 42/52",
            details = "Core lecture",
            milestone = "",
            sourcePage = 1,
            isBuffer = false,
            isRevision = false,
            isSundayActivity = false
        )

        val metricsMissedLecture = AnalyticsEngine.calculateAdherenceAndForecast(
            currentDateStr = mondayDateStr,
            allTasks = listOf(sundayTest, sundayPyq, coreLecture),
            allProgress = emptyList(),
            activeRecoveries = emptyList()
        )

        assertEquals("Only missed core lecture must be counted as backlog", 1, metricsMissedLecture.currentBacklog)
    }
}
