package com.gatecse.companion.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class UpdateApiClient(
    private val context: Context,
    private val baseUrl: String = "https://gate-buddy.vercel.app"
) {

    fun getInstalledPackageName(): String {
        return context.packageName
    }

    fun getInstalledVersionCode(): Long {
        return try {
            val pInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            2L
        }
    }

    fun getInstalledVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0.1"
        } catch (e: Exception) {
            "1.0.1"
        }
    }

    fun getActiveChannel(): String {
        return if (context.packageName.endsWith(".debug")) "debug" else "stable"
    }

    suspend fun fetchCatalog(): Result<ReleaseCatalog> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val channel = getActiveChannel()
            val pkg = getInstalledPackageName()
            val url = URL("$baseUrl/api/releases?channel=$channel&package=$pkg")
            
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "GATEBuddy-Android/${getInstalledVersionName()}")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)
                val catalog = parseCatalog(json)
                Result.success(catalog)
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                val errorMsg = try {
                    JSONObject(errorStream ?: "").optString("message", "Server returned HTTP $responseCode")
                } catch (e: Exception) {
                    "Update server returned HTTP $responseCode"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to check for updates: ${e.localizedMessage ?: e.message}"))
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val installedCode = getInstalledVersionCode()
        val installedName = getInstalledVersionName()

        val catalogResult = fetchCatalog()
        if (catalogResult.isFailure) {
            return@withContext UpdateCheckResult.CheckFailed(catalogResult.exceptionOrNull()?.message ?: "Unknown error")
        }

        val catalog = catalogResult.getOrThrow()

        // Package and channel verification
        if (catalog.packageName != getInstalledPackageName()) {
            return@withContext UpdateCheckResult.CheckFailed("Catalog package '${catalog.packageName}' does not match installed app.")
        }

        val recommended = catalog.recommendedUpdate
        if (recommended != null &&
            recommended.releaseType == "standard" &&
            recommended.versionCode > installedCode
        ) {
            UpdateCheckResult.UpdateAvailable(
                release = recommended,
                installedVersionCode = installedCode,
                installedVersionName = installedName
            )
        } else {
            UpdateCheckResult.UpToDate(
                installedVersionCode = installedCode,
                installedVersionName = installedName
            )
        }
    }

    private fun parseCatalog(json: JSONObject): ReleaseCatalog {
        val manifestVersion = json.optInt("manifestVersion", 1)
        val channel = json.getString("channel")
        val packageName = json.getString("packageName")

        val recommendedObj = json.optJSONObject("recommendedUpdate")
        val recommended = if (recommendedObj != null) parseReleaseEntry(recommendedObj) else null

        val releasesArray = json.optJSONArray("releases") ?: JSONArray()
        val releasesList = mutableListOf<ReleaseEntry>()
        for (i in 0 until releasesArray.length()) {
            val entryObj = releasesArray.getJSONObject(i)
            releasesList.add(parseReleaseEntry(entryObj))
        }

        return ReleaseCatalog(
            manifestVersion = manifestVersion,
            channel = channel,
            packageName = packageName,
            recommendedUpdate = recommended,
            releases = releasesList
        )
    }

    private fun parseReleaseEntry(obj: JSONObject): ReleaseEntry {
        val changelogObj = obj.getJSONObject("changelog")
        val highlightsArray = changelogObj.optJSONArray("highlights") ?: JSONArray()
        val highlights = mutableListOf<String>()
        for (i in 0 until highlightsArray.length()) {
            highlights.add(highlightsArray.getString(i))
        }
        val changelog = ReleaseChangelog(
            summary = changelogObj.optString("summary", ""),
            highlights = highlights
        )

        val certObj = obj.getJSONObject("signingCertificate")
        val cert = ReleaseSigningCertificate(
            sha256Fingerprint = certObj.getString("sha256Fingerprint"),
            subjectDn = if (certObj.has("subjectDn") && !certObj.isNull("subjectDn")) certObj.getString("subjectDn") else null
        )

        val compatObj = obj.getJSONObject("compatibility")
        val abisArray = compatObj.optJSONArray("supportedAbis") ?: JSONArray()
        val abis = mutableListOf<String>()
        for (i in 0 until abisArray.length()) {
            abis.add(abisArray.getString(i))
        }
        val compat = ReleaseCompatibility(
            minSdk = compatObj.optInt("minSdk", 26),
            targetSdk = compatObj.optInt("targetSdk", 35),
            supportedAbis = abis
        )

        val dbObj = obj.getJSONObject("database")
        val db = ReleaseDatabase(
            databaseVersion = dbObj.optInt("databaseVersion", 3),
            minSupportedDatabaseVersion = dbObj.optInt("minSupportedDatabaseVersion", 3),
            requiresSchemaDowngrade = dbObj.optBoolean("requiresSchemaDowngrade", false)
        )

        return ReleaseEntry(
            versionCode = obj.getInt("versionCode"),
            versionName = obj.getString("versionName"),
            releaseDate = obj.optString("releaseDate", ""),
            releaseType = obj.optString("releaseType", "standard"),
            changelog = changelog,
            apkUrl = obj.getString("apkUrl"),
            apkSizeBytes = obj.getLong("apkSizeBytes"),
            sha256 = obj.getString("sha256"),
            signingCertificate = cert,
            compatibility = compat,
            database = db
        )
    }
}
