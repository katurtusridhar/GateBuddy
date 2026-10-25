package com.gatecse.companion.data

import com.gatecse.companion.analytics.AnalyticsEngine
import com.gatecse.companion.gamification.AchievementManager
import com.gatecse.companion.model.AchievementEntity
import com.gatecse.companion.model.AssessmentResultEntity
import com.gatecse.companion.model.CalendarDayUi
import com.gatecse.companion.model.CustomTaskEntity
import com.gatecse.companion.model.DppRecordEntity
import com.gatecse.companion.model.PreparationAdherenceUi
import com.gatecse.companion.model.RecoveryAssignmentEntity
import com.gatecse.companion.model.ScheduleTaskEntity
import com.gatecse.companion.model.SubjectRegistry
import com.gatecse.companion.model.SubjectUi
import com.gatecse.companion.model.TaskItemUi
import com.gatecse.companion.model.TaskProgressEntity
import com.gatecse.companion.model.TaskStatus
import com.gatecse.companion.model.TaskType
import com.gatecse.companion.model.UserSettingsEntity
import com.gatecse.companion.recovery.RecoveryEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

sealed class RescheduleValidationResult {
    object Success : RescheduleValidationResult()
    data class Error(val message: String) : RescheduleValidationResult()
}

data class CandidateDateSlot(
    val date: String,
    val currentCount: Int,
    val existingSubjects: List<String>
) {
    val isFull: Boolean get() = currentCount >= 2
    fun canAcceptSubject(subject: String): Boolean {
        if (isFull) return false
        return existingSubjects.none { it.equals(subject, ignoreCase = true) }
    }
}

