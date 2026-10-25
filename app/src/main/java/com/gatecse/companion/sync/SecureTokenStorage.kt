package com.gatecse.companion.sync

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

enum class KeySecurityLevel {
    STRONGBOX_BACKED,
    TEE_BACKED,
    HARDWARE_BACKED,
    SOFTWARE_BACKED,
    UNKNOWN
}

/**
 * Android-supported, Keystore-backed storage for sensitive companion session tokens.
 *
 * Security architecture:
 * 1. Passwords are NEVER persisted to disk after authentication.
 * 2. Only the revocable session token is persisted.
 * 3. The token is protected using standard AES-256 GCM authenticated encryption (API 26+).
 *    Where hardware support exists, keys reside inside TEE or StrongBox Keymaster.
 * 4. Practical compatibility: Gracefully functions on all supported Android devices without
 *    making hardware keystore an unconditional blocker for budget devices.
 * 5. Robust resilience: AEAD tag verification failures or key invalidation trigger clean
 *    session recovery rather than crashes.
 */
interface TokenCryptoProvider {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
    fun getSecurityLevel(): KeySecurityLevel
    fun invalidateKey()
}

class AndroidKeyStoreCryptoProvider(
    private val keyAlias: String = "gate_companion_sync_token_key"
) : TokenCryptoProvider {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private fun getOrCreateKey(): SecretKey {
        try {
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val spec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                return keyGenerator.generateKey()
            }
            val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        } catch (_: Exception) {
            // Key store entry corrupted or invalidated -> purge and regenerate
            try { keyStore.deleteEntry(keyAlias) } catch (_: Exception) {}
        }

        // Regenerate clean key on recovery
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun encrypt(plainText: String): String {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv // 12 bytes standard GCM IV
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        // Pack: IV length (1 byte) + IV + ciphertext
        val buffer = ByteBuffer.allocate(1 + iv.size + cipherBytes.size)
        buffer.put(iv.size.toByte())
        buffer.put(iv)
        buffer.put(cipherBytes)
        return Base64.getEncoder().encodeToString(buffer.array())
    }

    override fun decrypt(cipherText: String): String {
        val raw = Base64.getDecoder().decode(cipherText)
        val buffer = ByteBuffer.wrap(raw)
        val ivSize = buffer.get().toInt()
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val cipherBytes = ByteArray(buffer.remaining())
        buffer.get(cipherBytes)

        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    override fun getSecurityLevel(): KeySecurityLevel {
        return try {
            val secretKey = getOrCreateKey()
            val factory = SecretKeyFactory.getInstance(secretKey.algorithm, "AndroidKeyStore")
            val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.STRONGBOX_BACKED
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeySecurityLevel.TEE_BACKED
                    else -> KeySecurityLevel.SOFTWARE_BACKED
                }
            } else {
                @Suppress("DEPRECATION")
                if (keyInfo.isInsideSecureHardware) {
                    KeySecurityLevel.HARDWARE_BACKED
                } else {
                    KeySecurityLevel.SOFTWARE_BACKED
                }
            }
        } catch (_: Throwable) {
            KeySecurityLevel.UNKNOWN
        }
    }

    override fun invalidateKey() {
        try {
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        } catch (_: Exception) {}
    }
}

class JvmAesGcmCryptoProvider(
    private var secretKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
) : TokenCryptoProvider {

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        val buffer = ByteBuffer.allocate(1 + iv.size + cipherBytes.size)
        buffer.put(iv.size.toByte())
        buffer.put(iv)
        buffer.put(cipherBytes)
        return Base64.getEncoder().encodeToString(buffer.array())
    }

    override fun decrypt(cipherText: String): String {
        val raw = Base64.getDecoder().decode(cipherText)
        val buffer = ByteBuffer.wrap(raw)
        val ivSize = buffer.get().toInt()
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val cipherBytes = ByteArray(buffer.remaining())
        buffer.get(cipherBytes)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    override fun getSecurityLevel(): KeySecurityLevel {
        return KeySecurityLevel.SOFTWARE_BACKED
    }

    override fun invalidateKey() {
        // Regenerate a brand new random key to simulate key invalidation
        secretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}

object SecureTokenStorage {
    private const val PREFS_NAME = "gate_auth_prefs"
    private const val KEY_ENCRYPTED_TOKEN = "enc_session_token"

    @Volatile
    var customProviderForTesting: TokenCryptoProvider? = null

    @Volatile
    var customPrefsForTesting: android.content.SharedPreferences? = null

    fun getProvider(): TokenCryptoProvider {
        val custom = customProviderForTesting
        if (custom != null) return custom
        return try {
            AndroidKeyStoreCryptoProvider()
        } catch (_: Throwable) {
            // JVM testing or fallback environment
            JvmAesGcmCryptoProvider()
        }
    }

    private fun getPrefs(context: Context?): android.content.SharedPreferences? {
        val custom = customPrefsForTesting
        if (custom != null) return custom
        return context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(context: Context?, token: String) {
        val clean = token.trim()
        if (clean.isBlank()) return
        val encrypted = getProvider().encrypt(clean)
        getPrefs(context)?.edit()
            ?.putString(KEY_ENCRYPTED_TOKEN, encrypted)
            ?.apply()
    }

    fun getToken(context: Context?): String? {
        val encrypted = getPrefs(context)
            ?.getString(KEY_ENCRYPTED_TOKEN, null) ?: return null
        return try {
            getProvider().decrypt(encrypted)
        } catch (e: Exception) {
            // Authentication tag mismatch, corrupted payload, or key invalidation:
            // Auto-recover cleanly by clearing corrupted token from storage
            clearToken(context)
            null
        }
    }

    fun clearToken(context: Context?) {
        getPrefs(context)?.edit()
            ?.remove(KEY_ENCRYPTED_TOKEN)
            ?.apply()
    }

    fun hasToken(context: Context?): Boolean {
        return !getToken(context).isNullOrBlank()
    }

    fun getEffectiveSecurityLevel(): KeySecurityLevel {
        return getProvider().getSecurityLevel()
    }
}
