package com.gatecse.companion.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class UpdateDownloader(private val context: Context) {

    companion object {
        private const val BUFFER_SIZE = 8192
    }

    suspend fun downloadAndVerify(
        release: ReleaseEntry,
        onProgress: (UpdateDownloadProgress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val stagingFile = File(updatesDir, "staging_update.apk.tmp")
        val finalApkFile = File(updatesDir, "gatebuddy_v${release.versionCode}.apk")

        // 1. Storage Pre-flight Check: Ensure at least 2x APK size free space
        val requiredBytes = release.apkSizeBytes * 2L
        val availableBytes = getAvailableStorageBytes(updatesDir)
        if (availableBytes < requiredBytes) {
            val reqMb = requiredBytes / (1024 * 1024)
            val availMb = availableBytes / (1024 * 1024)
            return@withContext Result.failure(
                IllegalStateException("Insufficient storage space: $availMb MB available, $reqMb MB required.")
            )
        }

        // Clean up any stale staging file
        if (stagingFile.exists()) {
            stagingFile.delete()
        }

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val url = URL(release.apkUrl)
            if (!url.protocol.equals("https", ignoreCase = true)) {
                return@withContext Result.failure(SecurityException("Insecure download URL: HTTPS required."))
            }

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "GATEBuddy-Updater")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    IllegalStateException("Download failed with HTTP $responseCode")
                )
            }

            val contentLength = connection.contentLength.toLong()
            if (contentLength > 0 && contentLength != release.apkSizeBytes) {
                return@withContext Result.failure(
                    IllegalStateException("Content-Length mismatch: expected ${release.apkSizeBytes}, server sent $contentLength")
                )
            }

            inputStream = connection.inputStream
            outputStream = FileOutputStream(stagingFile)
            val digest = MessageDigest.getInstance("SHA-256")

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesDownloaded = 0L
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                if (!coroutineContext.isActive) {
                    stagingFile.delete()
                    return@withContext Result.failure(InterruptedException("Download was cancelled by user."))
                }

                outputStream.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                bytesDownloaded += read

                val percent = if (release.apkSizeBytes > 0) {
                    ((bytesDownloaded * 100) / release.apkSizeBytes).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                onProgress(
                    UpdateDownloadProgress(
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = release.apkSizeBytes,
                        percentage = percent
                    )
                )
            }

            outputStream.flush()
            outputStream.fd.sync()

            // 2. File size verification
            if (stagingFile.length() != release.apkSizeBytes) {
                stagingFile.delete()
                return@withContext Result.failure(
                    IllegalStateException("File size verification failed: expected ${release.apkSizeBytes}, downloaded ${stagingFile.length()}")
                )
            }

            // 3. SHA-256 Checksum verification
            val computedHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!computedHash.equals(release.sha256, ignoreCase = true)) {
                stagingFile.delete()
                return@withContext Result.failure(
                    SecurityException("SHA-256 checksum mismatch: computed $computedHash, manifest expected ${release.sha256}")
                )
            }

            // 4. Package Identity Verification
            val archiveInfo = getPackageArchiveInfo(stagingFile.absolutePath)
                ?: run {
                    stagingFile.delete()
                    return@withContext Result.failure(IllegalStateException("Downloaded file is not a valid Android APK package."))
                }

            if (archiveInfo.packageName != context.packageName) {
                stagingFile.delete()
                return@withContext Result.failure(
                    SecurityException("Package mismatch: APK contains '${archiveInfo.packageName}', current app is '${context.packageName}'")
                )
            }

            // 5. Signing Certificate Verification
            val installedCertFingerprint = getInstalledAppCertificateFingerprint()
            val apkCertFingerprint = getApkCertificateFingerprint(stagingFile.absolutePath)

            if (apkCertFingerprint == null || installedCertFingerprint == null) {
                stagingFile.delete()
                return@withContext Result.failure(SecurityException("Failed to extract signing certificates for verification."))
            }

            val normalizedApkFingerprint = normalizeFingerprint(apkCertFingerprint)
            val normalizedInstalledFingerprint = normalizeFingerprint(installedCertFingerprint)
            val normalizedManifestFingerprint = normalizeFingerprint(release.signingCertificate.sha256Fingerprint)

            if (!normalizedApkFingerprint.equals(normalizedInstalledFingerprint, ignoreCase = true)) {
                stagingFile.delete()
                return@withContext Result.failure(
                    SecurityException("Signing certificate mismatch: APK is signed with a different key than the installed app.")
                )
            }

            if (!normalizedApkFingerprint.equals(normalizedManifestFingerprint, ignoreCase = true)) {
                stagingFile.delete()
                return@withContext Result.failure(
                    SecurityException("Signing certificate mismatch: APK signature does not match manifest specification.")
                )
            }

            // Atomic promotion: Replace existing final APK if present
            if (finalApkFile.exists()) {
                finalApkFile.delete()
            }
            if (!stagingFile.renameTo(finalApkFile)) {
                stagingFile.copyTo(finalApkFile, overwrite = true)
                stagingFile.delete()
            }

            Result.success(finalApkFile)
        } catch (e: Exception) {
            if (stagingFile.exists()) {
                stagingFile.delete()
            }
            Result.failure(e)
        } finally {
            try {
                outputStream?.close()
                inputStream?.close()
                connection?.disconnect()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun getAvailableStorageBytes(dir: File): Long {
        return try {
            val stat = StatFs(dir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun getPackageArchiveInfo(path: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(path, 0)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getInstalledAppCertificateFingerprint(): String? {
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, flags)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pInfo.signatures
            }

            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(certBytes).joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun getApkCertificateFingerprint(apkPath: String): String? {
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apkPath, flags)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo?.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                archiveInfo?.signatures
            }

            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(certBytes).joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeFingerprint(fp: String): String {
        return fp.replace(":", "").trim().uppercase()
    }
}
