package com.gatecse.companion.updater

import java.io.File

data class ReleaseChangelog(
    val summary: String,
    val highlights: List<String>
)

data class ReleaseSigningCertificate(
    val sha256Fingerprint: String,
    val subjectDn: String? = null
)

data class ReleaseCompatibility(
    val minSdk: Int,
    val targetSdk: Int,
    val supportedAbis: List<String>
)

data class ReleaseDatabase(
    val databaseVersion: Int,
    val minSupportedDatabaseVersion: Int,
    val requiresSchemaDowngrade: Boolean
)

data class ReleaseEntry(
    val releaseId: String = "",
    val behaviorVersion: String = "",
    val installVersionCode: Int,
    val installVersionName: String,
    val releaseType: String = "standard", // "standard" or "rollback"
    val restoresBehaviorVersion: String? = null,
    val releaseDate: String = "",
    val changelog: ReleaseChangelog,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val sha256: String,
    val signingCertificate: ReleaseSigningCertificate,
    val compatibility: ReleaseCompatibility,
    val database: ReleaseDatabase,
    val verificationStatus: String = "verified" // "verified" or "unverified"
) {
    // Backwards compatibility aliases
    val versionCode: Int get() = installVersionCode
    val versionName: String get() = installVersionName
}

sealed interface ReleaseActionState {
    object Current : ReleaseActionState
    data class CanUpdate(val release: ReleaseEntry) : ReleaseActionState
    data class CanRestore(val release: ReleaseEntry, val targetBehaviorVersion: String) : ReleaseActionState
    data class Incompatible(val reason: String) : ReleaseActionState
    data class HistoricalBuildRequired(val reason: String, val historicalBehaviorVersion: String) : ReleaseActionState
}

data class ReleaseCatalog(
    val manifestVersion: Int,
    val channel: String,
    val packageName: String,
    val recommendedUpdate: ReleaseEntry?,
    val releases: List<ReleaseEntry>
)

sealed interface UpdateCheckResult {
    data class UpToDate(val installedVersionCode: Long, val installedVersionName: String) : UpdateCheckResult
    data class UpdateAvailable(
        val release: ReleaseEntry,
        val installedVersionCode: Long,
        val installedVersionName: String
    ) : UpdateCheckResult
    data class CheckFailed(val error: String) : UpdateCheckResult
}

data class UpdateDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percentage: Int
)

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    data class UpToDate(val installedVersionCode: Long, val installedVersionName: String) : UpdateUiState
    data class UpdateAvailable(val release: ReleaseEntry) : UpdateUiState
    data class Downloading(val release: ReleaseEntry, val progress: UpdateDownloadProgress) : UpdateUiState
    data class Verifying(val release: ReleaseEntry) : UpdateUiState
    data class ReadyToInstall(val release: ReleaseEntry, val apkFile: File) : UpdateUiState
    data class Installing(val release: ReleaseEntry) : UpdateUiState
    data class Error(val message: String, val canRetry: Boolean = true) : UpdateUiState
}
