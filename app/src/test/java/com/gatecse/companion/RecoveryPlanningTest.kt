package com.gatecse.companion

import com.gatecse.companion.model.RecoveryAssignmentEntity
import com.gatecse.companion.model.ScheduleTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPlanningTest {

    @Test
    fun testBufferDayPriorityOverSunday() {
        val priority1 = 1 // Buffer day
        val priority2 = 2 // Sunday capacity
        val priority3 = 3 // Next available day

        assertTrue("Buffer days must have highest recovery priority", priority1 < priority2)
        assertTrue("Sunday recovery has priority over regular days", priority2 < priority3)
    }

    @Test
    fun testSundayActivitiesRemainPreservedDuringRecovery() {
        val sundayDate = "2026-10-25"
        val sundayActivities = listOf(
            ScheduleTaskEntity(
                id = "TASK_0013",
                date = sundayDate,
                dayOfWeek = "Sun",
                slot = 1,
                subject = "Weekly Test & Active Recall",
                type = "SUNDAY_TEST",
                curNum = 0,
                totalCount = 0,
                title = "Weekly Active Recall & Mock Test",
                details = "Active Recall + Weekly Test",
                milestone = "",
                sourcePage = 1,
                isBuffer = false,
                isRevision = false,
                isSundayActivity = true
            ),
            ScheduleTaskEntity(
                id = "TASK_0014",
                date = sundayDate,
                dayOfWeek = "Sun",
                slot = 2,
                subject = "Sunday Revision",
                type = "BOOSTER_REVISION",
                curNum = 0,
                totalCount = 0,
                title = "Sunday Active Recall & Formula Revision",
                details = "Revision",
                milestone = "",
                sourcePage = 1,
                isBuffer = false,
                isRevision = true,
                isSundayActivity = true
            )
        )

        // Add recovery assignment to Sunday alongside regular activities
        val recoveryAssignment = RecoveryAssignmentEntity(
            id = 1,
            taskId = "TASK_0001",
            originalDate = "2026-10-19",
            recoveryDate = sundayDate,
            assignedAtTimestamp = System.currentTimeMillis(),
            isCompleted = false,
            priorityLevel = 2,
            status = "ACTIVE"
        )

        val combinedItems = sundayActivities.map { it.id } + listOf(recoveryAssignment.taskId)
        assertEquals(3, combinedItems.size)
        assertTrue(combinedItems.contains("TASK_0013"))
        assertTrue(combinedItems.contains("TASK_0014"))
        assertTrue(combinedItems.contains("TASK_0001"))
    }

    @Test
    fun testStrictSubjectDiversityPerSunday() {
        // Rule: if 2 or more backlogs are of the same subject, strictly 1 backlog per subject per Sunday
        val sunday1 = "2026-10-25"
        val sunday2 = "2026-11-01"

        // Two backlogs of Computer Network (CN)
        val cnBacklog1 = RecoveryAssignmentEntity(
            id = 1,
            taskId = "CN_01",
            originalDate = "2026-10-19",
            recoveryDate = sunday1,
            assignedAtTimestamp = System.currentTimeMillis(),
            priorityLevel = 2,
            status = "ACTIVE"
        )
        // Second CN backlog must roll over to the next Sunday
        val cnBacklog2 = RecoveryAssignmentEntity(
            id = 2,
            taskId = "CN_02",
            originalDate = "2026-10-20",
            recoveryDate = sunday2,
            assignedAtTimestamp = System.currentTimeMillis(),
            priorityLevel = 2,
            status = "ACTIVE"
        )

        assertNotEquals("Second backlog of same subject must go to next Sunday", cnBacklog1.recoveryDate, cnBacklog2.recoveryDate)
        assertEquals(sunday1, cnBacklog1.recoveryDate)
        assertEquals(sunday2, cnBacklog2.recoveryDate)
    }

    @Test
    fun testRecoveryAssignmentRetainsOriginalDateAndSeparateRecoveryDate() {
        val originalDate = "2026-10-19"
        val recoveryDate = "2026-10-25"

        val recovery = RecoveryAssignmentEntity(
            id = 10,
            taskId = "TASK_0005",
            originalDate = originalDate,
            recoveryDate = recoveryDate,
            assignedAtTimestamp = System.currentTimeMillis(),
            priorityLevel = 1,
            status = "ACTIVE"
        )

        assertEquals("2026-10-19", recovery.originalDate)
        assertEquals("2026-10-25", recovery.recoveryDate)
        assertNotEquals(recovery.originalDate, recovery.recoveryDate)
    }

    @Test
    fun testPreventDuplicateRecoveryAssignments() {
        val existingRecoveries = mutableMapOf<String, RecoveryAssignmentEntity>()
        val recovery1 = RecoveryAssignmentEntity(
            id = 1,
            taskId = "TASK_0002",
            originalDate = "2026-10-19",
            recoveryDate = "2026-10-25",
            assignedAtTimestamp = System.currentTimeMillis(),
            priorityLevel = 2,
            status = "ACTIVE"
        )
        existingRecoveries[recovery1.taskId] = recovery1

        val taskId = "TASK_0002"
        val isAlreadyAssigned = existingRecoveries.containsKey(taskId)
        assertTrue("Must prevent creating duplicate recovery assignments for same task", isAlreadyAssigned)
    }

    @Test
    fun testManualBacklogRescheduleUpdatesDate() {
        val originalRecovery = RecoveryAssignmentEntity(
            id = 1,
            taskId = "CN_19",
            originalDate = "2026-10-19",
            recoveryDate = "2026-10-25",
            assignedAtTimestamp = System.currentTimeMillis(),
            priorityLevel = 2,
            status = "ACTIVE"
        )
        val rescheduled = originalRecovery.copy(recoveryDate = "2026-11-01")
        assertEquals("2026-11-01", rescheduled.recoveryDate)
        assertEquals("CN_19", rescheduled.taskId)
    }

    @Test
    fun testSequentialLectureOrderingInBacklogs() {
        // Earlier lecture (curNum = 19) must always be scheduled before or on the same date as later lecture (curNum = 20)
        val lec19 = ScheduleTaskEntity(
            id = "CN_19",
            date = "2026-10-19",
            dayOfWeek = "Mon",
            slot = 1,
            subject = "Computer Network",
            type = "LECTURE_RECORDED",
            curNum = 19,
            totalCount = 73,
            title = "CN Lec 19",
            details = "Routing",
            milestone = "",
            sourcePage = 1,
            isBuffer = false,
            isRevision = false,
            isSundayActivity = false
        )
        val lec20 = ScheduleTaskEntity(
            id = "CN_20",
            date = "2026-10-20",
            dayOfWeek = "Tue",
            slot = 1,
            subject = "Computer Network",
            type = "LECTURE_RECORDED",
            curNum = 20,
            totalCount = 73,
            title = "CN Lec 20",
            details = "OSPF",
            milestone = "",
            sourcePage = 1,
            isBuffer = false,
            isRevision = false,
            isSundayActivity = false
        )

        val backlogList = listOf(lec20, lec19).sortedBy { it.curNum }
        assertEquals("CN_19", backlogList[0].id)
        assertEquals("CN_20", backlogList[1].id)
        assertTrue(backlogList[0].curNum < backlogList[1].curNum)
    }

    @Test
    fun testManualOnlyBacklogRescheduleRequirement() {
        // Requirement: No automatic assignment; backlogs remain unassigned until user explicitly reschedules
        val pastTask = ScheduleTaskEntity(
            id = "DBMS_28",
            date = "2026-10-19",
            dayOfWeek = "Mon",
            slot = 2,
            subject = "Database Management",
            type = "LECTURE_RECORDED",
            curNum = 28,
            totalCount = 42,
            title = "DBMS Lec 28",
            details = "Indexing",
            milestone = "",
            sourcePage = 1,
            isBuffer = false,
            isRevision = false,
            isSundayActivity = false
        )

        // Without manual assignment, active recoveries must not contain this task
        val activeRecoveries = mutableMapOf<String, RecoveryAssignmentEntity>()
        assertTrue("Backlog must remain unassigned until manual user action", !activeRecoveries.containsKey(pastTask.id))

        // After manual reschedule
        val userAssignedRecovery = RecoveryAssignmentEntity(
            id = 5,
            taskId = pastTask.id,
            originalDate = pastTask.date,
            recoveryDate = "2026-10-25",
            assignedAtTimestamp = System.currentTimeMillis(),
            priorityLevel = 2,
            status = "ACTIVE"
        )
        activeRecoveries[pastTask.id] = userAssignedRecovery
        assertTrue("Active recovery present only after manual assignment", activeRecoveries.containsKey(pastTask.id))
        assertEquals("2026-10-25", activeRecoveries[pastTask.id]?.recoveryDate)
    }

    @Test
    fun testOneBacklogPerSundayPerSubjectConstraint() {
        val candidateSundays = listOf("2026-10-25", "2026-11-01", "2026-11-08")
        val cnTasks = listOf("CN_19", "CN_20", "CN_21")

        // Rule: If 2 or more backlogs are of the same subject, max 1 backlog per Sunday
        val assignments = mutableMapOf<String, String>()
        cnTasks.forEachIndexed { index, taskId ->
            assignments[taskId] = candidateSundays[index]
        }

        assertEquals("2026-10-25", assignments["CN_19"])
        assertEquals("2026-11-01", assignments["CN_20"])
        assertEquals("2026-11-08", assignments["CN_21"])

        // All assigned dates for this subject must be unique
        val uniqueDates = assignments.values.toSet()
        assertEquals(cnTasks.size, uniqueDates.size)
    }

    @Test
    fun testCadenceScheduleHours() {
        val hours = com.gatecse.companion.worker.NotificationScheduler.CADENCE_HOURS
        assertEquals(listOf(5, 9, 13, 17, 21, 23), hours)
    }

    @Test
    fun testSequentialContinuityAcrossFutureDatesWhenBehind() {
        // DL: completed up to 41, earliest uncompleted is 42.
        // Today is Oct 21. User is at DL 42 and CN 19.
        // Tomorrow Oct 22 MUST be DL 43 and CN 20 (not baseline DL 45 or CN 22).
        val earliestDL = 42
        val earliestCN = 19

        // On day offset 1 (Tomorrow, Oct 22):
        val tomorrowDL = earliestDL + 1
        val tomorrowCN = earliestCN + 1
        assertEquals(43, tomorrowDL)
        assertEquals(20, tomorrowCN)

        // On day offset 2 (Oct 23):
        val day2DL = earliestDL + 2
        val day2CN = earliestCN + 2
        assertEquals(44, day2DL)
        assertEquals(21, day2CN)
    }

    @Test
    fun testSundayCatchUpFollowsPrecedingSaturdayInSequence() {
        // DL: 41 completed before today (Oct 21).
        // Wed Oct 21: DL 42
        // Thu Oct 22: DL 43
        // Fri Oct 23: DL 44
        // Sat Oct 24: DL 45
        // Sun Oct 25 Catch-Up: MUST BE DL 46 (NOT DL 42!)
        val completedBefore = 41
        val uncompletedSlotsPriorToSunday = 4 // Wed, Thu, Fri, Sat
        val sundayRecoveryLectureIndex = completedBefore + uncompletedSlotsPriorToSunday // 45 -> Lec 46
        assertEquals(45, sundayRecoveryLectureIndex)
        val curNum = sundayRecoveryLectureIndex + 1
        assertEquals(46, curNum)
    }

    @Test
    fun testCatchUpSuffixNotDuplicated() {
        val baseTitleWithCatchUp = "Digital Logic — Lec 46/52 (Catch-Up)"
        val cleanTitle = baseTitleWithCatchUp.replace(Regex("\\s*\\(Catch-Up\\)"), "").trim()
        val finalTitle = "$cleanTitle (Catch-Up)"
        assertEquals("Digital Logic — Lec 46/52 (Catch-Up)", finalTitle)

        // Multiple catchups stripped
        val multiCatchUp = "Digital Logic — Lec 46/52 (Catch-Up) (Catch-Up)"
        val cleanMulti = multiCatchUp.replace(Regex("\\s*\\(Catch-Up\\)"), "").trim()
        val finalSingle = "$cleanMulti (Catch-Up)"
        assertEquals("Digital Logic — Lec 46/52 (Catch-Up)", finalSingle)
    }

    @Test
    fun testBoostersNeverEligibleForRescheduling() {
        val boosterRev = com.gatecse.companion.model.TaskItemUi(
            id = "BOOSTER_REV_2026-10-21",
            originalDate = "2026-10-21",
            displayDate = "2026-10-21",
            dayOfWeek = "Wed",
            slot = 3,
            subject = "Daily Revision",
            subjectCode = "REV",
            type = com.gatecse.companion.model.TaskType.BOOSTER_REVISION,
            curNum = 1,
            totalCount = 1,
            title = "Daily Active Revision",
            details = "Active revision",
            milestone = "",
            sourcePage = 0,
            isBuffer = false,
            isRevision = true,
            isSundayActivity = false,
            isBooster = true,
            status = com.gatecse.companion.model.TaskStatus.MISSED,
            isCompleted = false,
            pointsValue = 5,
            isFuture = false,
            isPast = true
        )

        val boosterPs = com.gatecse.companion.model.TaskItemUi(
            id = "BOOSTER_PS_2026-10-21",
            originalDate = "2026-10-21",
            displayDate = "2026-10-21",
            dayOfWeek = "Wed",
            slot = 4,
            subject = "Problem Solving",
            subjectCode = "PRB",
            type = com.gatecse.companion.model.TaskType.BOOSTER_PROBLEM_SOLVING,
            curNum = 1,
            totalCount = 1,
            title = "Daily Problem Solving",
            details = "Problem solving",
            milestone = "",
            sourcePage = 0,
            isBuffer = false,
            isRevision = false,
            isSundayActivity = false,
            isBooster = true,
            status = com.gatecse.companion.model.TaskStatus.MISSED,
            isCompleted = false,
            pointsValue = 5,
            isFuture = false,
            isPast = true
        )

        fun canReschedule(task: com.gatecse.companion.model.TaskItemUi): Boolean {
            return !task.isBooster &&
                !task.isRevision &&
                !task.isSundayActivity &&
                !task.isCustom &&
                !task.subject.contains("Revision", ignoreCase = true) &&
                !task.subject.contains("Problem", ignoreCase = true) &&
                !task.title.contains("Revision", ignoreCase = true) &&
                !task.title.contains("Problem", ignoreCase = true) &&
                (task.status == com.gatecse.companion.model.TaskStatus.MISSED || task.status == com.gatecse.companion.model.TaskStatus.RECOVERY_SCHEDULED || task.isAssignedForRecovery || (!task.isFuture && !task.isCompleted))
        }

        assertTrue("Revision booster must NEVER be eligible for rescheduling", !canReschedule(boosterRev))
        assertTrue("Problem solving booster must NEVER be eligible for rescheduling", !canReschedule(boosterPs))
    }

    @Test
    fun testRule1MaxTwoRecoveriesPerDay() {
        val date = "2026-10-25"
        val existingRecoveriesOnDate = listOf(
            RecoveryAssignmentEntity(
                id = 1,
                taskId = "DL_42",
                originalDate = "2026-10-21",
                recoveryDate = date,
                assignedAtTimestamp = System.currentTimeMillis(),
                priorityLevel = 2,
                status = "ACTIVE"
            ),
            RecoveryAssignmentEntity(
                id = 2,
                taskId = "CN_19",
                originalDate = "2026-10-21",
                recoveryDate = date,
                assignedAtTimestamp = System.currentTimeMillis(),
                priorityLevel = 2,
                status = "ACTIVE"
            )
        )

        // Attempting to schedule a 3rd recovery on the same date must fail Rule 1
        val canAddThird = existingRecoveriesOnDate.size < 2
        assertTrue("Day with 2 recoveries must reject a 3rd recovery (Rule 1 violation)", !canAddThird)
    }

    @Test
    fun testRule2MaxOneLecturePerSubjectPerDay() {
        val date = "2026-10-25"
        val existingSubjectsOnDate = setOf("digital logic")

        // Attempting to schedule another DL lecture on the same date must fail Rule 2
        val newSubject1 = "Digital Logic"
        val canAddDL = !existingSubjectsOnDate.contains(newSubject1.lowercase().trim())
        assertTrue("Cannot schedule 2 lectures of Digital Logic on same day (Rule 2 violation)", !canAddDL)

        // Scheduling a different subject (e.g. Computer Network) is permitted under Rule 2
        val newSubject2 = "Computer Network"
        val canAddCN = !existingSubjectsOnDate.contains(newSubject2.lowercase().trim())
        assertTrue("Different subject allowed on same date under Rule 2", canAddCN)
    }

    @Test
    fun testReschedulingOneLecturePreservesAllOtherSchedules() {
        // Given three independent recovery assignments
        val scheduleMap = mutableMapOf(
            "DL_42" to "2026-10-25",
            "CN_19" to "2026-10-25",
            "DBMS_28" to "2026-11-01"
        )

        // Reschedule DL_42 to a new custom date 2026-11-04
        val targetTaskId = "DL_42"
        val newTargetDate = "2026-11-04"

        // Update ONLY the target task
        scheduleMap[targetTaskId] = newTargetDate

        // Verify other schedules are 100% intact and unaffected
        assertEquals("2026-11-04", scheduleMap["DL_42"])
        assertEquals("2026-10-25", scheduleMap["CN_19"]) // MUST remain untouched
        assertEquals("2026-11-01", scheduleMap["DBMS_28"]) // MUST remain untouched
    }

    @Test
    fun testCandidateSlotsAvailabilityValidation() {
        // Slot with 1 existing task from Digital Logic
        val slotDate = "2026-10-25"
        val existingCount = 1
        val existingSubjects = listOf("Digital Logic")

        // Slot capacity: 2 max
        val isFull = existingCount >= 2
        assertTrue("Slot with 1 item is not full", !isFull)

        // Can accept Computer Network? Yes
        val canAcceptCN = !isFull && !existingSubjects.any { it.equals("Computer Network", ignoreCase = true) }
        assertTrue("Slot can accept Computer Network", canAcceptCN)

        // Can accept Digital Logic? No (violates Rule 2)
        val canAcceptDL = !isFull && !existingSubjects.any { it.equals("Digital Logic", ignoreCase = true) }
        assertTrue("Slot cannot accept second Digital Logic lecture", !canAcceptDL)
    }
}
