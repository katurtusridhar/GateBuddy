package com.gatecse.companion.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

sealed class SessionAuthResult {
    data class Success(val username: String, val token: String) : SessionAuthResult()
    data class AuthFailed(val errorMessage: String) : SessionAuthResult()
    data class NetworkError(val errorMessage: String) : SessionAuthResult()
}

enum class CompanionSessionStatus {
    NOT_CONFIGURED,
    CONNECTED,
    SESSION_EXPIRED
}

class SyncApiClient(
    private val baseUrl: String = "https://gate-buddy.vercel.app",
    private val context: Context? = null
) {

    companion object {
        private const val PREFS_NAME = "gate_auth_prefs"
        private const val KEY_USERNAME = "auth_username"
        private const val KEY_EXPIRED = "is_session_expired"
        private const val LEGACY_KEY_PASSWORD = "auth_password"
        private const val LEGACY_KEY_TOKEN = "session_token"

        @Volatile
        var customPrefsForTesting: android.content.SharedPreferences? = null

        private fun getPrefs(context: Context?): android.content.SharedPreferences? {
            val custom = customPrefsForTesting
            if (custom != null) return custom
            return context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        @Volatile
        private var inMemoryToken: String? = null

        fun getSavedUsername(context: Context?): String {
            return getPrefs(context)?.getString(KEY_USERNAME, "") ?: ""
        }

        fun isSessionExpired(context: Context?): Boolean {
            return getPrefs(context)?.getBoolean(KEY_EXPIRED, false) ?: false
        }

        fun markSessionExpired(context: Context?) {
            getPrefs(context)?.edit()
                ?.putBoolean(KEY_EXPIRED, true)
                ?.apply()
            SecureTokenStorage.clearToken(context)
            inMemoryToken = null
        }

        fun hasCredentials(context: Context?): Boolean {
            val hasToken = SecureTokenStorage.hasToken(context)
            val hasUser = getSavedUsername(context).isNotBlank()
            val expired = isSessionExpired(context)
            return hasToken && hasUser && !expired
        }

        fun getSessionStatus(context: Context?): CompanionSessionStatus {
            if (isSessionExpired(context)) return CompanionSessionStatus.SESSION_EXPIRED
            if (hasCredentials(context)) return CompanionSessionStatus.CONNECTED
            return CompanionSessionStatus.NOT_CONFIGURED
        }

        fun signOut(context: Context?) {
            SecureTokenStorage.clearToken(context)
            getPrefs(context)?.edit()
                ?.remove(KEY_USERNAME)
                ?.remove(KEY_EXPIRED)
                ?.remove(LEGACY_KEY_PASSWORD)
                ?.remove(LEGACY_KEY_TOKEN)
                ?.apply()
            inMemoryToken = null
        }

        /**
         * Authenticates with user credentials, retrieves a revocable session token,
         * encrypts it via AndroidKeyStore, and PURGES the user's password from memory.
         * The password is NEVER written to disk.
         */
        suspend fun authenticateAndSaveSession(
            context: Context?,
            username: String,
            password: String,
            customBaseUrl: String = "https://gate-buddy.vercel.app"
        ): SessionAuthResult = withContext(Dispatchers.IO) {
            val cleanUser = username.trim()
            val cleanPass = password.trim()

            if (cleanUser.isBlank() || cleanPass.isBlank()) {
                return@withContext SessionAuthResult.AuthFailed("Username and password cannot be empty.")
            }

            var connection: HttpURLConnection? = null
            try {
                val url = URL("$customBaseUrl/api/auth/token")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.doOutput = true

                val body = JSONObject()
                body.put("grantType", "companion_client")
                body.put("clientKey", "gb_companion_secure_sync_2026_tok")
                body.put("username", cleanUser)
                body.put("password", cleanPass)

                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                    val responseText = reader.readText()
                    reader.close()

                    val json = JSONObject(responseText)
                    val token = json.optString("token", "")
                    if (token.isNotBlank()) {
                        // Persist revocable token via AndroidKeyStore
                        SecureTokenStorage.saveToken(context, token)
                        inMemoryToken = token

                        // Save username and clear expired/legacy password keys
                        getPrefs(context)?.edit()
                            ?.putString(KEY_USERNAME, cleanUser)
                            ?.putBoolean(KEY_EXPIRED, false)
                            ?.remove(LEGACY_KEY_PASSWORD)
                            ?.remove(LEGACY_KEY_TOKEN)
                            ?.apply()

                        return@withContext SessionAuthResult.Success(cleanUser, token)
                    } else {
                        return@withContext SessionAuthResult.AuthFailed("Server returned empty session token.")
                    }
                } else if (responseCode == 401 || responseCode == 403) {
                    val errorText = try {
                        val errReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream, "UTF-8"))
                        val errJson = JSONObject(errReader.readText())
                        errReader.close()
                        errJson.optString("error", "Invalid credentials.")
                    } catch (_: Exception) {
                        "Invalid username or password."
                    }
                    return@withContext SessionAuthResult.AuthFailed(errorText)
                } else {
                    return@withContext SessionAuthResult.NetworkError("HTTP $responseCode from authorization server.")
                }
            } catch (e: Exception) {
                return@withContext SessionAuthResult.NetworkError("Network error: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }

        suspend fun switchAccount(
            context: Context?,
            newUsername: String,
            newPassword: String,
            customBaseUrl: String = "https://gate-buddy.vercel.app"
        ): SessionAuthResult {
            signOut(context)
            return authenticateAndSaveSession(context, newUsername, newPassword, customBaseUrl)
        }
    }

    private fun getSessionToken(): String? {
        val cached = inMemoryToken
        if (!cached.isNullOrBlank()) {
            return cached
        }
        val ctx = context ?: return null
        if (isSessionExpired(ctx)) {
            return null
        }
        val token = SecureTokenStorage.getToken(ctx)
        if (!token.isNullOrBlank()) {
            inMemoryToken = token
            return token
        }
        return null
    }

    suspend fun postSyncPayload(payloadJson: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        val token = getSessionToken()
        if (token.isNullOrBlank()) {
            android.util.Log.w("GateBuddySync", "Cannot sync: No active session token. Please sign in via Settings.")
            return@withContext null
        }
        executePost(payloadJson, token)
    }

    private fun executePost(payloadJson: JSONObject, token: String): JSONObject? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/sync")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(payloadJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            android.util.Log.d("GateBuddySync", "POST /api/sync status: $responseCode")

            if (responseCode == 401) {
                // Session expired or revoked
                context?.let { markSessionExpired(it) }
                return null
            }

            return if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val responseText = reader.readText()
                reader.close()
                JSONObject(responseText)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("GateBuddySync", "POST /api/sync exception: ${e.message}", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun fetchServerSnapshot(): JSONObject? = withContext(Dispatchers.IO) {
        val token = getSessionToken()
        if (token.isNullOrBlank()) {
            return@withContext null
        }
        executeGet(token)
    }

    private fun executeGet(token: String): JSONObject? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/sync")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            val responseCode = connection.responseCode
            android.util.Log.d("GateBuddySync", "GET /api/sync status: $responseCode")

            if (responseCode == 401) {
                // Session expired or revoked
                context?.let { markSessionExpired(it) }
                return null
            }

            return if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val responseText = reader.readText()
                reader.close()
                JSONObject(responseText)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("GateBuddySync", "GET /api/sync exception: ${e.message}", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }
}
