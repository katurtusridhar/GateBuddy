package com.gatecse.companion.sync

import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SyncSecurityTest {

    private lateinit var testPrefs: InMemoryTestSharedPreferences

    @Before
    fun setUp() {
        testPrefs = InMemoryTestSharedPreferences()
        SecureTokenStorage.customPrefsForTesting = testPrefs
        SecureTokenStorage.customProviderForTesting = JvmAesGcmCryptoProvider()
        SyncApiClient.customPrefsForTesting = testPrefs
    }

    @After
    fun tearDown() {
        SecureTokenStorage.customPrefsForTesting = null
        SecureTokenStorage.customProviderForTesting = null
        SyncApiClient.customPrefsForTesting = null
    }

    @Test
    fun testKeystoreAesGcmEncryptionRoundtrip() {
        val crypto = JvmAesGcmCryptoProvider()
        val originalToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sridhar_session_token_2026"

        val cipherText = crypto.encrypt(originalToken)
        assertNotNull(cipherText)
        assertNotEquals(originalToken, cipherText)

        val decrypted = crypto.decrypt(cipherText)
        assertEquals(originalToken, decrypted)
    }

    @Test
    fun testMissingCredentialsHandling() {
        // 1. Fresh install with empty SharedPreferences
        assertFalse("Fresh install should not have credentials", SyncApiClient.hasCredentials(null))
        assertEquals(CompanionSessionStatus.NOT_CONFIGURED, SyncApiClient.getSessionStatus(null))
        assertEquals("", SyncApiClient.getSavedUsername(null))
        assertFalse(SecureTokenStorage.hasToken(null))
    }

    @Test
    fun testZeroPasswordPersistenceOnDisk() = runBlocking {
        // Mock token persistence
        val testUser = "katurtusridhar"
        val sampleToken = "revocable_jwt_session_token_valid_90_days"

        // Save token to secure storage & username to preferences
        SecureTokenStorage.saveToken(null, sampleToken)
        testPrefs.edit().putString("auth_username", testUser).apply()

        // Verify status
        assertTrue(SyncApiClient.hasCredentials(null))
        assertEquals(CompanionSessionStatus.CONNECTED, SyncApiClient.getSessionStatus(null))
        assertEquals(testUser, SyncApiClient.getSavedUsername(null))

        // CRITICAL INVARIANT: The password MUST NEVER be persisted to disk!
        assertFalse(
            "Plaintext password must NEVER exist in SharedPreferences",
            testPrefs.contains("auth_password")
        )
        assertNull(
            "Legacy auth_password must be null",
            testPrefs.getString("auth_password", null)
        )

        // Raw encrypted token in prefs must NOT equal the plaintext token
        val rawEncrypted = testPrefs.getString("enc_session_token", null)
        assertNotNull("Encrypted token must exist in prefs", rawEncrypted)
        assertNotEquals("Token on disk must be encrypted, not plaintext", sampleToken, rawEncrypted)

        // Decrypted token through SecureTokenStorage must match
        assertEquals(sampleToken, SecureTokenStorage.getToken(null))
    }

    @Test
    fun testExpiredSessionDetectionAndInvalidation() {
        val testUser = "katurtusridhar"
        val sampleToken = "expired_token_123"

        SecureTokenStorage.saveToken(null, sampleToken)
        testPrefs.edit().putString("auth_username", testUser).apply()
        assertTrue(SyncApiClient.hasCredentials(null))

        // Simulate 401 Unauthorized -> Mark session expired
        SyncApiClient.markSessionExpired(null)

        assertTrue("Session must be marked expired", SyncApiClient.isSessionExpired(null))
        assertEquals(CompanionSessionStatus.SESSION_EXPIRED, SyncApiClient.getSessionStatus(null))
        assertFalse("Credentials check must fail when session is expired", SyncApiClient.hasCredentials(null))
        assertNull("Expired session token must be cleared from storage", SecureTokenStorage.getToken(null))
    }

    @Test
    fun testReauthenticationAfterSessionExpiry() {
        val testUser = "katurtusridhar"
        // Start in expired state
        testPrefs.edit()
            .putString("auth_username", testUser)
            .putBoolean("is_session_expired", true)
            .apply()

        assertEquals(CompanionSessionStatus.SESSION_EXPIRED, SyncApiClient.getSessionStatus(null))

        // Re-authenticate: simulate successful re-issuance of fresh token
        val freshToken = "fresh_token_after_reauth_999"
        SecureTokenStorage.saveToken(null, freshToken)
        testPrefs.edit()
            .putBoolean("is_session_expired", false)
            .putString("auth_username", testUser)
            .apply()

        assertFalse(SyncApiClient.isSessionExpired(null))
        assertTrue(SyncApiClient.hasCredentials(null))
        assertEquals(CompanionSessionStatus.CONNECTED, SyncApiClient.getSessionStatus(null))
        assertEquals(freshToken, SecureTokenStorage.getToken(null))
    }

    @Test
    fun testAccountSwitching() {
        // User A
        val userA = "student_alpha"
        val tokenA = "token_for_alpha"
        SecureTokenStorage.saveToken(null, tokenA)
        testPrefs.edit().putString("auth_username", userA).apply()

        assertEquals(userA, SyncApiClient.getSavedUsername(null))
        assertEquals(tokenA, SecureTokenStorage.getToken(null))

        // Switch to User B: Sign out old user
        SyncApiClient.signOut(null)

        assertEquals("", SyncApiClient.getSavedUsername(null))
        assertNull(SecureTokenStorage.getToken(null))
        assertEquals(CompanionSessionStatus.NOT_CONFIGURED, SyncApiClient.getSessionStatus(null))

        // Provision User B
        val userB = "student_beta"
        val tokenB = "token_for_beta"
        SecureTokenStorage.saveToken(null, tokenB)
        testPrefs.edit().putString("auth_username", userB).apply()

        assertEquals(userB, SyncApiClient.getSavedUsername(null))
        assertEquals(tokenB, SecureTokenStorage.getToken(null))
        assertEquals(CompanionSessionStatus.CONNECTED, SyncApiClient.getSessionStatus(null))
    }

    class InMemoryTestSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = (data[key] as? Set<String>) ?: defValues
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = Editor()

        inner class Editor : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val toRemove = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) { temp[key] = value; toRemove.remove(key) }
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) { temp[key] = value; toRemove.remove(key) }
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) { temp[key] = value; toRemove.remove(key) }
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) { temp[key] = value; toRemove.remove(key) }
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) { temp[key] = value; toRemove.remove(key) }
                return this
            }
            override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor {
                if (key != null) { temp[key] = values; toRemove.remove(key) }
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) { toRemove.add(key); temp.remove(key) }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true; temp.clear()
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) data.clear()
                toRemove.forEach { data.remove(it) }
                data.putAll(temp)
            }
        }
    }
}
