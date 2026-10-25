package com.gatecse.companion.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

sealed interface InstallIntentResult {
    data class PermissionRequired(val settingsIntent: Intent) : InstallIntentResult
    data class ReadyToLaunch(val installIntent: Intent) : InstallIntentResult
    data class Failure(val reason: String) : InstallIntentResult
}

class UpdateInstaller(private val context: Context) {

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun prepareInstallIntent(apkFile: File): InstallIntentResult {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return InstallIntentResult.Failure("APK file is missing or empty.")
        }

        // 1. Permission Check on Android 8.0+
        if (!canRequestPackageInstalls()) {
            val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return InstallIntentResult.PermissionRequired(permissionIntent)
        }

        // 2. Build FileProvider URI
        return try {
            val authority = "${context.packageName}.updateprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            InstallIntentResult.ReadyToLaunch(installIntent)
        } catch (e: Exception) {
            InstallIntentResult.Failure("Failed to prepare installation intent: ${e.message}")
        }
    }

    fun cleanUpCachedApk(versionCode: Int) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            val targetApk = File(updatesDir, "gatebuddy_v$versionCode.apk")
            if (targetApk.exists()) {
                targetApk.delete()
            }
        } catch (ignored: Exception) {
        }
    }
}
