package com.gatecse.companion.recovery

import com.gatecse.companion.data.ScheduleDao
import com.gatecse.companion.model.RecoveryAssignmentEntity
import com.gatecse.companion.model.ScheduleTaskEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RecoveryEngine(private val dao: ScheduleDao) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Reconciles recovery assignments.
     * STRICT USER REQUIREMENT:
     * NO AUTOMATIC RESCHEDULING.
     * Backlogs are ONLY manually rescheduled by the user.
     * This function only cleans up completed recoveries and removes expired ones.
     */
    suspend fun reconcileRecoveryAssignments(
        todayDateStr: String,
        configuredDailyLimit: Int = 2
    ): List<RecoveryAssignmentEntity> {
        val today = LocalDate.parse(todayDateStr, dateFormatter)
        val allProgress = dao.getAllProgress().associateBy { it.taskId }
        val existingActiveRecoveries = dao.getActiveRecoveries()

        for (rec in existingActiveRecoveries) {
            // Keep completed recoveries intact with COMPLETED status
            if (allProgress[rec.taskId]?.isCompleted == true || rec.isCompleted) {
                if (rec.status != "COMPLETED" || !rec.isCompleted) {
                    dao.updateRecoveryAssignment(rec.copy(isCompleted = true, status = "COMPLETED"))
                }
                continue
            }

            // If a previously scheduled recovery date is now strictly in the past and was not completed,
            // mark it EXPIRED so that the underlying core task enters unrescheduled backlog status for user re-assignment.
            val recDate = try {
                LocalDate.parse(rec.recoveryDate, dateFormatter)
            } catch (e: Exception) {
                null
            }
            if (recDate != null && recDate.isBefore(today)) {
                dao.updateRecoveryAssignment(rec.copy(status = "EXPIRED"))
            }
        }

        return dao.getActiveRecoveries()
    }
}