class ScheduleRepository(
    private val database: GateCompanionDatabase,
    private val context: android.content.Context? = null
) {

    private val dao = database.scheduleDao()
    private val recoveryEngine = RecoveryEngine(dao)
    private val achievementManager = AchievementManager(dao)
    private val syncRepository = com.gatecse.companion.sync.SyncRepository(dao, context)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    suspend fun syncWithCloud(): com.gatecse.companion.sync.SyncResult = withContext(Dispatchers.IO) {
        val res = syncRepository.performBidirectionalSync()
        try {
            val allTasks = dao.getAllScheduleTasks()
            val allProgress = dao.getAllProgress()
            val backlog = dao.getActiveRecoveries().size
            val testsCount = dao.getAllAssessments().size
            achievementManager.evaluateAchievements(allTasks, allProgress, backlog, testsCount)
        } catch (_: Exception) {}
        res
    }

    fun getUserSettingsFlow(): Flow<UserSettingsEntity?> = dao.getUserSettingsFlow()

    fun getAllProgressFlow(): Flow<List<TaskProgressEntity>> = dao.getAllProgressFlow()

    fun getActiveRecoveriesFlow(): Flow<List<RecoveryAssignmentEntity>> = dao.getActiveRecoveriesFlow()

    suspend fun getUserSettings(): UserSettingsEntity {
        return dao.getUserSettings() ?: UserSettingsEntity(id = 1)
    }

    suspend fun saveUserSettings(settings: UserSettingsEntity) = withContext(Dispatchers.IO) {
        dao.insertOrUpdateUserSettings(settings)
    }

    suspend fun reconcileRecovery(currentDate: String, dailyLimit: Int = 2) = withContext(Dispatchers.IO) {
        recoveryEngine.reconcileRecoveryAssignments(currentDate, dailyLimit)
    }

    /**
     * Updates the recovery date for a backlog task while strictly enforcing:
     * 1) Only 2 lectures per day maximum (Rule 1).
     * 2) 2 subjects cannot be the same on the same day (Rule 2).
     * 3) All previous schedules for other tasks remain completely intact.
     */
    suspend fun updateRecoveryDate(
        taskId: String,
        newRecoveryDate: String,
        currentDateStr: String? = null
    ): RescheduleValidationResult = withContext(Dispatchers.IO) {
        val cleanTaskId = taskId.removePrefix("REC_")
        val task = dao.getTaskById(cleanTaskId) ?: return@withContext RescheduleValidationResult.Error("Task not found.")
        val targetDate = try {
            LocalDate.parse(newRecoveryDate, dateFormatter)
        } catch (e: Exception) {
            return@withContext RescheduleValidationResult.Error("Invalid date format: $newRecoveryDate. Use YYYY-MM-DD.")
        }

        if (currentDateStr != null) {
            val today = try { LocalDate.parse(currentDateStr, dateFormatter) } catch (e: Exception) { null }
            if (today != null && targetDate.isBefore(today)) {
                return@withContext RescheduleValidationResult.Error("Cannot reschedule to a past date ($newRecoveryDate).")
            }
        }

        val allActiveRecoveries = dao.getActiveRecoveries()
        val otherRecoveriesOnDate = allActiveRecoveries.filter {
            it.recoveryDate == newRecoveryDate && it.taskId != cleanTaskId
        }

        // Rule 1: Only 2 lectures per day
        if (otherRecoveriesOnDate.size >= 2) {
            return@withContext RescheduleValidationResult.Error("Cannot reschedule to $newRecoveryDate: Maximum 2 recovery lectures per day allowed (Rule 1).")
        }

        // Rule 2: 2 subjects cannot be the same on the same day
        val sameSubjectExists = otherRecoveriesOnDate.any { rec ->
            val otherTask = dao.getTaskById(rec.taskId)
            otherTask?.subject.equals(task.subject, ignoreCase = true)
        }
        if (sameSubjectExists) {
            return@withContext RescheduleValidationResult.Error("Cannot reschedule to $newRecoveryDate: A recovery lecture for ${task.subject} is already scheduled on this day (Rule 2).")
        }

        // Save this recovery assignment
        com.gatecse.companion.sync.SyncRepository.registerLocalMutation(cleanTaskId)
        val recovery = dao.getRecoveryForTask(cleanTaskId)
        if (recovery != null) {
            dao.updateRecoveryAssignment(recovery.copy(recoveryDate = newRecoveryDate, status = "MANUAL_CONFIRMED"))
        } else {
            dao.insertRecoveryAssignment(
                RecoveryAssignmentEntity(
                    taskId = cleanTaskId,
                    originalDate = task.date,
                    recoveryDate = newRecoveryDate,
                    assignedAtTimestamp = System.currentTimeMillis(),
                    isCompleted = false,
                    priorityLevel = 2,
                    status = "MANUAL_CONFIRMED"
                )
            )
        }

        // Previous schedules for all other lectures remain 100% INTACT!
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try { syncRepository.performBidirectionalSync() } catch (_: Exception) {}
        }
        RescheduleValidationResult.Success
    }

    /**
     * Batch updates multiple recovery dates with collective Rule 1 & Rule 2 validation.
     * Guarantees that all previous schedules outside this batch remain intact.
     */
    suspend fun updateBatchRecoveryDates(
        assignments: Map<String, String>,
        currentDateStr: String? = null
    ): RescheduleValidationResult = withContext(Dispatchers.IO) {
        val today = if (currentDateStr != null) {
            try { LocalDate.parse(currentDateStr, dateFormatter) } catch (e: Exception) { null }
        } else null

        val allActiveRecoveries = dao.getActiveRecoveries().associateBy { it.taskId }

        val cleanAssignments = mutableMapOf<ScheduleTaskEntity, LocalDate>()
        for ((rawTaskId, dateStr) in assignments) {
            val cleanId = rawTaskId.removePrefix("REC_")
            val task = dao.getTaskById(cleanId) ?: continue
            val date = try {
                LocalDate.parse(dateStr, dateFormatter)
            } catch (e: Exception) {
                return@withContext RescheduleValidationResult.Error("Invalid date format: $dateStr")
            }
            if (today != null && date.isBefore(today)) {
                return@withContext RescheduleValidationResult.Error("Cannot reschedule to a past date ($dateStr).")
            }
            cleanAssignments[task] = date
        }

        // Group by target date to validate Rule 1 & Rule 2 across the entire assignment set
        val byDate = cleanAssignments.entries.groupBy { it.value.format(dateFormatter) }
        for ((dateStr, entries) in byDate) {
            // Existing active recoveries on this date that are not part of this batch
            val existingOnDate = allActiveRecoveries.values.filter {
                it.recoveryDate == dateStr && cleanAssignments.keys.none { t -> t.id == it.taskId }
            }

            val totalCount = existingOnDate.size + entries.size
            if (totalCount > 2) {
                return@withContext RescheduleValidationResult.Error("Cannot reschedule: $dateStr would have $totalCount lectures. Maximum 2 lectures per day allowed (Rule 1).")
            }

            // Check duplicate subjects on dateStr
            val subjectsOnDate = mutableSetOf<String>()
            for (rec in existingOnDate) {
                val t = dao.getTaskById(rec.taskId)
                if (t != null) {
                    subjectsOnDate.add(t.subject.lowercase().trim())
                }
            }
            for (e in entries) {
                val subj = e.key.subject.lowercase().trim()
                if (subjectsOnDate.contains(subj)) {
                    return@withContext RescheduleValidationResult.Error("Cannot reschedule: Multiple lectures for '${e.key.subject}' assigned to $dateStr (Rule 2).")
                }
                subjectsOnDate.add(subj)
            }
        }

        // Validation passed! Save all assignments intact
        for ((task, date) in cleanAssignments) {
            val dateStr = date.format(dateFormatter)
            com.gatecse.companion.sync.SyncRepository.registerLocalMutation(task.id)
            val existingRecovery = dao.getRecoveryForTask(task.id)
            if (existingRecovery != null) {
                dao.updateRecoveryAssignment(existingRecovery.copy(recoveryDate = dateStr, status = "MANUAL_CONFIRMED"))
            } else {
                dao.insertRecoveryAssignment(
                    RecoveryAssignmentEntity(
                        taskId = task.id,
                        originalDate = task.date,
                        recoveryDate = dateStr,
                        assignedAtTimestamp = System.currentTimeMillis(),
                        isCompleted = false,
                        priorityLevel = 2,
                        status = "MANUAL_CONFIRMED"
                    )
                )
            }
        }

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try { syncRepository.performBidirectionalSync() } catch (_: Exception) {}
        }
        RescheduleValidationResult.Success
    }

    /**
     * Returns candidate upcoming Sundays and buffer days for manual rescheduling.
     */
    suspend fun getCandidateRescheduleDates(currentDateStr: String): List<String> = withContext(Dispatchers.IO) {
        val today = LocalDate.parse(currentDateStr, dateFormatter)
        val allTasks = dao.getAllScheduleTasks().groupBy { it.date }
        allTasks.keys
            .map { LocalDate.parse(it, dateFormatter) }
            .filter { !it.isBefore(today) }
            .filter { date ->
                val dStr = date.format(dateFormatter)
                val dTasks = allTasks[dStr].orEmpty()
                date.dayOfWeek.name == "SUNDAY" || dTasks.any { it.isBuffer }
            }
            .sorted()
            .take(16)
            .map { it.format(dateFormatter) }
    }

    /**
     * Returns slot availability info for candidate reschedule dates.
     */
    suspend fun getCandidateSlotsInfo(currentDateStr: String): List<CandidateDateSlot> = withContext(Dispatchers.IO) {
        val candidateDates = getCandidateRescheduleDates(currentDateStr)
        val allActive = dao.getActiveRecoveries()
        val allTasks = dao.getAllScheduleTasks().associateBy { it.id }

        candidateDates.map { date ->
            val onDate = allActive.filter { it.recoveryDate == date }
            val subjects = onDate.mapNotNull { allTasks[it.taskId]?.subject }
            CandidateDateSlot(
                date = date,
                currentCount = onDate.size,
                existingSubjects = subjects
            )
        }
    }

    /**
     * Returns all uncompleted past core lectures that do NOT have a valid future recovery date assigned.
     * User requirement: Backlogs are strictly manually rescheduled, and the app restricts other activity
     * until all backlogs are scheduled for recovery.
     */
    suspend fun getUnrescheduledBacklogs(currentDateStr: String): List<TaskItemUi> = withContext(Dispatchers.IO) {
        val today = LocalDate.parse(currentDateStr, dateFormatter)
        val allTasks = dao.getAllScheduleTasks()
        val allProgress = dao.getAllProgress().associateBy { it.taskId }
        val activeRecoveries = dao.getActiveRecoveries().associateBy { it.taskId }

        val overdueTasks = allTasks.filter { task ->
            val taskDate = LocalDate.parse(task.date, dateFormatter)
            val isCompleted = allProgress[task.id]?.isCompleted == true
            val hasValidFutureRecovery = activeRecoveries[task.id]?.let { rec ->
                if (rec.status != "MANUAL_CONFIRMED" && rec.status != "ACTIVE") return@let false
                try {
                    val recDate = LocalDate.parse(rec.recoveryDate, dateFormatter)
                    !recDate.isBefore(today)
                } catch (e: Exception) {
                    false
                }
            } ?: false

            taskDate.isBefore(today) &&
                !isCompleted &&
                !task.isSundayActivity &&
                !task.isRevision &&
                !task.isBuffer &&
                !task.subject.contains("Revision", ignoreCase = true) &&
                !task.subject.contains("Problem", ignoreCase = true) &&
                !task.title.contains("Revision", ignoreCase = true) &&
                !task.title.contains("Buffer", ignoreCase = true) &&
                !task.title.contains("Problem", ignoreCase = true) &&
                !hasValidFutureRecovery
        }.sortedWith(compareBy<ScheduleTaskEntity> { it.subject }.thenBy { it.curNum })

        overdueTasks.map { task ->
            mapTaskToUi(
                task = task,
                progress = allProgress[task.id],
                recovery = null,
                todayDate = today,
                targetDate = LocalDate.parse(task.date, dateFormatter),
                isRecoveryAssignment = false
            ).copy(status = TaskStatus.MISSED)
        }
    }

    suspend fun getTasksForDate(dateStr: String, currentDateStr: String): List<TaskItemUi> = withContext(Dispatchers.IO) {
        val tasks = dao.getTasksForDate(dateStr)
        val recoveriesForToday = dao.getRecoveriesForDate(dateStr)
        val allProgress = dao.getAllProgress().associateBy { it.taskId }
        val activeRecoveries = dao.getActiveRecoveries().associateBy { it.taskId }
        val customTasks = dao.getCustomTasksForDate(dateStr)

        val todayDate = LocalDate.parse(currentDateStr, dateFormatter)
        val targetDate = LocalDate.parse(dateStr, dateFormatter)
        val isFuture = targetDate.isAfter(todayDate)
        val isPast = targetDate.isBefore(todayDate)
        val isSunday = targetDate.dayOfWeek.name == "SUNDAY"

        val taskList = mutableListOf<TaskItemUi>()

        if (isSunday) {
            // STRICT SUNDAY WORKFLOW (User requirement: Exactly Test, Revision, Problem Solving + Rescheduled tasks)
            // 1. Weekly Active Recall & Mock Test
            val testId = "SUNDAY_TEST_$dateStr"
            val testProgress = allProgress[testId]
            val isTestDone = testProgress?.isCompleted == true

            taskList.add(
                TaskItemUi(
                    id = testId,
                    originalDate = dateStr,
                    displayDate = dateStr,
                    dayOfWeek = "Sun",
                    slot = 1,
                    subject = "Weekly Assessment",
                    subjectCode = "TEST",
                    type = TaskType.SUNDAY_TEST,
                    curNum = 1,
                    totalCount = 1,
                    title = "Weekly Active Recall & Mock Test",
                    details = "Attempt weekly timed assessment and log marks in Mistake Notebook.",
                    milestone = "", // No milestone banner on Sunday test
                    sourcePage = 1,
                    isBuffer = false,
                    isRevision = false,
                    isSundayActivity = true,
                    isBooster = false,
                    status = if (isTestDone) TaskStatus.COMPLETED_ON_TIME else if (isPast) TaskStatus.MISSED else if (isFuture) TaskStatus.UPCOMING else TaskStatus.DUE_TODAY,
                    isCompleted = isTestDone,
                    completedAtTimestamp = testProgress?.completedAtTimestamp,
                    completionType = testProgress?.completionType,
                    userNotes = testProgress?.notes.orEmpty(),
                    pointsValue = 20,
                    isFuture = isFuture,
                    isPast = isPast
                )
            )

            // 2. Sunday Active Revision
            val revId = "BOOSTER_REV_$dateStr"
            val revProgress = allProgress[revId]
            val isRevDone = revProgress?.isCompleted == true
            taskList.add(
                TaskItemUi(
                    id = revId,
                    originalDate = dateStr,
                    displayDate = dateStr,
                    dayOfWeek = "Sun",
                    slot = 2,
                    subject = "Sunday Revision",
                    subjectCode = "REV",
                    type = TaskType.BOOSTER_REVISION,
                    curNum = 1,
                    totalCount = 1,
                    title = "Sunday Active Recall & Formula Revision",
                    details = "Review flashcards, core formulas, and theorem cheat-sheets (+5 pts).",
                    milestone = "",
                    sourcePage = 0,
                    isBuffer = false,
                    isRevision = true,
                    isSundayActivity = true,
                    isBooster = true,
                    status = if (isRevDone) TaskStatus.COMPLETED_ON_TIME else if (isFuture || isPast) TaskStatus.UPCOMING else TaskStatus.DUE_TODAY,
                    isCompleted = isRevDone,
                    completedAtTimestamp = revProgress?.completedAtTimestamp,
                    completionType = revProgress?.completionType,
                    userNotes = revProgress?.notes.orEmpty(),
                    pointsValue = 5,
                    isFuture = isFuture,
                    isPast = isPast
                )
            )

            // 3. Sunday Problem Solving & Mistake Notebook
            val psId = "BOOSTER_PS_$dateStr"
            val psProgress = allProgress[psId]
            val isPsDone = psProgress?.isCompleted == true
            taskList.add(
                TaskItemUi(
                    id = psId,
                    originalDate = dateStr,
                    displayDate = dateStr,
                    dayOfWeek = "Sun",
                    slot = 3,
                    subject = "PYQ Practice",
                    subjectCode = "PYQ",
                    type = TaskType.BOOSTER_PROBLEM_SOLVING,
                    curNum = 1,
                    totalCount = 1,
                    title = "Sunday PYQ Practice & Mistake Analysis",
                    details = "Solve previous year questions and log errors in your mistake notebook (+5 pts).",
                    milestone = "",
                    sourcePage = 0,
                    isBuffer = false,
                    isRevision = false,
                    isSundayActivity = true,
                    isBooster = true,
                    status = if (isPsDone) TaskStatus.COMPLETED_ON_TIME else if (isFuture || isPast) TaskStatus.UPCOMING else TaskStatus.DUE_TODAY,
                    isCompleted = isPsDone,
                    completedAtTimestamp = psProgress?.completedAtTimestamp,
                    completionType = psProgress?.completionType,
                    userNotes = psProgress?.notes.orEmpty(),
                    pointsValue = 5,
                    isFuture = isFuture,
                    isPast = isPast
                )
            )
        } else {
            // Monday through Saturday: Scheduled core slots (Strict sequential lecture continuity)
            for (task in tasks) {
                val sequentialTask = getSequentialTaskForDate(
                    baseTask = task,
                    targetDate = targetDate,
                    todayDate = todayDate,
                    dateStr = dateStr,
                    allProgress = allProgress
                )

                val item = mapTaskToUi(
                    task = sequentialTask,
                    progress = allProgress[sequentialTask.id],
                    recovery = activeRecoveries[sequentialTask.id],
                    todayDate = todayDate,
                    targetDate = targetDate
                )
                taskList.add(item)
            }

            // Everyday Booster: Revision (5 pts, no backlog)
            val revId = "BOOSTER_REV_$dateStr"
            val revProgress = allProgress[revId]
            val isRevDone = revProgress?.isCompleted == true
            taskList.add(
                TaskItemUi(
                    id = revId,
                    originalDate = dateStr,
                    displayDate = dateStr,
                    dayOfWeek = targetDate.dayOfWeek.name.take(3),
                    slot = 3,
                    subject = "Daily Revision",
                    subjectCode = "REV",
                    type = TaskType.BOOSTER_REVISION,
                    curNum = 1,
                    totalCount = 1,
                    title = "Daily Active Revision",
                    details = "30-min active recall of previously completed subject topics (+5 pts).",
                    milestone = "",
                    sourcePage = 0,
                    isBuffer = false,
                    isRevision = true,
                    isSundayActivity = false,
                    isBooster = true,
                    status = if (isRevDone) TaskStatus.COMPLETED_ON_TIME else if (isFuture || isPast) TaskStatus.UPCOMING else TaskStatus.DUE_TODAY,
                    isCompleted = isRevDone,
                    completedAtTimestamp = revProgress?.completedAtTimestamp,
                    completionType = revProgress?.completionType,
                    userNotes = revProgress?.notes.orEmpty(),
                    pointsValue = 5,
                    isFuture = isFuture,
                    isPast = isPast
                )
            )

            // Everyday Booster: Problem Solving (5 pts, no backlog)
            val psId = "BOOSTER_PS_$dateStr"
            val psProgress = allProgress[psId]
            val isPsDone = psProgress?.isCompleted == true
            taskList.add(
                TaskItemUi(
                    id = psId,
                    originalDate = dateStr,
                    displayDate = dateStr,
                    dayOfWeek = targetDate.dayOfWeek.name.take(3),
                    slot = 4,
                    subject = "Problem Solving",
                    subjectCode = "PRB",
                    type = TaskType.BOOSTER_PROBLEM_SOLVING,
                    curNum = 1,
                    totalCount = 1,
                    title = "Daily Problem Solving",
                    details = "Solve 10-15 standard GATE practice problems (+5 pts).",
                    milestone = "",
                    sourcePage = 0,
                    isBuffer = false,
                    isRevision = false,
                    isSundayActivity = false,
                    isBooster = true,
                    status = if (isPsDone) TaskStatus.COMPLETED_ON_TIME else if (isFuture || isPast) TaskStatus.UPCOMING else TaskStatus.DUE_TODAY,
                    isCompleted = isPsDone,
                    completedAtTimestamp = psProgress?.completedAtTimestamp,
                    completionType = psProgress?.completionType,
                    userNotes = psProgress?.notes.orEmpty(),
                    pointsValue = 5,
                    isFuture = isFuture,
                    isPast = isPast
                )
            )
        }

        // Add recovery catch-up tasks assigned to this date (max 2 per Sunday, max 1 per subject)
        // Strictly sort in lecture sequence (curNum ASC)
        val recoveryItems = recoveriesForToday.mapNotNull { rec ->
            val origTask = dao.getTaskById(rec.taskId) ?: return@mapNotNull null
            val isRecCompleted = rec.isCompleted || allProgress[rec.taskId]?.isCompleted == true
            val trackNum = getTrackForSubject(origTask.subject)
            val earlierRecsOnSameDay = recoveriesForToday.takeWhile { it.id != rec.id }.count { otherRec ->
                val otherTask = dao.getTaskById(otherRec.taskId)
                getTrackForSubject(otherTask?.subject ?: "") == trackNum
            }

            val lecture = if (targetDate.isBefore(todayDate)) {
                val subjectTasks = dao.getTasksForSubject(origTask.subject).sortedBy { it.curNum }
                val recIdx = (origTask.curNum - 1).coerceIn(0, subjectTasks.lastIndex)
                subjectTasks[recIdx]
            } else {
                getSequentialLectureForTrack(
                    trackNum = trackNum,
                    targetDate = targetDate,
                    todayDate = todayDate,
                    targetSlot = 5,
                    isRecovery = true,
                    recoveryOrderIndex = earlierRecsOnSameDay,
                    currentSlotTaskId = rec.taskId,
                    allProgress = allProgress
                )
            }

            val taskToUse = origTask.copy(
                subject = lecture.subject,
                curNum = lecture.curNum,
                totalCount = lecture.totalCount,
                title = "${lecture.subject} — Lec ${lecture.curNum}/${lecture.totalCount}",
                details = "Rescheduled Sunday catch-up • Slot 5 • +10 points",
                milestone = lecture.milestone,
                sourcePage = lecture.sourcePage
            )

            val progress = allProgress[origTask.id]
            mapTaskToUi(taskToUse, progress, rec, todayDate, targetDate, isRecoveryAssignment = true)
        }.sortedWith(compareBy<TaskItemUi> { it.subject }.thenBy { it.curNum })
        taskList.addAll(recoveryItems)

        // Add User Custom Tasks for this date
        for (ct in customTasks) {
            taskList.add(
                TaskItemUi(
                    id = "CUSTOM_${ct.id}",
                    originalDate = ct.date,
                    displayDate = ct.date,
                    dayOfWeek = targetDate.dayOfWeek.name.take(3),
                    slot = 9,
                    subject = ct.subjectName,
                    subjectCode = ct.subjectCode,
                    type = TaskType.CUSTOM,
                    curNum = 1,
                    totalCount = 1,
                    title = ct.title,
                    details = ct.notes.ifEmpty { "User-defined custom study task (+${ct.pointsValue} pts)." },
                    milestone = "",
                    sourcePage = 0,
                    isBuffer = false,
                    isRevision = false,
                    isSundayActivity = isSunday,
                    isBooster = false,
                    isCustom = true,
                    status = if (ct.isCompleted) TaskStatus.COMPLETED_ON_TIME else if (isPast) TaskStatus.MISSED else if (isFuture) TaskStatus.UPCOMING else TaskStatus.DUE_TODAY,
                    isCompleted = ct.isCompleted,
                    completedAtTimestamp = ct.completedAtTimestamp,
                    completionType = if (ct.isCompleted) "ON_TIME" else null,
                    userNotes = ct.notes,
                    pointsValue = ct.pointsValue,
                    isFuture = isFuture,
                    isPast = isPast
                )
            )
        }

        taskList.distinctBy { it.id }
    }

    @Volatile
    private var track1CanonicalTasks: List<ScheduleTaskEntity>? = null
    @Volatile
    private var track2CanonicalTasks: List<ScheduleTaskEntity>? = null

    private suspend fun getTrack1CanonicalTasks(): List<ScheduleTaskEntity> {
        val cached = track1CanonicalTasks
        if (cached != null) return cached
        val all = dao.getAllScheduleTasks()
        val filtered = all.filter { (it.type == "RECORDED" || it.type == "LIVE") && it.slot == 1 }
            .sortedBy { it.date }
        track1CanonicalTasks = filtered
        return filtered
    }

    private suspend fun getTrack2CanonicalTasks(): List<ScheduleTaskEntity> {
        val cached = track2CanonicalTasks
        if (cached != null) return cached
        val all = dao.getAllScheduleTasks()
        val filtered = all.filter { (it.type == "RECORDED" || it.type == "LIVE") && it.slot == 2 }
            .sortedBy { it.date }
        track2CanonicalTasks = filtered
        return filtered
    }

    private fun getTrackForSubject(subject: String): Int {
        val clean = subject.lowercase()
        return if (clean.contains("digital") || clean.contains("dbms") || clean.contains("c prog") ||
            clean.contains("fundamental of c") || clean.contains("data struct") ||
            clean.contains("engg math") || clean.contains("linear algebra") ||
            clean.contains("probability") || clean.contains("calculus") ||
            clean.contains("general aptitude") || clean.contains("toc") || clean.contains("compiler")
        ) {
            1
        } else {
            2
        }
    }

    private suspend fun getSequentialLectureForTrack(
        trackNum: Int,
        targetDate: LocalDate,
        todayDate: LocalDate,
        targetSlot: Int,
        isRecovery: Boolean,
        recoveryOrderIndex: Int = 0,
        currentSlotTaskId: String? = null,
        allProgress: Map<String, TaskProgressEntity>
    ): ScheduleTaskEntity {
        val canonicalList = if (trackNum == 1) getTrack1CanonicalTasks() else getTrack2CanonicalTasks()
        if (canonicalList.isEmpty()) {
            return dao.getAllScheduleTasks().first()
        }

        // Historical past dates preserve original canonical lecture definition
        if (targetDate.isBefore(todayDate)) {
            if (currentSlotTaskId != null) {
                val found = canonicalList.find { it.id == currentSlotTaskId }
                if (found != null) return found
            }
        }

        // 1. Calculate how many lectures for this track were COMPLETED strictly before targetDate
        var completedBefore = 0
        for (t in canonicalList) {
            val codeId = "${SubjectRegistry.getCodeForSubject(t.subject)}_${t.curNum}"
            if (allProgress[t.id]?.isCompleted == true || allProgress[codeId]?.isCompleted == true) {
                completedBefore++
            }
        }

        val codeSlotId = currentSlotTaskId?.let { id ->
            dao.getTaskById(id)?.let { "${SubjectRegistry.getCodeForSubject(it.subject)}_${it.curNum}" }
        }
        val isThisSlotCompleted = currentSlotTaskId != null &&
            (allProgress[currentSlotTaskId]?.isCompleted == true || (codeSlotId != null && allProgress[codeSlotId]?.isCompleted == true))
        if (isThisSlotCompleted) {
            val idx = (completedBefore - 1).coerceIn(0, canonicalList.lastIndex)
            return canonicalList[idx]
        }

        // 2. Count uncompleted earlier scheduled slots between todayDate and targetDate
        val allRecoveries = dao.getActiveRecoveries()
        var uncompletedEarlier = 0
        var d = todayDate
        while (!d.isAfter(targetDate)) {
            val dStr = d.format(dateFormatter)
            val dTasks = dao.getTasksForDate(dStr).filter {
                it.slot == trackNum && (it.type == "RECORDED" || it.type == "LIVE")
            }
            for (dt in dTasks) {
                if (d == targetDate) {
                    if (isRecovery) {
                        if (allProgress[dt.id]?.isCompleted != true) {
                            uncompletedEarlier++
                        }
                    }
                } else {
                    if (allProgress[dt.id]?.isCompleted != true) {
                        uncompletedEarlier++
                    }
                }
            }

            // Recoveries on day d belonging to this track
            val dRecs = allRecoveries.filter { r ->
                val rTask = dao.getTaskById(r.taskId)
                val sub = rTask?.subject ?: ""
                r.recoveryDate == dStr &&
                    (r.status == "ACTIVE" || r.status == "MANUAL_CONFIRMED" || r.status == "COMPLETED") &&
                    getTrackForSubject(sub) == trackNum
            }

            if (d.isBefore(targetDate)) {
                for (r in dRecs) {
                    if (!r.isCompleted && allProgress[r.taskId]?.isCompleted != true) {
                        uncompletedEarlier++
                    }
                }
            } else if (d == targetDate && isRecovery) {
                uncompletedEarlier += recoveryOrderIndex
            }

            d = d.plusDays(1)
        }

        val idx = (completedBefore + uncompletedEarlier).coerceIn(0, canonicalList.lastIndex)
        return canonicalList[idx]
    }

    private suspend fun getSequentialTaskForDate(
        baseTask: ScheduleTaskEntity,
        targetDate: LocalDate,
        todayDate: LocalDate,
        dateStr: String,
        allProgress: Map<String, TaskProgressEntity>
    ): ScheduleTaskEntity {
        // If targetDate is in the past, or special task type, preserve historical definition
        if (targetDate.isBefore(todayDate) || baseTask.isSundayActivity || baseTask.isBuffer || baseTask.isRevision) {
            return baseTask
        }
        val trackNum = if (baseTask.slot == 1 || baseTask.slot == 2) baseTask.slot else getTrackForSubject(baseTask.subject)
        val lecture = getSequentialLectureForTrack(
            trackNum = trackNum,
            targetDate = targetDate,
            todayDate = todayDate,
            targetSlot = baseTask.slot,
            isRecovery = false,
            recoveryOrderIndex = 0,
            currentSlotTaskId = baseTask.id,
            allProgress = allProgress
        )

        // Keep baseTask.id, baseTask.date, baseTask.slot!
        // Adapt subject, curNum, totalCount, title, details, milestone, and sourcePage from the continuous track stream
        return baseTask.copy(
            subject = lecture.subject,
            curNum = lecture.curNum,
            totalCount = lecture.totalCount,
            title = lecture.title,
            details = lecture.details,
            milestone = lecture.milestone,
            sourcePage = lecture.sourcePage
        )
    }

    private fun mapTaskToUi(
        task: ScheduleTaskEntity,
        progress: TaskProgressEntity?,
        recovery: RecoveryAssignmentEntity?,
        todayDate: LocalDate,
        targetDate: LocalDate,
        isRecoveryAssignment: Boolean = false
    ): TaskItemUi {
        val isCompleted = progress?.isCompleted == true
        val taskOrigDate = LocalDate.parse(task.date, dateFormatter)
        val isFuture = targetDate.isAfter(todayDate)
        val isPast = targetDate.isBefore(todayDate)

        val status = when {
            isCompleted -> {
                if (progress?.completionType == "ON_TIME") {
                    TaskStatus.COMPLETED_ON_TIME
                } else {
                    TaskStatus.COMPLETED_LATE
                }
            }
            recovery != null && recovery.status == "MANUAL_CONFIRMED" && !isRecoveryAssignment -> {
                TaskStatus.RECOVERY_SCHEDULED
            }
            isPast -> {
                TaskStatus.MISSED
            }
            !isRecoveryAssignment && taskOrigDate.isBefore(todayDate) -> {
                TaskStatus.MISSED
            }
            targetDate == todayDate -> {
                TaskStatus.DUE_TODAY
            }
            else -> {
                TaskStatus.UPCOMING
            }
        }

        val typeEnum = try {
            TaskType.valueOf(task.type)
        } catch (e: Exception) {
            TaskType.OTHER
        }

        // Strip any existing "(Catch-Up)" from title so it is NEVER duplicated
        val cleanTitle = task.title.replace(Regex("\\s*\\(Catch-Up\\)"), "").trim()
        val finalTitle = if (isRecoveryAssignment) "$cleanTitle (Catch-Up)" else cleanTitle

        val finalDetails = if (isRecoveryAssignment) {
            if (task.details.contains("Rescheduled Sunday catch-up")) {
                task.details
            } else {
                "Rescheduled from ${task.date} for Sunday recovery."
            }
        } else {
            task.details
        }

        return TaskItemUi(
            id = if (isRecoveryAssignment) "REC_${task.id}" else task.id,
            originalDate = task.date,
            displayDate = if (isRecoveryAssignment && recovery != null) recovery.recoveryDate else task.date,
            dayOfWeek = if (isRecoveryAssignment && recovery != null) "Sun" else task.dayOfWeek,
            slot = if (isRecoveryAssignment) 5 else task.slot,
            subject = task.subject,
            subjectCode = SubjectRegistry.getCodeForSubject(task.subject),
            type = typeEnum,
            curNum = task.curNum,
            totalCount = task.totalCount,
            title = finalTitle,
            details = finalDetails,
            milestone = if (isRecoveryAssignment) "" else task.milestone,
            sourcePage = task.sourcePage,
            isBuffer = task.isBuffer,
            isRevision = task.isRevision,
            isSundayActivity = task.isSundayActivity,
            isBooster = false,
            isCustom = false,
            status = status,
            isCompleted = isCompleted,
            completedAtTimestamp = progress?.completedAtTimestamp,
            completionType = progress?.completionType,
            userNotes = progress?.notes.orEmpty(),
            isAssignedForRecovery = isRecoveryAssignment,
            recoveryDate = recovery?.recoveryDate,
            recoveryPriority = recovery?.priorityLevel,
            pointsValue = 10,
            isFuture = isFuture,
            isPast = isPast
        )
    }

    suspend fun markTaskComplete(
        taskId: String,
        isCompleted: Boolean,
        currentDateStr: String,
        notes: String = ""
    ) = withContext(Dispatchers.IO) {
        val today = LocalDate.parse(currentDateStr, dateFormatter)
        val cleanTaskId = taskId.removePrefix("REC_")
        val isRecoveryTask = taskId.startsWith("REC_")

        // 1. Handle Custom Tasks
        if (cleanTaskId.startsWith("CUSTOM_")) {
            val customId = cleanTaskId.removePrefix("CUSTOM_").toLongOrNull() ?: return@withContext
            val customTask = dao.getCustomTaskById(customId) ?: return@withContext
            val taskDate = LocalDate.parse(customTask.date, dateFormatter)
            // Strict rule: Marking complete is restricted to scheduled day, but unchecking is always permitted
            if (taskDate != today && isCompleted) return@withContext

            com.gatecse.companion.sync.SyncRepository.registerLocalMutation("CUSTOM_$customId")
            dao.updateCustomTask(
                customTask.copy(
                    isCompleted = isCompleted,
                    completedAtTimestamp = if (isCompleted) System.currentTimeMillis() else null
                )
            )
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try { syncRepository.performBidirectionalSync() } catch (_: Exception) {}
            }
            return@withContext
        }

        // 2. Handle Daily Booster Tasks & Sunday Tasks
        if (cleanTaskId.startsWith("BOOSTER_") || cleanTaskId.startsWith("SUNDAY_")) {
            val dateStr = cleanTaskId.substringAfterLast("_")
            val boosterDate = LocalDate.parse(dateStr, dateFormatter)
            // Strict rule: Marking complete is restricted to today, but unchecking is always permitted
            if (boosterDate != today && isCompleted) return@withContext

            // If Sunday test is unchecked before 12 AM (same day), remove the recorded test assessment
            if (cleanTaskId.startsWith("SUNDAY_TEST_") && !isCompleted && boosterDate == today) {
                dao.deleteSundayAssessmentForDate(dateStr)
            }

            com.gatecse.companion.sync.SyncRepository.registerLocalMutation(cleanTaskId)
            dao.insertOrUpdateProgress(
                TaskProgressEntity(
                    taskId = cleanTaskId,
                    originalDate = dateStr,
                    isCompleted = isCompleted,
                    completedAtTimestamp = if (isCompleted) System.currentTimeMillis() else null,
                    completionType = if (isCompleted) "ON_TIME" else null,
                    notes = notes
                )
            )
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try { syncRepository.performBidirectionalSync() } catch (_: Exception) {}
            }
            return@withContext
        }

        // 3. Regular Scheduled Tasks & Recovery Tasks
        val task = dao.getTaskById(cleanTaskId) ?: return@withContext
        val recovery = dao.getRecoveryForTask(cleanTaskId)
        val origDate = LocalDate.parse(task.date, dateFormatter)

        // Strict rule:
        // Marking complete can ONLY be done on today's regular scheduled task or today's assigned recovery task.
        // Uncompleting (undoing) is ALWAYS allowed for any completed task.
        val isTodayRecovery = isRecoveryTask && recovery != null && recovery.recoveryDate == currentDateStr
        val isTodayRegularTask = !isRecoveryTask && origDate == today

        if (isCompleted && !isTodayRegularTask && !isTodayRecovery) {
            // Strictly reject marking future or unrescheduled past tasks complete!
            return@withContext
        }

        com.gatecse.companion.sync.SyncRepository.registerLocalMutation(cleanTaskId)

        if (isCompleted) {
            val completionType = if (isTodayRecovery) "RECOVERY" else "ON_TIME"

            val progress = TaskProgressEntity(
                taskId = cleanTaskId,
                originalDate = if (isTodayRecovery) task.date else currentDateStr,
                isCompleted = true,
                completedAtTimestamp = System.currentTimeMillis(),
                completionType = completionType,
                notes = notes
            )
            dao.insertOrUpdateProgress(progress)

            if (recovery != null && isTodayRecovery) {
                dao.updateRecoveryAssignment(
                    recovery.copy(
                        isCompleted = true,
                        completedAtTimestamp = System.currentTimeMillis(),
                        status = "COMPLETED"
                    )
                )
            }

            // Re-reconcile remaining recoveries so backlog updates immediately
            reconcileRecovery(currentDateStr)

            // Evaluate achievements
            val allTasks = dao.getAllScheduleTasks()
            val allProgress = dao.getAllProgress()
            val testsCount = dao.getAllAssessments().size
            val backlog = allTasks.count { t ->
                val d = LocalDate.parse(t.date, dateFormatter)
                d.isBefore(today) && allProgress.none { it.taskId == t.id && it.isCompleted }
            }
            achievementManager.evaluateAchievements(allTasks, allProgress, backlog, testsCount)

        } else {
            // Explicitly persist uncompleted state (isCompleted = false) so cloud sync broadcasts it
            dao.insertOrUpdateProgress(
                TaskProgressEntity(
                    taskId = cleanTaskId,
                    originalDate = task.date,
                    isCompleted = false,
                    completedAtTimestamp = null,
                    completionType = null,
                    notes = notes
                )
            )

            if (recovery != null) {
                dao.updateRecoveryAssignment(
                    recovery.copy(
                        isCompleted = false,
                        completedAtTimestamp = null,
                        status = "MANUAL_CONFIRMED"
                    )
                )
            }
            reconcileRecovery(currentDateStr)

            val allTasks = dao.getAllScheduleTasks()
            val allProgress = dao.getAllProgress()
            val testsCount = dao.getAllAssessments().size
            val backlog = allTasks.count { t ->
                val d = LocalDate.parse(t.date, dateFormatter)
                d.isBefore(today) && allProgress.none { it.taskId == t.id && it.isCompleted }
            }
            achievementManager.evaluateAchievements(allTasks, allProgress, backlog, testsCount)
        }

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try { syncRepository.performBidirectionalSync() } catch (_: Exception) {}
        }
    }

    suspend fun getCalendarMonthDays(yearMonth: YearMonth, currentDateStr: String): List<CalendarDayUi> = withContext(Dispatchers.IO) {
        val startDate = yearMonth.atDay(1).format(dateFormatter)
        val endDate = yearMonth.atEndOfMonth().format(dateFormatter)
        val tasks = dao.getTasksForDateRange(startDate, endDate)
        val allProgress = dao.getAllProgress().associateBy { it.taskId }
        val activeRecoveries = dao.getActiveRecoveries().groupBy { it.recoveryDate }
        val allCustom = dao.getAllCustomTasks().groupBy { it.date }

        val today = LocalDate.parse(currentDateStr, dateFormatter)
        val tasksByDate = tasks.groupBy { it.date }

        val result = mutableListOf<CalendarDayUi>()
        var dayPointer = yearMonth.atDay(1)
        while (!dayPointer.isAfter(yearMonth.atEndOfMonth())) {
            val dateStr = dayPointer.format(dateFormatter)
            val dayTasks = tasksByDate[dateStr].orEmpty()
            val recList = activeRecoveries[dateStr].orEmpty()
            val customList = allCustom[dateStr].orEmpty()
            val isSunday = dayPointer.dayOfWeek.name == "SUNDAY"

            val completed = dayTasks.count { allProgress[it.id]?.isCompleted == true }
            val isPast = dayPointer.isBefore(today)
            val missed = if (isPast && !isSunday) (dayTasks.size - completed).coerceAtLeast(0) else 0

            val milestoneText = dayTasks.mapNotNull { it.milestone.ifEmpty { null } }.firstOrNull().orEmpty()

            var dPts = 0
            if (isSunday) {
                if (allProgress["SUNDAY_TEST_$dateStr"]?.isCompleted == true) dPts += 20
            } else {
                for (t in dayTasks) {
                    if (allProgress[t.id]?.isCompleted == true) {
                        dPts += 10
                    } else if (isPast) {
                        dPts -= 2
                    }
                }
            }
            if (allProgress["BOOSTER_REV_$dateStr"]?.isCompleted == true) dPts += 5
            if (allProgress["BOOSTER_PS_$dateStr"]?.isCompleted == true) dPts += 5
            for (c in customList) {
                if (c.isCompleted) dPts += c.pointsValue
            }

            // Sunday tasks count = 3 standard (Test, Rev, PS) + recoveries + custom
            val totalTaskCount = if (isSunday) 3 + recList.size + customList.size else dayTasks.size + 2 + customList.size
            val completedTaskCount = if (isSunday) {
                (if (allProgress["SUNDAY_TEST_$dateStr"]?.isCompleted == true) 1 else 0) +
                        (if (allProgress["BOOSTER_REV_$dateStr"]?.isCompleted == true) 1 else 0) +
                        (if (allProgress["BOOSTER_PS_$dateStr"]?.isCompleted == true) 1 else 0) +
                        recList.count { allProgress[it.taskId]?.isCompleted == true } +
                        customList.count { it.isCompleted }
            } else {
                completed + (if (allProgress["BOOSTER_REV_$dateStr"]?.isCompleted == true) 1 else 0) +
                        (if (allProgress["BOOSTER_PS_$dateStr"]?.isCompleted == true) 1 else 0) +
                        customList.count { it.isCompleted }
            }

            result.add(
                CalendarDayUi(
                    date = dateStr,
                    dayOfWeek = dayPointer.dayOfWeek.name.take(3),
                    isSunday = isSunday,
                    isBuffer = dayTasks.any { it.isBuffer },
                    isRevision = dayTasks.any { it.isRevision },
                    milestone = if (isSunday) "" else milestoneText,
                    totalTasks = totalTaskCount,
                    completedTasks = completedTaskCount,
                    missedTasks = missed,
                    recoveryTasks = recList.size,
                    dailyPoints = dPts.coerceAtLeast(0),
                    isToday = dayPointer == today,
                    isPast = isPast,
                    isFuture = dayPointer.isAfter(today)
                )
            )
            dayPointer = dayPointer.plusDays(1)
        }
        result
    }

    suspend fun getAllSubjectsProgress(): List<SubjectUi> = withContext(Dispatchers.IO) {
        val allTasks = dao.getAllScheduleTasks().filter { !it.isSundayActivity }
        val allProgress = dao.getAllProgress().associateBy { it.taskId }

        val grouped = allTasks.groupBy { it.subject }
        val subjectsList = mutableListOf<SubjectUi>()

        for ((subjectName, sTasks) in grouped) {
            val code = SubjectRegistry.getCodeForSubject(subjectName)
            val info = SubjectRegistry.getSubjectInfoByCode(code)

            val prior = info?.priorCompletedLectures ?: 0
            val total = info?.totalLectures ?: sTasks.maxOfOrNull { it.totalCount } ?: sTasks.size
            val scheduleCompleted = sTasks.count { allProgress[it.id]?.isCompleted == true }

            val totalCompleted = (prior + scheduleCompleted).coerceAtMost(total)
            val remaining = (total - totalCompleted).coerceAtLeast(0)
            val percentage = if (total > 0) ((totalCompleted.toDouble() / total.toDouble()) * 100).toInt() else 0

            val lateCount = sTasks.count {
                val p = allProgress[it.id]
                p?.isCompleted == true && p.completionType != "ON_TIME"
            }
            val missedCount = sTasks.count {
                allProgress[it.id]?.isCompleted != true
            }

            val nextTask = sTasks.firstOrNull { allProgress[it.id]?.isCompleted != true }
            val milestone = sTasks.firstOrNull { it.milestone.isNotEmpty() }?.milestone

            val typeEnum = try {
                TaskType.valueOf(sTasks.first().type)
            } catch (e: Exception) {
                TaskType.OTHER
            }

            val isFullyCompleted = totalCompleted >= total && total > 0

            subjectsList.add(
                SubjectUi(
                    code = code,
                    name = subjectName,
                    type = typeEnum,
                    priorCompletedLectures = prior,
                    totalLectures = total,
                    completedLectures = totalCompleted,
                    remainingLectures = remaining,
                    completionPercentage = percentage,
                    missedLectures = missedCount,
                    lateCompletedLectures = lateCount,
                    nextLecture = nextTask?.title,
                    completionMilestone = milestone,
                    isFullyCompleted = isFullyCompleted
                )
            )
        }

        // Sort by completion percentage descending, then code
        subjectsList.sortedWith(compareByDescending<SubjectUi> { it.completionPercentage }.thenBy { it.code })
    }

    suspend fun getPreparationAdherence(currentDateStr: String): PreparationAdherenceUi = withContext(Dispatchers.IO) {
        val allTasks = dao.getAllScheduleTasks()
        val allProgress = dao.getAllProgress()
        val activeRecoveries = dao.getActiveRecoveries()
        val allCustom = dao.getAllCustomTasks()
        AnalyticsEngine.calculateAdherenceAndForecast(
            currentDateStr = currentDateStr,
            allTasks = allTasks,
            allProgress = allProgress,
            activeRecoveries = activeRecoveries,
            allCustomTasks = allCustom
        )
    }

    // Assessments
    fun getAllAssessmentsFlow(): Flow<List<AssessmentResultEntity>> = dao.getAllAssessmentsFlow()
    suspend fun recordAssessment(assessment: AssessmentResultEntity) = withContext(Dispatchers.IO) {
        dao.insertAssessment(assessment)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try { syncRepository.performBidirectionalSync() } catch (_: Exception) {}
        }
    }
    suspend fun deleteAssessment(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteAssessment(id)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try { syncRepository.performBidirectionalSync() } catch (_: Exception) {}
        }
    }

    // Daily Practice Problems (DPP)
    fun getAllDppFlow(): Flow<List<DppRecordEntity>> = dao.getAllDppFlow()
    suspend fun getAllDpp(): List<DppRecordEntity> = withContext(Dispatchers.IO) {
        dao.getAllDpp()
    }
    suspend fun recordDpp(dpp: DppRecordEntity) = withContext(Dispatchers.IO) {
        dao.insertDpp(dpp)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try { syncRepository.performBidirectionalSync() } catch (_: Exception) {}
        }
    }
    suspend fun deleteDpp(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteDpp(id)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try { syncRepository.performBidirectionalSync() } catch (_: Exception) {}
        }
    }

    // Custom Tasks
    suspend fun addCustomTask(
        date: String,
        title: String,
        subjectCode: String = "GATE",
        notes: String = "",
        pointsValue: Int = 5
    ) = withContext(Dispatchers.IO) {
        val subjectInfo = SubjectRegistry.getSubjectInfoByCode(subjectCode)
        val task = CustomTaskEntity(
            date = date,
            title = title,
            subjectCode = subjectCode,
            subjectName = subjectInfo?.name ?: "Custom Study",
            notes = notes,
            pointsValue = pointsValue
        )
        dao.insertCustomTask(task)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try { syncRepository.performBidirectionalSync() } catch (_: Exception) {}
        }
    }

    suspend fun deleteCustomTask(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteCustomTask(id)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try { syncRepository.performBidirectionalSync() } catch (_: Exception) {}
        }
    }

    // Achievements
    fun getAllAchievementsFlow(): Flow<List<AchievementEntity>> = dao.getAllAchievementsFlow()

    // Export & Backup
    suspend fun exportDataToJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        val progressList = dao.getAllProgress()
        val pArray = JSONArray()
        for (p in progressList) {
            val obj = JSONObject().apply {
                put("taskId", p.taskId)
                put("originalDate", p.originalDate)
                put("isCompleted", p.isCompleted)
                put("completedAtTimestamp", p.completedAtTimestamp ?: 0L)
                put("completionType", p.completionType ?: "")
                put("notes", p.notes)
            }
            pArray.put(obj)
        }
        root.put("progress", pArray)

        val assessments = dao.getAllAssessments()
        val aArray = JSONArray()
        for (a in assessments) {
            val obj = JSONObject().apply {
                put("testDate", a.testDate)
                put("testName", a.testName)
                put("subjectName", a.subjectName)
                put("marksObtained", a.marksObtained)
                put("maxMarks", a.maxMarks)
                put("percentage", a.percentage)
                put("mistakesAnalysis", a.mistakesAnalysis)
            }
            aArray.put(obj)
        }
        root.put("assessments", aArray)
        root.toString(2)
    }

    suspend fun restoreDataFromJson(jsonStr: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonStr)
            val pArray = root.optJSONArray("progress")
            if (pArray != null) {
                for (i in 0 until pArray.length()) {
                    val obj = pArray.getJSONObject(i)
                    dao.insertOrUpdateProgress(
                        TaskProgressEntity(
                            taskId = obj.getString("taskId"),
                            originalDate = obj.getString("originalDate"),
                            isCompleted = obj.getBoolean("isCompleted"),
                            completedAtTimestamp = obj.optLong("completedAtTimestamp"),
                            completionType = obj.optString("completionType"),
                            notes = obj.optString("notes")
                        )
                    )
                }
            }
            val aArray = root.optJSONArray("assessments")
            if (aArray != null) {
                for (i in 0 until aArray.length()) {
                    val obj = aArray.getJSONObject(i)
                    dao.insertAssessment(
                        AssessmentResultEntity(
                            testDate = obj.getString("testDate"),
                            testName = obj.getString("testName"),
                            subjectName = obj.getString("subjectName"),
                            marksObtained = obj.getDouble("marksObtained"),
                            maxMarks = obj.getDouble("maxMarks"),
                            percentage = obj.getDouble("percentage"),
                            mistakesAnalysis = obj.optString("mistakesAnalysis")
                        )
                    )
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
