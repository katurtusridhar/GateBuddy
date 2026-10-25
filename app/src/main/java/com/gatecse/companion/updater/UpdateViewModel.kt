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

    private var downloadJob: Job? = null

    val installedVersionCode: Long
        get() = apiClient.getInstalledVersionCode()

    val installedVersionName: String
        get() = apiClient.getInstalledVersionName()

    val activeChannel: String
        get() = apiClient.getActiveChannel()

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
