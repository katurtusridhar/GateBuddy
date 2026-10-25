package com.gatecse.companion.updater

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = UpdateApiClient(application)
    private val downloader = UpdateDownloader(application)
    val installer = UpdateInstaller(application)

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val _catalog = MutableStateFlow<ReleaseCatalog?>(null)
    val catalog: StateFlow<ReleaseCatalog?> = _catalog.asStateFlow()

    private val _selectedReleaseForConfirmation = MutableStateFlow<ReleaseEntry?>(null)
    val selectedReleaseForConfirmation: StateFlow<ReleaseEntry?> = _selectedReleaseForConfirmation.asStateFlow()

    private var downloadJob: Job? = null

    val installedVersionCode: Long
        get() = apiClient.getInstalledVersionCode()

    val installedVersionName: String
        get() = apiClient.getInstalledVersionName()

    val activeChannel: String
        get() = apiClient.getActiveChannel()

    companion object {
        const val CURRENT_DATABASE_VERSION = 3
    }

    fun evaluateReleaseAction(release: ReleaseEntry): ReleaseActionState {
        val currentCode = installedVersionCode
        val targetCode = release.installVersionCode.toLong()

        if (targetCode == currentCode) {
            return ReleaseActionState.Current
        }

        if (targetCode < currentCode) {
            return ReleaseActionState.HistoricalBuildRequired(
                reason = "Direct in-place install blocked by Android (Build ${release.installVersionCode} < current Build $currentCode). Use a Restore build to run this version.",
                historicalBehaviorVersion = release.behaviorVersion.ifEmpty { release.installVersionName }
            )
        }

        // Target code is greater than current code (Forward install supported by Android)
        // 1. Check database schema compatibility
        if (release.database.requiresSchemaDowngrade ||
            release.database.minSupportedDatabaseVersion > CURRENT_DATABASE_VERSION ||
            release.database.databaseVersion < CURRENT_DATABASE_VERSION
        ) {
            return ReleaseActionState.Incompatible(
                "Database schema mismatch: requires schema downgrade or unsupported database version."
            )
        }

        // 2. Check Android SDK level compatibility
        if (android.os.Build.VERSION.SDK_INT < release.compatibility.minSdk) {
            return ReleaseActionState.Incompatible(
                "Requires Android API ${release.compatibility.minSdk}+ (device is API ${android.os.Build.VERSION.SDK_INT})."
            )
        }

        return if (release.releaseType.equals("rollback", ignoreCase = true)) {
            val targetBehavior = release.restoresBehaviorVersion ?: release.behaviorVersion.ifEmpty { release.installVersionName }
            ReleaseActionState.CanRestore(release, targetBehavior)
        } else {
            ReleaseActionState.CanUpdate(release)
        }
    }

    fun requestReleaseInstallation(release: ReleaseEntry) {
        _selectedReleaseForConfirmation.value = release
    }

    fun dismissConfirmation() {
        _selectedReleaseForConfirmation.value = null
    }

    fun confirmReleaseInstallation() {
        val release = _selectedReleaseForConfirmation.value ?: return
        _selectedReleaseForConfirmation.value = null
        startDownload(release)
    }

    init {
        // Initial silent check
        checkForUpdates()
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            val result = apiClient.checkForUpdates()
            when (result) {
                is UpdateCheckResult.UpToDate -> {
                    _uiState.value = UpdateUiState.UpToDate(
                        installedVersionCode = result.installedVersionCode,
                        installedVersionName = result.installedVersionName
                    )
                }
                is UpdateCheckResult.UpdateAvailable -> {
                    _uiState.value = UpdateUiState.UpdateAvailable(result.release)
                }
                is UpdateCheckResult.CheckFailed -> {
                    _uiState.value = UpdateUiState.Error(result.error)
                }
            }

            // Also load full catalog for version history
            val catalogResult = apiClient.fetchCatalog()
            if (catalogResult.isSuccess) {
                _catalog.value = catalogResult.getOrNull()
            }
        }
    }

    fun startDownload(release: ReleaseEntry) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.value = UpdateUiState.Downloading(
                release = release,
                progress = UpdateDownloadProgress(0, release.apkSizeBytes, 0)
            )

            val downloadResult = downloader.downloadAndVerify(release) { progress ->
                _uiState.value = UpdateUiState.Downloading(release, progress)
            }

            if (downloadResult.isSuccess) {
                val apkFile = downloadResult.getOrThrow()
                _uiState.value = UpdateUiState.ReadyToInstall(release, apkFile)
            } else {
                val errorMsg = downloadResult.exceptionOrNull()?.message ?: "Download failed."
                _uiState.value = UpdateUiState.Error(errorMsg)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.value = UpdateUiState.Idle
    }

    fun onInstallStarted(release: ReleaseEntry) {
        _uiState.value = UpdateUiState.Installing(release)
    }

    fun verifyPostInstall(targetVersionCode: Int) {
        val current = apiClient.getInstalledVersionCode()
        if (current >= targetVersionCode) {
            installer.cleanUpCachedApk(targetVersionCode)
            _uiState.value = UpdateUiState.UpToDate(current, apiClient.getInstalledVersionName())
        }
    }
}
