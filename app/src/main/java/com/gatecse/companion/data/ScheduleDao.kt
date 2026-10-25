package com.gatecse.companion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gatecse.companion.model.AchievementEntity
import com.gatecse.companion.model.AssessmentResultEntity
import com.gatecse.companion.model.CustomTaskEntity
import com.gatecse.companion.model.DppRecordEntity
import com.gatecse.companion.model.RecoveryAssignmentEntity
import com.gatecse.companion.model.ScheduleTaskEntity
import com.gatecse.companion.model.TaskProgressEntity
import com.gatecse.companion.model.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    // --- Schedule Tasks ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScheduleTasks(tasks: List<ScheduleTaskEntity>)

    @Query("SELECT * FROM schedule_tasks ORDER BY date ASC, slot ASC")
    fun getAllScheduleTasksFlow(): Flow<List<ScheduleTaskEntity>>

    @Query("SELECT * FROM schedule_tasks ORDER BY date ASC, slot ASC")
    suspend fun getAllScheduleTasks(): List<ScheduleTaskEntity>

    @Query("SELECT * FROM schedule_tasks WHERE date = :date ORDER BY slot ASC")
    fun getTasksForDateFlow(date: String): Flow<List<ScheduleTaskEntity>>

    @Query("SELECT * FROM schedule_tasks WHERE date = :date ORDER BY slot ASC")
    suspend fun getTasksForDate(date: String): List<ScheduleTaskEntity>

    @Query("SELECT * FROM schedule_tasks WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, slot ASC")
    suspend fun getTasksForDateRange(startDate: String, endDate: String): List<ScheduleTaskEntity>

    @Query("SELECT * FROM schedule_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): ScheduleTaskEntity?

    @Query("SELECT * FROM schedule_tasks WHERE subject = :subject ORDER BY curNum ASC, date ASC")
    suspend fun getTasksForSubject(subject: String): List<ScheduleTaskEntity>

    @Query("SELECT DISTINCT subject FROM schedule_tasks WHERE isSundayActivity = 0 ORDER BY subject ASC")
    suspend fun getDistinctSubjects(): List<String>

    @Query("SELECT COUNT(*) FROM schedule_tasks")
    suspend fun getScheduleTaskCount(): Int

    // --- Task Progress (Explicit completion) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: TaskProgressEntity)

    @Query("SELECT * FROM task_progress WHERE taskId = :taskId LIMIT 1")
    suspend fun getProgressForTask(taskId: String): TaskProgressEntity?

    @Query("SELECT * FROM task_progress WHERE taskId = :taskId LIMIT 1")
    fun getProgressForTaskFlow(taskId: String): Flow<TaskProgressEntity?>

    @Query("SELECT * FROM task_progress")
    fun getAllProgressFlow(): Flow<List<TaskProgressEntity>>

    @Query("SELECT * FROM task_progress")
    suspend fun getAllProgress(): List<TaskProgressEntity>

    @Query("DELETE FROM task_progress WHERE taskId = :taskId")
    suspend fun deleteProgressForTask(taskId: String)

    // --- Recovery Assignments ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecoveryAssignment(recovery: RecoveryAssignmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecoveryAssignments(recoveries: List<RecoveryAssignmentEntity>)

    @Update
    suspend fun updateRecoveryAssignment(recovery: RecoveryAssignmentEntity)

    @Query("SELECT * FROM recovery_assignments WHERE status IN ('ACTIVE', 'MANUAL_CONFIRMED') AND isCompleted = 0 ORDER BY recoveryDate ASC, priorityLevel ASC")
    fun getActiveRecoveriesFlow(): Flow<List<RecoveryAssignmentEntity>>

    @Query("SELECT * FROM recovery_assignments WHERE status IN ('ACTIVE', 'MANUAL_CONFIRMED') AND isCompleted = 0 ORDER BY recoveryDate ASC, priorityLevel ASC")
    suspend fun getActiveRecoveries(): List<RecoveryAssignmentEntity>

    @Query("SELECT * FROM recovery_assignments WHERE recoveryDate = :date AND status IN ('ACTIVE', 'MANUAL_CONFIRMED', 'COMPLETED')")
    suspend fun getRecoveriesForDate(date: String): List<RecoveryAssignmentEntity>

    @Query("SELECT * FROM recovery_assignments WHERE recoveryDate = :date AND status IN ('ACTIVE', 'MANUAL_CONFIRMED', 'COMPLETED')")
    fun getRecoveriesForDateFlow(date: String): Flow<List<RecoveryAssignmentEntity>>

    @Query("SELECT * FROM recovery_assignments WHERE taskId = :taskId LIMIT 1")
    suspend fun getRecoveryForTask(taskId: String): RecoveryAssignmentEntity?

    @Query("DELETE FROM recovery_assignments WHERE taskId = :taskId")
    suspend fun deleteRecoveryForTask(taskId: String)

    // --- Assessment Results ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessment(assessment: AssessmentResultEntity): Long

    @Query("SELECT * FROM assessment_results ORDER BY testDate DESC, id DESC")
    fun getAllAssessmentsFlow(): Flow<List<AssessmentResultEntity>>

    @Query("SELECT * FROM assessment_results ORDER BY testDate DESC, id DESC")
    suspend fun getAllAssessments(): List<AssessmentResultEntity>

    @Query("SELECT * FROM assessment_results WHERE subjectName = :subject ORDER BY testDate DESC")
    suspend fun getAssessmentsForSubject(subject: String): List<AssessmentResultEntity>

    @Query("DELETE FROM assessment_results WHERE id = :id")
    suspend fun deleteAssessment(id: Long)

    @Query("DELETE FROM assessment_results WHERE testDate = :date AND (testName LIKE '%Sunday%' OR testName LIKE '%Weekly%')")
    suspend fun deleteSundayAssessmentForDate(date: String)

    // --- Daily Practice Problems (DPP) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDpp(dpp: DppRecordEntity): Long

    @Query("SELECT * FROM dpp_records ORDER BY date DESC, id DESC")
    fun getAllDppFlow(): Flow<List<DppRecordEntity>>

    @Query("SELECT * FROM dpp_records ORDER BY date DESC, id DESC")
    suspend fun getAllDpp(): List<DppRecordEntity>

    @Query("SELECT * FROM dpp_records WHERE subjectCode = :subjectCode ORDER BY date DESC")
    suspend fun getDppForSubject(subjectCode: String): List<DppRecordEntity>

    @Query("DELETE FROM dpp_records WHERE id = :id")
    suspend fun deleteDpp(id: Long)

    // --- Achievements ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAchievements(achievements: List<AchievementEntity>)

    @Update
    suspend fun updateAchievement(achievement: AchievementEntity)

    @Query("SELECT * FROM achievements ORDER BY isUnlocked DESC, code ASC")
    fun getAllAchievementsFlow(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun getAllAchievementsList(): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE isUnlocked = 1")
    suspend fun getUnlockedAchievements(): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE code = :code LIMIT 1")
    suspend fun getAchievementByCode(code: String): AchievementEntity?

    @Query("DELETE FROM achievements WHERE code NOT IN (:allowedCodes)")
    suspend fun pruneExtraAchievements(allowedCodes: List<String>)

    // --- User Settings ---

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun getUserSettingsFlow(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun getUserSettings(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUserSettings(settings: UserSettingsEntity)

    // --- Custom Tasks ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomTask(task: CustomTaskEntity): Long

    @Query("SELECT * FROM custom_tasks WHERE date = :date ORDER BY id ASC")
    suspend fun getCustomTasksForDate(date: String): List<CustomTaskEntity>

    @Query("SELECT * FROM custom_tasks WHERE date = :date ORDER BY id ASC")
    fun getCustomTasksForDateFlow(date: String): Flow<List<CustomTaskEntity>>

    @Query("SELECT * FROM custom_tasks")
    suspend fun getAllCustomTasks(): List<CustomTaskEntity>

    @Query("SELECT * FROM custom_tasks WHERE id = :id LIMIT 1")
    suspend fun getCustomTaskById(id: Long): CustomTaskEntity?

    @Update
    suspend fun updateCustomTask(task: CustomTaskEntity)

    @Query("DELETE FROM custom_tasks WHERE id = :id")
    suspend fun deleteCustomTask(id: Long)
}
