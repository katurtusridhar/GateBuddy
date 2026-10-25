package com.gatecse.companion.sync

import com.gatecse.companion.model.AssessmentResultEntity
import com.gatecse.companion.model.CustomTaskEntity
import com.gatecse.companion.model.RecoveryAssignmentEntity
import com.gatecse.companion.model.TaskProgressEntity
import com.gatecse.companion.model.UserSettingsEntity

data class SyncResult(
    val success: Boolean,
    val message: String,
    val syncedCount: Int = 0,
    val serverTimestamp: Long = 0L
)

data class SyncConfig(
    val serverBaseUrl: String = "http://10.0.2.2:3000",
    val authToken: String = "",
    val autoSyncEnabled: Boolean = true
)
