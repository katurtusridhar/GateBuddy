package com.gatecse.companion.sync

import android.content.Context
import com.gatecse.companion.data.ScheduleDao
import com.gatecse.companion.model.AssessmentResultEntity
import com.gatecse.companion.model.CustomTaskEntity
import com.gatecse.companion.model.RecoveryAssignmentEntity
import com.gatecse.companion.model.TaskProgressEntity
import com.gatecse.companion.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class SyncRepository(
    private val dao: ScheduleDao,
    private val context: Context? = null,
    private val apiClient: SyncApiClient = SyncApiClient(context = context)
) {

    companion object {
        // Track recent local explicit mutations so they are never reverted by stale server responses
        private val localMutations = ConcurrentHashMap<String, Long>()

        fun registerLocalMutation(taskId: String) {
            localMutations[taskId] = System.currentTimeMillis()
        }
    }

    suspend fun performBidirectionalSync(): SyncResult = withContext(Dispatchers.IO) {
        try {
            if (context != null && !SyncApiClient.hasCredentials(context)) {
                val isExpired = SyncApiClient.isSessionExpired(context)
                val msg = if (isExpired) "Session expired. Re-authentication required in Settings." else "Sync not configured. Please sign in via Settings."
                return@withContext SyncResult(success = false, message = msg)
            }

            // PHASE 1: Gather current local Room records
            val allProgress = dao.getAllProgress()
            val allRecoveries = dao.getActiveRecoveries()
            val allAssessments = dao.getAllAssessments()
            val allDpp = dao.getAllDpp()
            val allCustomTasks = dao.getAllCustomTasks()
            val settings = dao.getUserSettings()

            // Build sync JSON payload
            val payload = JSONObject()
            payload.put("lastSyncTimestamp", System.currentTimeMillis())

            // Task Progress
            val progressArray = JSONArray()
            val now = System.currentTimeMillis()
            for (p in allProgress) {
                val mutationTime = localMutations[p.taskId]
                val isExplicit = mutationTime != null && (now - mutationTime < 30000L)
                val obj = JSONObject()
                obj.put("taskId", p.taskId)
                obj.put("originalDate", p.originalDate)
                obj.put("isCompleted", p.isCompleted)
                obj.put("completedAtTimestamp", p.completedAtTimestamp ?: JSONObject.NULL)
                obj.put("completionType", p.completionType ?: JSONObject.NULL)
                obj.put("notes", p.notes)
                obj.put("isExplicitMutation", isExplicit)
                obj.put("lastMutatedTimestamp", mutationTime ?: (p.completedAtTimestamp ?: 0L))
                progressArray.put(obj)
            }
            payload.put("progress", progressArray)

            // Recoveries
            val recoveriesArray = JSONArray()
            for (r in allRecoveries) {
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("taskId", r.taskId)
                obj.put("originalDate", r.originalDate)
                obj.put("recoveryDate", r.recoveryDate)
                obj.put("assignedAtTimestamp", r.assignedAtTimestamp)
                obj.put("isCompleted", r.isCompleted)
                obj.put("priorityLevel", r.priorityLevel)
                obj.put("status", r.status)
                recoveriesArray.put(obj)
            }
            payload.put("recoveries", recoveriesArray)

            // Assessments
            val assessmentsArray = JSONArray()
            for (a in allAssessments) {
                val obj = JSONObject()
                obj.put("id", a.id)
                obj.put("testDate", a.testDate)
                obj.put("testName", a.testName)
                obj.put("subjectName", a.subjectName)
                obj.put("marksObtained", a.marksObtained)
                obj.put("maxMarks", a.maxMarks)
                obj.put("percentage", a.percentage)
                obj.put("topic", a.topic)
                obj.put("mistakesAnalysis", a.mistakesAnalysis)
                obj.put("followUpTasks", a.followUpTasks)
                assessmentsArray.put(obj)
            }
            payload.put("assessments", assessmentsArray)

            // DPP Records
            val dppArray = JSONArray()
            for (d in allDpp) {
                val obj = JSONObject()
                obj.put("id", d.id)
                obj.put("date", d.date)
                obj.put("subjectCode", d.subjectCode)
                obj.put("subjectName", d.subjectName)
                obj.put("topic", d.topic)
                obj.put("totalQuestions", d.totalQuestions)
                obj.put("questionsCorrect", d.questionsCorrect)
                obj.put("accuracyPercentage", d.accuracyPercentage)
                obj.put("notes", d.notes)
                obj.put("createdAtTimestamp", d.createdAtTimestamp)
                dppArray.put(obj)
            }
            payload.put("dppRecords", dppArray)

            // Custom Tasks
            val customArray = JSONArray()
            for (c in allCustomTasks) {
                val obj = JSONObject()
                obj.put("id", c.id)
                obj.put("date", c.date)
                obj.put("title", c.title)
                obj.put("subjectCode", c.subjectCode)
                obj.put("subjectName", c.subjectName)
                obj.put("isCompleted", c.isCompleted)
                obj.put("notes", c.notes)
                obj.put("pointsValue", c.pointsValue)
                customArray.put(obj)
            }
            payload.put("customTasks", customArray)

            // Settings
            if (settings != null) {
                val setObj = JSONObject()
                setObj.put("targetExamName", settings.targetExamName)
                setObj.put("targetExamDate", settings.targetExamDate ?: JSONObject.NULL)
                setObj.put("dailyRecoveryLimit", settings.dailyRecoveryLimit)
                setObj.put("themeMode", settings.themeMode)
                payload.put("settings", setObj)
            }

            // Achievements
            val allAchievements = dao.getAllAchievementsList()
            val achievementsArray = JSONArray()
            for (a in allAchievements) {
                val obj = JSONObject()
                obj.put("id", a.code)
                obj.put("code", a.code)
                obj.put("title", a.title)
                obj.put("description", a.description)
                obj.put("category", a.category)
                obj.put("isUnlocked", a.isUnlocked)
                obj.put("unlockedAtTimestamp", a.unlockedAtTimestamp ?: JSONObject.NULL)
                achievementsArray.put(obj)
            }
            payload.put("achievements", achievementsArray)

            // PHASE 2: Push local state to cloud first
            val postResponse = apiClient.postSyncPayload(payload)
            if (postResponse != null && postResponse.optString("status") == "synced") {
                val serverData = postResponse.optJSONObject("data")
                if (serverData != null) {
                    reconcileIncomingSnapshot(serverData)
                }

                // Clean up expired local mutations
                val expireThreshold = System.currentTimeMillis() - 10000L
                localMutations.entries.removeIf { it.value < expireThreshold }

                context?.let { ctx ->
                    try {
                        WidgetUpdateHelper.updateAllWidgets(ctx)
                    } catch (_: Exception) {}
                }

                SyncResult(
                    success = true,
                    message = "Successfully synchronized with GATE Companion Cloud",
                    syncedCount = allProgress.size,
                    serverTimestamp = postResponse.optLong("timestamp")
                )
            } else {
                // If push failed, attempt pull-only reconciliation from server snapshot
                val serverSnapshot = apiClient.fetchServerSnapshot()
                if (serverSnapshot != null) {
                    val serverData = serverSnapshot.optJSONObject("data")
                    if (serverData != null) {
                        reconcileIncomingSnapshot(serverData)
                    }
                    context?.let { ctx ->
                        try {
                            WidgetUpdateHelper.updateAllWidgets(ctx)
                        } catch (_: Exception) {}
                    }
                    SyncResult(
                        success = true,
                        message = "Synchronized from cloud snapshot",
                        syncedCount = allProgress.size,
                        serverTimestamp = System.currentTimeMillis()
                    )
                } else {
                    SyncResult(
                        success = false,
                        message = "Cloud sync server unreachable (offline cache retained)"
                    )
                }
            }
        } catch (e: Exception) {
            SyncResult(
                success = false,
                message = "Sync failed: ${e.message}"
            )
        }
    }

    private suspend fun reconcileIncomingSnapshot(serverData: JSONObject) {
        val now = System.currentTimeMillis()

        // Unidirectional model: The Android app is the sole authority for tasks and recoveries.
        // If local Room database already has progress records, server snapshot CANNOT overwrite them.
        val existingProgress = dao.getAllProgress()
        if (existingProgress.isNotEmpty()) {
            // Local app state is authoritative. Do not overwrite existing tasks.
            return
        }

        // Only bootstrap if local database has zero task records (fresh install restore)
        val incomingProgress = serverData.optJSONArray("progress")
        if (incomingProgress != null) {
            for (i in 0 until incomingProgress.length()) {
                val item = incomingProgress.getJSONObject(i)
                val taskId = item.optString("taskId")
                val isDone = item.optBoolean("isCompleted")
                val origDate = item.optString("originalDate")
                val compType = if (item.isNull("completionType")) null else item.optString("completionType")
                val notes = item.optString("notes")
                val remoteTimestamp = item.optLong("completedAtTimestamp", 0L)

                if (taskId.isNotEmpty()) {
                    // Protect recent local explicit mutations from being overwritten
                    val localMutatedAt = localMutations[taskId] ?: 0L
                    if (now - localMutatedAt < 6000L) {
                        continue
                    }

                    val local = dao.getProgressForTask(taskId)
                    if (local == null) {
                        dao.insertOrUpdateProgress(
                            TaskProgressEntity(
                                taskId = taskId,
                                originalDate = origDate,
                                isCompleted = isDone,
                                completedAtTimestamp = if (isDone) (if (remoteTimestamp > 0) remoteTimestamp else System.currentTimeMillis()) else null,
                                completionType = if (isDone) compType else null,
                                notes = notes
                            )
                        )
                    } else if (local.isCompleted != isDone || local.notes != notes) {
                        dao.insertOrUpdateProgress(
                            local.copy(
                                isCompleted = isDone,
                                completedAtTimestamp = if (isDone) (if (remoteTimestamp > 0) remoteTimestamp else System.currentTimeMillis()) else null,
                                completionType = if (isDone) (compType ?: local.completionType) else null,
                                notes = if (notes.isNotEmpty()) notes else local.notes
                            )
                        )
                        // If a task is completed or uncompleted remotely, update any associated recovery
                        val rec = dao.getRecoveryForTask(taskId)
                        if (rec != null) {
                            if (isDone && !rec.isCompleted) {
                                dao.updateRecoveryAssignment(
                                    rec.copy(
                                        isCompleted = true,
                                        completedAtTimestamp = if (remoteTimestamp > 0) remoteTimestamp else System.currentTimeMillis(),
                                        status = "COMPLETED"
                                    )
                                )
                            } else if (!isDone && rec.isCompleted) {
                                dao.updateRecoveryAssignment(
                                    rec.copy(
                                        isCompleted = false,
                                        completedAtTimestamp = null,
                                        status = "MANUAL_CONFIRMED"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Reconcile incoming recoveries from web
        val incomingRecoveries = serverData.optJSONArray("recoveries")
        if (incomingRecoveries != null) {
            for (i in 0 until incomingRecoveries.length()) {
                val item = incomingRecoveries.getJSONObject(i)
                val taskId = item.optString("taskId")
                val recoveryDate = item.optString("recoveryDate")
                val origDate = item.optString("originalDate")
                val status = item.optString("status", "MANUAL_CONFIRMED")
                val isCompleted = item.optBoolean("isCompleted", false)

                if (taskId.isNotEmpty() && recoveryDate.isNotEmpty()) {
                    val localMutatedAt = localMutations[taskId] ?: 0L
                    if (now - localMutatedAt < 6000L) {
                        continue
                    }

                    val local = dao.getRecoveryForTask(taskId)
                    if (local == null) {
                        dao.insertRecoveryAssignment(
                            RecoveryAssignmentEntity(
                                taskId = taskId,
                                originalDate = origDate,
                                recoveryDate = recoveryDate,
                                assignedAtTimestamp = System.currentTimeMillis(),
                                isCompleted = isCompleted,
                                priorityLevel = 2,
                                status = status
                            )
                        )
                    } else if (local.recoveryDate != recoveryDate || local.status != status || local.isCompleted != isCompleted) {
                        dao.updateRecoveryAssignment(
                            local.copy(
                                recoveryDate = recoveryDate,
                                status = status,
                                isCompleted = isCompleted
                            )
                        )
                    }
                }
            }
        }

        // Reconcile incoming custom tasks from web
        val incomingCustom = serverData.optJSONArray("customTasks")
        if (incomingCustom != null) {
            for (i in 0 until incomingCustom.length()) {
                val item = incomingCustom.getJSONObject(i)
                val id = item.optLong("id")
                val date = item.optString("date")
                val title = item.optString("title")
                val subCode = item.optString("subjectCode")
                val subName = item.optString("subjectName")
                val isCompleted = item.optBoolean("isCompleted", false)
                val notes = item.optString("notes")
                val pts = item.optInt("pointsValue", 5)

                if (id > 0 && title.isNotEmpty()) {
                    val local = dao.getCustomTaskById(id)
                    if (local == null) {
                        dao.insertCustomTask(
                            CustomTaskEntity(
                                id = id,
                                date = date,
                                title = title,
                                subjectCode = subCode,
                                subjectName = subName,
                                isCompleted = isCompleted,
                                notes = notes,
                                pointsValue = pts
                            )
                        )
                    } else if (local.isCompleted != isCompleted || local.notes != notes) {
                        dao.updateCustomTask(
                            local.copy(
                                isCompleted = isCompleted,
                                notes = notes
                            )
                        )
                    }
                }
            }
        }

        // Reconcile incoming achievements from web
        val incomingAchievements = serverData.optJSONArray("achievements")
        if (incomingAchievements != null) {
            for (i in 0 until incomingAchievements.length()) {
                val item = incomingAchievements.getJSONObject(i)
                val code = item.optString("code").ifEmpty { item.optString("id") }
                val isUnlocked = item.optBoolean("isUnlocked", false)
                val unlockedAt = if (item.isNull("unlockedAtTimestamp")) null else item.optLong("unlockedAtTimestamp")

                if (code.isNotEmpty()) {
                    val local = dao.getAchievementByCode(code)
                    if (local != null && local.isUnlocked != isUnlocked) {
                        dao.updateAchievement(
                            local.copy(
                                isUnlocked = isUnlocked,
                                unlockedAtTimestamp = if (isUnlocked) (if (unlockedAt != null && unlockedAt > 0) unlockedAt else System.currentTimeMillis()) else null
                            )
                        )
                    }
                }
            }
        }
    }
}
