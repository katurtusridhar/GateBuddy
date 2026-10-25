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

class SyncApiClient(
    private val baseUrl: String = "https://gate-buddy.vercel.app",
    private val context: Context? = null
) {

    companion object {
        private const val PREFS_NAME = "gate_auth_prefs"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_USERNAME = "auth_username"
        @Volatile
        private var inMemoryToken: String? = null
    }

    private suspend fun getSessionToken(): String? = withContext(Dispatchers.IO) {
        val cached = inMemoryToken
        if (!cached.isNullOrBlank()) {
            return@withContext cached
        }
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedToken = prefs?.getString(KEY_SESSION_TOKEN, null)
        if (!savedToken.isNullOrBlank()) {
            inMemoryToken = savedToken
            return@withContext savedToken
        }
        obtainFreshToken()
    }

    private suspend fun obtainFreshToken(): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/auth/token")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doOutput = true

            val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val username = prefs?.getString(KEY_USERNAME, "katurtusridhar") ?: "katurtusridhar"

            val body = JSONObject()
            body.put("grantType", "companion_client")
            body.put("username", username)

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
                    inMemoryToken = token
                    prefs?.edit()?.putString(KEY_SESSION_TOKEN, token)?.apply()
                    return@withContext token
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("GateBuddySync", "Token acquisition failed: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun invalidateToken() {
        inMemoryToken = null
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.remove(KEY_SESSION_TOKEN)?.apply()
    }

    suspend fun postSyncPayload(payloadJson: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        var token = getSessionToken()
        var response = executePost(payloadJson, token)

        // If unauthorized (token expired), refresh token and retry once
        if (response == null) {
            invalidateToken()
            token = obtainFreshToken()
            if (token != null) {
                response = executePost(payloadJson, token)
            }
        }
        response
    }

    private fun executePost(payloadJson: JSONObject, token: String?): JSONObject? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/sync")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(payloadJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            android.util.Log.d("GateBuddySync", "POST /api/sync status: $responseCode")
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
        var token = getSessionToken()
        var response = executeGet(token)

        // If unauthorized, refresh token and retry once
        if (response == null) {
            invalidateToken()
            token = obtainFreshToken()
            if (token != null) {
                response = executeGet(token)
            }
        }
        response
    }

    private fun executeGet(token: String?): JSONObject? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/sync")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            val responseCode = connection.responseCode
            android.util.Log.d("GateBuddySync", "GET /api/sync status: $responseCode")
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
