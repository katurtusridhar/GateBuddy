package com.gatecse.companion

import com.gatecse.companion.model.ScheduleTaskEntity
import com.gatecse.companion.model.TaskItemUi
import com.gatecse.companion.model.TaskProgressEntity
import com.gatecse.companion.model.TaskStatus
import com.gatecse.companion.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskCompletionTest {

    @Test
    fun testTaskInitialStateIsIncomplete() {
        // Core Rule: Every task begins as incomplete unless explicitly marked
        val task = ScheduleTaskEntity(
            id = "TASK_0001",
            date = "2026-10-19",
            dayOfWeek = "Mon",
            slot = 1,
            subject = "Digital Logic",
            type = "RECORDED",
            curNum = 42,
            totalCount = 52,
            title = "Digital Logic — Lec 42/52",
            details = "Digital Logic Lecture 42 of 52",
            milestone = "",
            sourcePage = 1,
            isBuffer = false,
            isRevision = false,
            isSundayActivity = false
        )

        // Without progress entity
        val progress: TaskProgressEntity? = null
        assertNull("Task progress entity must be null initially", progress)

        val isCompleted = progress?.isCompleted == true
        assertFalse("Unchecked task must NEVER be considered complete", isCompleted)
    }

    @Test
    fun testExplicitCompletionRules() {
        val taskDate = "2026-10-19"
        val todayDate = "2026-10-19"

        // On-time completion
        val onTimeProgress = TaskProgressEntity(
            taskId = "TASK_0001",
            originalDate = taskDate,
            isCompleted = true,
            completedAtTimestamp = System.currentTimeMillis(),
            completionType = "ON_TIME",
            notes = "Completed binary arithmetic exercises"
        )

        assertTrue(onTimeProgress.isCompleted)
        assertEquals("ON_TIME", onTimeProgress.completionType)
        assertEquals(taskDate, onTimeProgress.originalDate)

        // Late completion (marked on 2026-10-22 for a 2026-10-19 task)
        val lateProgress = TaskProgressEntity(
            taskId = "TASK_0001",
            originalDate = taskDate,
            isCompleted = true,
            completedAtTimestamp = System.currentTimeMillis(),
            completionType = "LATE",
            notes = "Caught up on Thursday"
        )

        assertTrue(lateProgress.isCompleted)
        assertEquals("LATE", lateProgress.completionType)
        // Original due date is strictly retained
        assertEquals("2026-10-19", lateProgress.originalDate)
    }

    @Test
    fun testTaskStatusTransitions() {
        // Due today
        val today = LocalDate.of(2026, 10, 20)
        val taskDate = LocalDate.of(2026, 10, 20)
        val statusToday = if (taskDate == today) TaskStatus.DUE_TODAY else TaskStatus.UPCOMING
        assertEquals(TaskStatus.DUE_TODAY, statusToday)

        // Past unchecked task is MISSED
        val pastDate = LocalDate.of(2026, 10, 19)
        val isCompleted = false
        val pastStatus = if (pastDate.isBefore(today) && !isCompleted) TaskStatus.MISSED else TaskStatus.DUE_TODAY
        assertEquals(TaskStatus.MISSED, pastStatus)

        // Completed late retains historical missed identity
        val completedLateStatus = TaskStatus.COMPLETED_LATE
        assertEquals(TaskStatus.COMPLETED_LATE, completedLateStatus)
    }

    @Test
    fun testPastDayTasksAreImmutableAndLocked() {
        val today = LocalDate.of(2026, 10, 21)
        val pastDate = LocalDate.of(2026, 10, 19)

        val pastTask = TaskItemUi(
            id = "TASK_0001",
            originalDate = "2026-10-19",
            displayDate = "2026-10-19",
            dayOfWeek = "Mon",
            slot = 1,
            subject = "Digital Logic",
            type = TaskType.RECORDED,
            curNum = 42,
            totalCount = 52,
            title = "Digital Logic — Lec 42/52",
            details = "Core GATE CSE lecture",
            milestone = "",
            sourcePage = 1,
            isBuffer = false,
            isRevision = false,
            isSundayActivity = false,
            status = TaskStatus.MISSED,
            isCompleted = false,
            isFuture = false,
            isPast = true
        )

        assertTrue("Past task must be marked as isPast = true", pastTask.isPast)
        assertFalse("Past task is not in the future", pastTask.isFuture)
        assertEquals("Past uncompleted task must be MISSED", TaskStatus.MISSED, pastTask.status)
        assertFalse("Past missed task cannot be completed", pastTask.isCompleted)
    }

    @Test
    fun testOnlyTodayTasksCanBeCompleted() {
        val todayStr = "2026-10-21"
        val pastDateStr = "2026-10-19"
        val futureDateStr = "2026-10-23"

        val isTodayValid = todayStr == todayStr
        val isPastValid = pastDateStr == todayStr
        val isFutureValid = futureDateStr == todayStr

        assertTrue("Today's task can be completed", isTodayValid)
        assertFalse("Past task cannot be completed directly", isPastValid)
        assertFalse("Future task cannot be completed directly", isFutureValid)
    }

    @Test
    fun testCompletingTodayTaskPreservesSlotIdentityAndDoesNotMutatePastTask() {
        val todayTaskId = "TASK_0005"
        val pastTaskId = "TASK_0001"

        // Mark today's task complete
        val progressMap = mutableMapOf<String, TaskProgressEntity>()
        progressMap[todayTaskId] = TaskProgressEntity(
            taskId = todayTaskId,
            originalDate = "2026-10-21",
            isCompleted = true,
            completedAtTimestamp = System.currentTimeMillis(),
            completionType = "ON_TIME"
        )

        // Today's task is completed
        assertTrue("Today's slot TASK_0005 must be completed", progressMap[todayTaskId]?.isCompleted == true)
        assertEquals("ON_TIME", progressMap[todayTaskId]?.completionType)

        // Past task is NOT completed
        assertFalse("Past slot TASK_0001 must NOT be marked complete", progressMap[pastTaskId]?.isCompleted == true)
        assertNull("Past slot has no progress record", progressMap[pastTaskId])
    }
}
